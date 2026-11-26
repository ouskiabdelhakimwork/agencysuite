import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { routes } from './app.routes';
import { AuthService, AuthState } from './core/auth/auth.service';

/** Table des routes reelle : la garde de connexion passe avant celles des modules. */
describe('routes', () => {
  let state: WritableSignal<AuthState>;
  let login: ReturnType<typeof vi.fn<() => void>>;
  let http: HttpTestingController;

  beforeEach(() => {
    state = signal<AuthState>('authenticated');
    login = vi.fn<() => void>();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
        {
          provide: AuthService,
          useValue: {
            init: () => Promise.resolve(),
            state,
            login,
            retry: vi.fn(),
            logout: vi.fn(),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function navigate(url: string): Promise<boolean> {
    const harness = await RouterTestingHarness.create();
    return TestBed.inject(Router)
      .navigateByUrl(url)
      .then(
        (ok) => ok,
        () => false,
      )
      .finally(() => harness.fixture.destroy());
  }

  it('starts the login once and calls no API on a module route without session', async () => {
    state.set('unauthenticated');

    await navigate('/travel');

    expect(login).toHaveBeenCalled();
    http.expectNone('/api/v1/me/modules');
  });

  it('does not loop through the wildcard route without session', async () => {
    state.set('unauthenticated');

    await navigate('/unknown/page');

    expect(login.mock.calls.length).toBeLessThan(5);
  });

  it('redirects every application route to the unavailable page when the provider is down', async () => {
    state.set('unavailable');

    await navigate('/mice');

    expect(TestBed.inject(Router).url).toBe('/connexion-indisponible');
  });

  it.each(['/connexion-indisponible', '/acces-refuse'])(
    'opens the public page %s without session',
    async (url) => {
      state.set('unauthenticated');

      await navigate(url);

      expect(TestBed.inject(Router).url).toBe(url);
      expect(login).not.toHaveBeenCalled();
    },
  );

  it('sends /administration/utilisateurs typed by hand back home without user.manage', async () => {
    const done = navigate('/administration/utilisateurs');
    await new Promise((resolve) => setTimeout(resolve));
    http.expectOne('/api/v1/me').flush({
      id: 'u',
      email: 'sales@example.test',
      fullName: 'Sales User',
      agency: { id: 'a', name: 'Agence Test' },
      roles: ['SALES'],
      permissions: ['identity.read'],
    });
    await done;

    expect(TestBed.inject(Router).url).toBe('/');
    http.expectNone('/api/v1/users');
  });

  it('opens an enabled module once signed in', async () => {
    const done = navigate('/travel');
    await new Promise((resolve) => setTimeout(resolve));
    http.expectOne('/api/v1/me/modules').flush({ modules: ['TRAVEL'] });
    await done;

    expect(TestBed.inject(Router).url).toBe('/travel');
  });
});
