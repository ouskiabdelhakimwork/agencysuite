import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AGENCY_SCOPE_REQUIRED } from '../modules/module-access.service';
import {
  ACCESS_DENIED,
  AGENCY_UNAVAILABLE,
  CurrentUserService,
  CurrentUserState,
} from './current-user.service';

const URL = '/api/v1/me';
const FORBIDDEN = { status: 403, statusText: 'Forbidden' };
const PROFILE = {
  id: '0190a1b2-0000-7000-8000-000000000001',
  email: 'test.user@example.test',
  fullName: 'Test User',
  agency: { id: '0190a1b2-0000-7000-8000-000000000002', name: 'Agence Test' },
  roles: ['MANAGER'],
  permissions: ['customer.read', 'quote.write'],
};

describe('CurrentUserService', () => {
  let service: CurrentUserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CurrentUserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts idle with no profile and no permission', () => {
    expect(service.state()).toBe('idle');
    expect(service.profile()).toBeNull();
    expect(service.has('customer.read')).toBe(false);
  });

  it('is ready with the profile and its permissions on 200', async () => {
    const done = service.load();
    expect(service.state()).toBe('loading');
    http.expectOne(URL).flush(PROFILE);
    await done;

    expect(service.state()).toBe('ready');
    expect(service.profile()?.agency.name).toBe('Agence Test');
    expect(service.has('customer.read')).toBe(true);
    expect(service.has('quote.write')).toBe(true);
    expect(service.has('invoice.write')).toBe(false);
  });

  it.each<[string, CurrentUserState]>([
    [AGENCY_SCOPE_REQUIRED, 'no-agency'],
    [ACCESS_DENIED, 'not-activated'],
    [AGENCY_UNAVAILABLE, 'agency-unavailable'],
  ])('maps 403 %s to %s', async (type, state) => {
    const done = service.load();
    http.expectOne(URL).flush({ type, status: 403 }, FORBIDDEN);
    await done;

    expect(service.state()).toBe(state);
    expect(service.profile()).toBeNull();
    expect(service.has('customer.read')).toBe(false);
  });

  it.each([
    ['another 403 type', { type: 'urn:agencysuite:problem:module-not-enabled' }, FORBIDDEN],
    ['a 403 without body', null, FORBIDDEN],
    ['a 500', 'boom', { status: 500, statusText: 'Server Error' }],
    ['a 401 with a known type', { type: ACCESS_DENIED }, { status: 401, statusText: 'x' }],
  ])('is error on %s, and load() never rejects', async (_label, body, status) => {
    const done = service.load();
    http.expectOne(URL).flush(body, status);
    await expect(done).resolves.toBeUndefined();

    expect(service.state()).toBe('error');
    expect(service.profile()).toBeNull();
  });

  it('sends a single request for two parallel load() calls', async () => {
    const a = service.load();
    const b = service.load();
    http.expectOne(URL).flush(PROFILE);
    await Promise.all([a, b]);

    expect(service.state()).toBe('ready');
  });

  it('sends no request when load() is called after ready', async () => {
    const first = service.load();
    http.expectOne(URL).flush(PROFILE);
    await first;

    await service.load();
    http.expectNone(URL);
  });

  it('reload() sends a new request after ready and applies the new permissions', async () => {
    const first = service.load();
    http.expectOne(URL).flush(PROFILE);
    await first;

    const again = service.reload();
    http.expectOne(URL).flush({ ...PROFILE, roles: ['SALES'], permissions: ['identity.read'] });
    await again;

    expect(service.state()).toBe('ready');
    expect(service.profile()?.roles).toEqual(['SALES']);
    expect(service.has('customer.read')).toBe(false);
    expect(service.has('identity.read')).toBe(true);
  });

  it('reload() waits for a pending load, then sends its own request', async () => {
    const first = service.load();
    const again = service.reload();
    http.expectOne(URL).flush(PROFILE);
    await first;
    await new Promise((resolve) => setTimeout(resolve));

    http.expectOne(URL).flush({ ...PROFILE, permissions: [] });
    await again;

    expect(service.has('customer.read')).toBe(false);
  });

  it('reload() never rejects and closes everything on error', async () => {
    const first = service.load();
    http.expectOne(URL).flush(PROFILE);
    await first;

    const again = service.reload();
    http.expectOne(URL).flush('boom', { status: 500, statusText: 'Server Error' });
    await expect(again).resolves.toBeUndefined();

    expect(service.state()).toBe('error');
    expect(service.has('customer.read')).toBe(false);
  });

  it('clear() forgets the profile', async () => {
    const first = service.load();
    http.expectOne(URL).flush(PROFILE);
    await first;

    service.clear();

    expect(service.state()).toBe('idle');
    expect(service.profile()).toBeNull();
    expect(service.has('customer.read')).toBe(false);
  });
});
