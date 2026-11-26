import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AUDIT_ACTIONS, AuditApi } from './audit-api';

describe('AuditApi', () => {
  let api: AuditApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuditApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads a page with GET /api/v1/audit-events?page=&size=', async () => {
    const page = { items: [], page: 2, size: 50, totalElements: 120, totalPages: 3 };
    const done = firstValueFrom(api.list(2));
    const req = http.expectOne((r) => r.url === '/api/v1/audit-events');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page);
    expect(await done).toEqual(page);
  });

  it('accepts an explicit page size', () => {
    void firstValueFrom(api.list(0, 10));
    http.expectOne('/api/v1/audit-events?page=0&size=10').flush({});
  });

  it('keeps the backend AuditAction order', () => {
    expect(AUDIT_ACTIONS).toEqual([
      'AGENCY_CREATED',
      'MODULE_ENABLED',
      'MODULE_DISABLED',
      'USER_LOGGED_IN',
      'ORGANIZATION_PROVISIONED',
      'USER_INVITED',
      'USER_ACTIVATED',
      'INVITATION_RESENT',
      'ROLE_ASSIGNED',
      'ROLE_REVOKED',
      'USER_DISABLED',
      'USER_ENABLED',
    ]);
  });
});
