import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PartialMatchRouteSnapshot, Route, Router, UrlTree, provideRouter } from '@angular/router';
import { requiresModule } from './requires-module.guard';

const URL = '/api/v1/me/modules';

describe('requiresModule', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function runGuard(): Promise<boolean | UrlTree> {
    const guard = requiresModule('TRAVEL');
    return runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
      guard({} as Route, [], {} as PartialMatchRouteSnapshot),
    ) as Promise<boolean | UrlTree>;
  }

  function serialize(result: boolean | UrlTree): string | boolean {
    return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;
  }

  it('allows the route when the module is enabled', async () => {
    const result = runGuard();
    http.expectOne(URL).flush({ modules: ['TRAVEL'] });

    expect(await result).toBe(true);
  });

  it('redirects to the not-enabled page when the module is not enabled', async () => {
    const result = runGuard();
    http.expectOne(URL).flush({ modules: ['VISA'] });

    expect(serialize(await result)).toBe('/module-non-active?module=TRAVEL');
  });

  it('waits for the modules to be loaded before deciding', async () => {
    let settled = false;
    const result = runGuard().then((r) => {
      settled = true;
      return r;
    });
    await new Promise((resolve) => setTimeout(resolve));
    expect(settled).toBe(false);

    http.expectOne(URL).flush({ modules: ['TRAVEL'] });
    expect(await result).toBe(true);
    expect(settled).toBe(true);
  });

  it('refuses when the modules could not be loaded', async () => {
    const result = runGuard();
    http.expectOne(URL).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(serialize(await result)).toBe('/module-non-active?module=TRAVEL');
  });
});
