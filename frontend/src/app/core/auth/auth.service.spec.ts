import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { LanguageService } from '../i18n/language.service';
import { ModuleAccessService } from '../modules/module-access.service';
import { AuthService } from './auth.service';
import { CurrentUserService } from './current-user.service';

const PROFILE = {
  id: '0190a1b2-0000-7000-8000-000000000001',
  email: 'test.user@example.test',
  fullName: 'Test User',
  agency: { id: '0190a1b2-0000-7000-8000-000000000002', name: 'Agence Test' },
  roles: ['MANAGER'],
  permissions: ['quote.read'],
};

/** Bibliotheque OIDC simulee : seules les methodes utilisees par AuthService. */
function fakeOAuth() {
  return {
    configure: vi.fn(),
    loadDiscoveryDocument: vi.fn().mockResolvedValue({}),
    tryLogin: vi.fn().mockResolvedValue(true),
    hasValidAccessToken: vi.fn().mockReturnValue(false),
    getAccessToken: vi.fn().mockReturnValue('access-token'),
    setupAutomaticSilentRefresh: vi.fn(),
    initCodeFlow: vi.fn(),
    logOut: vi.fn(),
  };
}

describe('AuthService', () => {
  let oauth: ReturnType<typeof fakeOAuth>;
  let http: HttpTestingController;

  beforeEach(() => {
    oauth = fakeOAuth();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
        { provide: OAuthService, useValue: oauth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('is authenticated when discovery succeeds and a valid token exists', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const auth = TestBed.inject(AuthService);
    expect(auth.state()).toBe('initializing');

    await auth.init();

    expect(auth.state()).toBe('authenticated');
    expect(oauth.configure).toHaveBeenCalledWith(
      expect.objectContaining({
        issuer: 'http://localhost:8180/realms/agencysuite',
        clientId: 'agencysuite-frontend',
        responseType: 'code',
        scope: 'openid profile email organization',
      }),
    );
    expect(oauth.tryLogin).toHaveBeenCalled();
    expect(oauth.setupAutomaticSilentRefresh).toHaveBeenCalled();
    expect(auth.accessToken()).toBe('access-token');
    // Apres la connexion : profil et modules charges.
    http.expectOne('/api/v1/me').flush(PROFILE);
    http.expectOne('/api/v1/me/modules').flush({ modules: ['TRAVEL'] });
  });

  it('is unauthenticated without a valid token, and sends no API request', async () => {
    const auth = TestBed.inject(AuthService);

    await auth.init();

    expect(auth.state()).toBe('unauthenticated');
    expect(auth.accessToken()).toBeNull();
    expect(oauth.setupAutomaticSilentRefresh).not.toHaveBeenCalled();
    http.expectNone('/api/v1/me');
  });

  it('is unauthenticated when the login callback is rejected', async () => {
    oauth.tryLogin.mockRejectedValue(new Error('invalid_nonce_in_state'));
    const auth = TestBed.inject(AuthService);

    await expect(auth.init()).resolves.toBeUndefined();

    expect(auth.state()).toBe('unauthenticated');
  });

  it('is unavailable when discovery fails, and init() does not reject', async () => {
    oauth.loadDiscoveryDocument.mockRejectedValue(new Error('net::ERR_CONNECTION_REFUSED'));
    const auth = TestBed.inject(AuthService);

    await expect(auth.init()).resolves.toBeUndefined();

    expect(auth.state()).toBe('unavailable');
    expect(oauth.tryLogin).not.toHaveBeenCalled();
  });

  it('initializes only once for parallel init() calls', async () => {
    const auth = TestBed.inject(AuthService);

    await Promise.all([auth.init(), auth.init()]);

    expect(oauth.loadDiscoveryDocument).toHaveBeenCalledTimes(1);
  });

  it('retry() initializes again', async () => {
    oauth.loadDiscoveryDocument.mockRejectedValueOnce(new Error('down'));
    const auth = TestBed.inject(AuthService);
    await auth.init();
    expect(auth.state()).toBe('unavailable');

    await auth.retry();

    expect(oauth.loadDiscoveryDocument).toHaveBeenCalledTimes(2);
    expect(auth.state()).toBe('unauthenticated');
  });

  it.each(['ar', 'en', 'fr'])('login() passes ui_locales=%s from the current language', (code) => {
    TestBed.inject(LanguageService).use(code);
    const auth = TestBed.inject(AuthService);

    auth.login();

    expect(oauth.initCodeFlow).toHaveBeenCalledWith('', { ui_locales: code });
  });

  it('login() starts a single redirection', () => {
    const auth = TestBed.inject(AuthService);

    auth.login();
    auth.login();

    expect(oauth.initCodeFlow).toHaveBeenCalledTimes(1);
  });

  it('logout() clears the modules and the profile, then logs out from OIDC', async () => {
    oauth.hasValidAccessToken.mockReturnValue(true);
    const auth = TestBed.inject(AuthService);
    const user = TestBed.inject(CurrentUserService);
    const modules = TestBed.inject(ModuleAccessService);
    await auth.init();
    http.expectOne('/api/v1/me').flush(PROFILE);
    http.expectOne('/api/v1/me/modules').flush({ modules: ['TRAVEL'] });
    await Promise.all([user.load(), modules.load()]);
    expect(user.profile()?.fullName).toBe('Test User');
    expect(modules.has('TRAVEL')).toBe(true);

    auth.logout();

    expect(user.profile()).toBeNull();
    expect(user.state()).toBe('idle');
    expect(user.has('quote.read')).toBe(false);
    expect(modules.has('TRAVEL')).toBe(false);
    expect(modules.state()).toBe('idle');
    expect(oauth.logOut).toHaveBeenCalledTimes(1);
    expect(auth.state()).toBe('unauthenticated');
  });
});
