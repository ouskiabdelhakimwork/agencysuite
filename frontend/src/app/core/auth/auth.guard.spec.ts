import { EnvironmentInjector, WritableSignal, runInInjectionContext, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PartialMatchRouteSnapshot, Route, Router, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService, AuthState } from './auth.service';

describe('authGuard', () => {
  let state: WritableSignal<AuthState>;
  let auth: {
    init: ReturnType<typeof vi.fn<() => Promise<void>>>;
    state: () => AuthState;
    login: ReturnType<typeof vi.fn<() => void>>;
  };

  function setUp(final: AuthState, init?: () => Promise<void>): void {
    state = signal<AuthState>('initializing');
    auth = {
      init: vi.fn<() => Promise<void>>(
        init ??
          (() => {
            state.set(final);
            return Promise.resolve();
          }),
      ),
      state: () => state(),
      login: vi.fn<() => void>(),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  }

  function runGuard(): Promise<boolean | UrlTree> {
    return runInInjectionContext(TestBed.inject(EnvironmentInjector), () =>
      authGuard({} as Route, [], {} as PartialMatchRouteSnapshot),
    ) as Promise<boolean | UrlTree>;
  }

  it('allows the route when authenticated', async () => {
    setUp('authenticated');

    expect(await runGuard()).toBe(true);
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('starts the login and refuses when unauthenticated', async () => {
    setUp('unauthenticated');

    expect(await runGuard()).toBe(false);
    expect(auth.login).toHaveBeenCalledTimes(1);
  });

  it('redirects to the unavailable page when the identity provider is unreachable', async () => {
    setUp('unavailable');

    const result = await runGuard();

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/connexion-indisponible');
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('waits for init() before deciding', async () => {
    let finish!: () => void;
    setUp('authenticated', () => new Promise<void>((resolve) => (finish = resolve)));
    let settled = false;
    const result = runGuard().then((r) => {
      settled = true;
      return r;
    });
    await new Promise((resolve) => setTimeout(resolve));
    expect(settled).toBe(false);

    state.set('authenticated');
    finish();

    expect(await result).toBe(true);
  });
});
