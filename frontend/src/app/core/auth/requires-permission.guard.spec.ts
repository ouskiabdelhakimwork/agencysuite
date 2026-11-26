import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PartialMatchRouteSnapshot, Route, Router, UrlTree, provideRouter } from '@angular/router';
import { requiresPermission } from './requires-permission.guard';

const URL = '/api/v1/me';

function profile(permissions: string[]) {
  return {
    id: '0190a1b2-0000-7000-8000-000000000001',
    email: 'test.user@example.test',
    fullName: 'Test User',
    agency: { id: '0190a1b2-0000-7000-8000-000000000002', name: 'Agence Test' },
    roles: ['SALES'],
    permissions,
  };
}

describe('requiresPermission', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function runGuard(): Promise<boolean | UrlTree> {
    const guard = requiresPermission('user.manage');
    return runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
      guard({} as Route, [], {} as PartialMatchRouteSnapshot),
    ) as Promise<boolean | UrlTree>;
  }

  function serialize(result: boolean | UrlTree): string | boolean {
    return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;
  }

  it('allows the route when the permission is present', async () => {
    const result = runGuard();
    http.expectOne(URL).flush(profile(['audit.read', 'user.manage']));

    expect(await result).toBe(true);
  });

  it('redirects to the home page when the permission is absent', async () => {
    const result = runGuard();
    http.expectOne(URL).flush(profile(['audit.read']));

    expect(serialize(await result)).toBe('/');
  });

  it('redirects to the home page when the profile cannot be loaded', async () => {
    const result = runGuard();
    http.expectOne(URL).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(serialize(await result)).toBe('/');
  });

  it('waits for the profile to be loaded before deciding', async () => {
    let settled = false;
    const result = runGuard().then((r) => {
      settled = true;
      return r;
    });
    await new Promise((resolve) => setTimeout(resolve));
    expect(settled).toBe(false);

    http.expectOne(URL).flush(profile(['user.manage']));
    expect(await result).toBe(true);
  });
});
