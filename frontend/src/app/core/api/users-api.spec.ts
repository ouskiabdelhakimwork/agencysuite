import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SYSTEM_ROLES, UsersApi, isSystemRole } from './users-api';

const ID = '0190a1b2-0000-7000-8000-00000000000a';

describe('UsersApi', () => {
  let api: UsersApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(UsersApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the users with GET /api/v1/users', async () => {
    const done = firstValueFrom(api.list());
    const req = http.expectOne('/api/v1/users');
    expect(req.request.method).toBe('GET');
    req.flush([]);
    expect(await done).toEqual([]);
  });

  it('invites with POST /api/v1/users and the exact body', async () => {
    const body = {
      email: 'new.user@example.test',
      firstName: 'New',
      lastName: 'User',
      roles: ['SALES'] as const,
    };
    const done = firstValueFrom(api.invite(body));
    const req = http.expectOne('/api/v1/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(
      { id: ID, status: 'INVITED', invitationSent: true },
      { status: 201, statusText: 'Created' },
    );
    expect((await done).invitationSent).toBe(true);
  });

  it('replaces the roles with PUT /api/v1/users/{id}/roles', () => {
    void firstValueFrom(api.changeRoles(ID, ['SALES', 'OPERATIONS']));
    const req = http.expectOne(`/api/v1/users/${ID}/roles`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roles: ['SALES', 'OPERATIONS'] });
    req.flush({});
  });

  it('resends the invitation with POST /api/v1/users/{id}/invitation', () => {
    void firstValueFrom(api.resendInvitation(ID));
    const req = http.expectOne(`/api/v1/users/${ID}/invitation`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 202, statusText: 'Accepted' });
  });

  it.each(['disable', 'enable'] as const)('calls POST /api/v1/users/{id}/%s', async (action) => {
    const done = firstValueFrom(api[action](ID));
    const req = http.expectOne(`/api/v1/users/${ID}/${action}`);
    expect(req.request.method).toBe('POST');
    req.flush({ identityProviderSynced: false });
    expect(await done).toEqual({ identityProviderSynced: false });
  });

  it('knows the four backend system roles', () => {
    expect(SYSTEM_ROLES).toEqual(['MANAGER', 'SALES', 'OPERATIONS', 'ACCOUNTANT']);
    expect(isSystemRole('SALES')).toBe(true);
    expect(isSystemRole('ADMIN')).toBe(false);
  });
});
