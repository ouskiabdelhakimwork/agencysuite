import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import fr from '../../public/i18n/fr.json';
import { App } from './app';
import { appConfig } from './app.config';

const ISSUER = 'http://localhost:8180/realms/agencysuite';
const OIDC = `${ISSUER}/protocol/openid-connect`;
const DISCOVERY = {
  issuer: ISSUER,
  authorization_endpoint: `${OIDC}/auth`,
  token_endpoint: `${OIDC}/token`,
  userinfo_endpoint: `${OIDC}/userinfo`,
  end_session_endpoint: `${OIDC}/logout`,
  jwks_uri: `${OIDC}/certs`,
};
const TOKEN = 'fake.access.token';

/**
 * Monte la vraie configuration de l'application (chargeur HTTP des traductions,
 * intercepteurs Accept-Language et jeton, bibliotheque OIDC, initialiseurs de langue et de
 * connexion) : protege contre la dependance circulaire NG0200 qui empechait le chargement de
 * fr.json, et verifie que le jeton part vers /api/ et nulle part ailleurs.
 */
describe('appConfig (real translation loader, interceptors and OIDC library)', () => {
  const logged: string[] = [];

  beforeEach(() => {
    logged.length = 0;
    for (const level of ['error', 'warn', 'log', 'info', 'debug'] as const) {
      vi.spyOn(console, level).mockImplementation((...args: unknown[]) => {
        logged.push(args.map(String).join(' '));
      });
    }
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem('agencysuite.lang', 'fr');
    document.documentElement.lang = 'fr';

    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  const tick = () => new Promise((resolve) => setTimeout(resolve));

  /** Simule un utilisateur deja connecte : jeton valide dans sessionStorage (stockage par defaut). */
  function signedIn(): void {
    sessionStorage.setItem('access_token', TOKEN);
    sessionStorage.setItem('access_token_stored_at', String(Date.now()));
    sessionStorage.setItem('expires_at', String(Date.now() + 300_000));
  }

  async function answerIdentityProvider(http: HttpTestingController): Promise<void> {
    await tick();
    const discovery = http.expectOne(`${ISSUER}/.well-known/openid-configuration`);
    expect(discovery.request.headers.has('Authorization')).toBe(false);
    discovery.flush(DISCOVERY);
    await tick();
    const jwks = http.expectOne(DISCOVERY.jwks_uri);
    expect(jwks.request.headers.has('Authorization')).toBe(false);
    jwks.flush({ keys: [] });
    await tick();
  }

  function answerTranslations(http: HttpTestingController): void {
    const translations = http.match('/i18n/fr.json');
    expect(translations.length).toBeGreaterThan(0);
    for (const request of translations) {
      expect(request.request.headers.has('Accept-Language')).toBe(false);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush(fr);
    }
  }

  function noCircularDependency(): void {
    expect(logged.filter((line) => line.includes('NG0200'))).toEqual([]);
    expect(logged.filter((line) => line.includes('error loading translation'))).toEqual([]);
  }

  it('loads fr.json, sends the token to /api/ only, and shows the user without NG0200', async () => {
    signedIn();
    const http = TestBed.inject(HttpTestingController);
    answerTranslations(http);
    await answerIdentityProvider(http);

    const fixture = TestBed.createComponent(App);

    const me = http.expectOne('/api/v1/me');
    expect(me.request.headers.get('Authorization')).toBe(`Bearer ${TOKEN}`);
    expect(me.request.headers.get('Accept-Language')).toBe('fr');
    me.flush({
      id: '0190a1b2-0000-7000-8000-000000000001',
      email: 'test.user@example.test',
      fullName: 'Test User',
      agency: { id: '0190a1b2-0000-7000-8000-000000000002', name: 'Agence Test' },
      roles: ['MANAGER'],
      permissions: [],
    });
    const modules = http.expectOne('/api/v1/me/modules');
    expect(modules.request.headers.get('Authorization')).toBe(`Bearer ${TOKEN}`);
    modules.flush({ modules: ['TRAVEL'] });

    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.brand-tagline')?.textContent?.trim()).toBe(fr.app.tagline);
    expect(el.querySelector('a.nav-link')?.textContent?.trim()).toBe(fr.module.TRAVEL.name);
    expect(el.querySelector('bdi.user-name')?.textContent?.trim()).toBe('Test User');
    expect(el.querySelector('button.logout')?.textContent?.trim()).toBe(fr.auth.logout);
    expect(localStorage.getItem('access_token')).toBeNull();
    noCircularDependency();
    http.verify();
  });

  it('still shows the translated shell when the identity provider is unreachable', async () => {
    const http = TestBed.inject(HttpTestingController);
    answerTranslations(http);
    await tick();
    http
      .expectOne(`${ISSUER}/.well-known/openid-configuration`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await tick();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.brand-tagline')?.textContent?.trim()).toBe(fr.app.tagline);
    expect(el.querySelector('button.logout')).toBeNull();
    http.expectNone('/api/v1/me');
    noCircularDependency();
    http.verify();
  });
});
