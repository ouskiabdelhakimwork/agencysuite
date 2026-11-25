import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AGENCY_SCOPE_REQUIRED, ModuleAccessService } from './module-access.service';

const URL = '/api/v1/me/modules';
const SERVER_ERROR = { status: 500, statusText: 'Server Error' };
const FORBIDDEN = { status: 403, statusText: 'Forbidden' };

describe('ModuleAccessService', () => {
  let service: ModuleAccessService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ModuleAccessService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts idle with no module', () => {
    expect(service.state()).toBe('idle');
    expect(service.enabled().size).toBe(0);
  });

  it('is ready with the enabled modules on 200', async () => {
    const done = service.load();
    expect(service.state()).toBe('loading');
    http.expectOne(URL).flush({ modules: ['TRAVEL', 'MICE'] });
    await done;

    expect(service.state()).toBe('ready');
    expect([...service.enabled()]).toEqual(['TRAVEL', 'MICE']);
    expect(service.has('TRAVEL')).toBe(true);
    expect(service.has('VISA')).toBe(false);
  });

  it('ignores unknown module codes without error', async () => {
    const done = service.load();
    http.expectOne(URL).flush({ modules: ['TRAVEL', 'SPACE_TOURISM', 'travel'] });
    await done;

    expect(service.state()).toBe('ready');
    expect([...service.enabled()]).toEqual(['TRAVEL']);
  });

  it('is no-agency on 403 agency-scope-required', async () => {
    const done = service.load();
    http
      .expectOne(URL)
      .flush({ type: AGENCY_SCOPE_REQUIRED, status: 403, title: 'Agence requise' }, FORBIDDEN);
    await done;

    expect(service.state()).toBe('no-agency');
    expect(service.enabled().size).toBe(0);
  });

  it('is error on another 403', async () => {
    const done = service.load();
    http
      .expectOne(URL)
      .flush({ type: 'urn:agencysuite:problem:access-denied', status: 403 }, FORBIDDEN);
    await done;

    expect(service.state()).toBe('error');
    expect(service.enabled().size).toBe(0);
  });

  it('is error with no module on 500, and load() never rejects', async () => {
    const done = service.load();
    http.expectOne(URL).flush('boom', SERVER_ERROR);
    await expect(done).resolves.toBeUndefined();

    expect(service.state()).toBe('error');
    expect(service.enabled().size).toBe(0);
  });

  it('is error on a network failure', async () => {
    const done = service.load();
    http.expectOne(URL).error(new ProgressEvent('error'));
    await done;

    expect(service.state()).toBe('error');
  });

  it('clears previously enabled modules when a refresh fails (closed by default)', async () => {
    const first = service.load();
    http.expectOne(URL).flush({ modules: ['TRAVEL'] });
    await first;

    const second = service.refresh();
    http.expectOne(URL).flush('boom', SERVER_ERROR);
    await second;

    expect(service.state()).toBe('error');
    expect(service.has('TRAVEL')).toBe(false);
  });

  it('sends a single request for two parallel load() calls', async () => {
    const a = service.load();
    const b = service.load();
    http.expectOne(URL).flush({ modules: ['VISA'] });
    await Promise.all([a, b]);

    expect(service.has('VISA')).toBe(true);
  });

  it('sends no request when load() is called after ready', async () => {
    const first = service.load();
    http.expectOne(URL).flush({ modules: [] });
    await first;

    await service.load();
    http.expectNone(URL);
  });

  it('sends no request when load() is called after no-agency', async () => {
    const first = service.load();
    http.expectOne(URL).flush({ type: AGENCY_SCOPE_REQUIRED }, FORBIDDEN);
    await first;

    await service.load();
    http.expectNone(URL);
  });

  it('retries on load() after an error', async () => {
    const first = service.load();
    http.expectOne(URL).flush('boom', SERVER_ERROR);
    await first;

    const second = service.load();
    http.expectOne(URL).flush({ modules: ['FLEET'] });
    await second;

    expect(service.state()).toBe('ready');
  });

  it('sends a new request on refresh()', async () => {
    const first = service.load();
    http.expectOne(URL).flush({ modules: ['TRAVEL'] });
    await first;

    const second = service.refresh();
    http.expectOne(URL).flush({ modules: ['GIFTBOX'] });
    await second;

    expect([...service.enabled()]).toEqual(['GIFTBOX']);
  });

  it('writes nothing to localStorage', async () => {
    localStorage.clear();
    const done = service.load();
    http.expectOne(URL).flush({ modules: ['TRAVEL'] });
    await done;

    expect(localStorage.length).toBe(0);
  });

  it('clear() forgets the modules (logout)', async () => {
    const first = service.load();
    http.expectOne(URL).flush({ modules: ['TRAVEL'] });
    await first;

    service.clear();

    expect(service.state()).toBe('idle');
    expect(service.has('TRAVEL')).toBe(false);
  });
});
