import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import ar from '../../public/i18n/ar.json';
import fr from '../../public/i18n/fr.json';
import { App } from './app';
import { AuthService, AuthState } from './core/auth/auth.service';
import { ACCESS_DENIED, CurrentUserService } from './core/auth/current-user.service';
import { AGENCY_SCOPE_REQUIRED, ModuleAccessService } from './core/modules/module-access.service';
import { ShellNotice } from './core/ui/shell-notice.service';

const MODULES_URL = '/api/v1/me/modules';
const ME_URL = '/api/v1/me';
const FORBIDDEN = { status: 403, statusText: 'Forbidden' };
const PROFILE = {
  id: '0190a1b2-0000-7000-8000-000000000001',
  email: 'test.user@example.test',
  fullName: 'Test User',
  agency: { id: '0190a1b2-0000-7000-8000-000000000002', name: 'Agence Test' },
  roles: ['MANAGER'],
  permissions: [],
};

@Component({ template: '' })
class Stub {}

describe('App', () => {
  let http: HttpTestingController;
  let authState: WritableSignal<AuthState>;
  let logout: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(async () => {
    authState = signal<AuthState>('authenticated');
    logout = vi.fn<() => void>();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'acces-refuse', component: Stub }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
        { provide: AuthService, useValue: { state: authState, logout } },
      ],
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('ar', ar);
    translate.use('fr');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function settle(fixture: ComponentFixture<App>): Promise<HTMLElement> {
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  async function loadModules(fixture: ComponentFixture<App>, flush: () => void) {
    const done = TestBed.inject(ModuleAccessService).load();
    flush();
    await done;
    return settle(fixture);
  }

  async function loadProfile(fixture: ComponentFixture<App>, flush: () => void) {
    const done = TestBed.inject(CurrentUserService).load();
    flush();
    await done;
    return settle(fixture);
  }

  function menuCodes(el: HTMLElement): (string | null)[] {
    return Array.from(el.querySelectorAll('nav.shell-nav a.nav-link[data-module]')).map((a) =>
      a.getAttribute('data-module'),
    );
  }

  it('should create the app without calling the API by itself', async () => {
    const fixture = TestBed.createComponent(App);
    await settle(fixture);

    expect(fixture.componentInstance).toBeTruthy();
    http.expectNone(MODULES_URL);
    http.expectNone(ME_URL);
  });

  it('should offer the three V1 languages', async () => {
    const el = await settle(TestBed.createComponent(App));
    const buttons = el.querySelectorAll('app-language-switcher button');
    expect(Array.from(buttons).map((b) => b.getAttribute('lang'))).toEqual(['fr', 'ar', 'en']);
  });

  it('shows the user and agency names isolated in <bdi>', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadProfile(fixture, () => http.expectOne(ME_URL).flush(PROFILE));

    expect(el.querySelector('.account bdi.user-name')?.textContent?.trim()).toBe('Test User');
    expect(el.querySelector('.account bdi.agency-name')?.textContent?.trim()).toBe('Agence Test');
  });

  it('keeps the names isolated in the Arabic interface', async () => {
    TestBed.inject(TranslateService).use('ar');
    const fixture = TestBed.createComponent(App);
    const el = await loadProfile(fixture, () => http.expectOne(ME_URL).flush(PROFILE));

    expect(el.querySelector('bdi.agency-name')?.textContent?.trim()).toBe('Agence Test');
    expect(el.querySelector('button.logout')?.textContent?.trim()).toBe(ar.auth.logout);
  });

  it('shows a logout button that logs out', async () => {
    const el = await settle(TestBed.createComponent(App));
    const button = el.querySelector<HTMLButtonElement>('button.logout');
    expect(button?.textContent?.trim()).toBe(fr.auth.logout);

    button!.click();

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('shows no logout button nor names when not authenticated', async () => {
    authState.set('unauthenticated');
    const el = await settle(TestBed.createComponent(App));

    expect(el.querySelector('button.logout')).toBeNull();
    expect(el.querySelector('.account')).toBeNull();
  });

  it('shows a translated loading indicator while initializing', async () => {
    authState.set('initializing');
    const el = await settle(TestBed.createComponent(App));

    const spinner = el.querySelector('.spinner');
    expect(spinner?.getAttribute('role')).toBe('status');
    expect(spinner?.getAttribute('aria-label')).toBe(fr.auth.loading);
    expect(spinner?.textContent?.trim()).toBe('');
  });

  it('shows only the enabled modules, in catalogue order', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadModules(fixture, () =>
      http.expectOne(MODULES_URL).flush({ modules: ['MICE', 'TRAVEL', 'UNKNOWN', 'FLEET'] }),
    );

    expect(menuCodes(el)).toEqual(['TRAVEL', 'MICE', 'FLEET']);
    const links = Array.from(el.querySelectorAll('nav.shell-nav a.nav-link'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/travel', '/mice', '/fleet']);
    expect(links[0].textContent?.trim()).toBe('Voyages');
    expect(el.querySelector('nav.shell-nav')?.getAttribute('aria-label')).toBe(fr.nav.label);
    expect(el.querySelector('.banner')).toBeNull();
  });

  it('no longer shows the step-3 no-agency banner', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadModules(fixture, () =>
      http.expectOne(MODULES_URL).flush({ type: AGENCY_SCOPE_REQUIRED }, FORBIDDEN),
    );

    expect(menuCodes(el)).toEqual([]);
    expect(el.querySelector('.banner')).toBeNull();
    expect((fr.modules as Record<string, string>)['noAgency']).toBeUndefined();
  });

  it('redirects to the access-denied page when the account is refused', async () => {
    const fixture = TestBed.createComponent(App);
    await loadProfile(fixture, () =>
      http.expectOne(ME_URL).flush({ type: ACCESS_DENIED }, FORBIDDEN),
    );
    await settle(fixture);

    expect(TestBed.inject(Router).url).toBe('/acces-refuse');
  });

  it('hides the modules error banner when the account is refused', async () => {
    const fixture = TestBed.createComponent(App);
    await loadProfile(fixture, () =>
      http.expectOne(ME_URL).flush({ type: ACCESS_DENIED }, FORBIDDEN),
    );
    const el = await loadModules(fixture, () =>
      http.expectOne(MODULES_URL).flush({ type: ACCESS_DENIED }, FORBIDDEN),
    );

    expect(el.querySelector('.banner-error')).toBeNull();
  });

  it('shows the translated shell notice until it is closed', async () => {
    const fixture = TestBed.createComponent(App);
    TestBed.inject(ShellNotice).show('admin.users.ownRolesChanged');
    const el = await settle(fixture);

    const banner = el.querySelector('.banner-info');
    expect(banner?.getAttribute('role')).toBe('status');
    expect(banner?.querySelector('span')?.textContent?.trim()).toBe('Vos rôles ont changé.');

    banner!.querySelector<HTMLButtonElement>('button.dismiss')!.click();
    await settle(fixture);
    expect(el.querySelector('.banner-info')).toBeNull();
  });

  function adminLinks(
    el: HTMLElement,
  ): { id: string | null; href: string | null; text?: string }[] {
    return Array.from(el.querySelectorAll('nav.shell-nav .admin-section a.admin-link')).map(
      (a) => ({
        id: a.getAttribute('data-admin'),
        href: a.getAttribute('href'),
        text: a.textContent?.trim(),
      }),
    );
  }

  it('hides the Administration section without any admin permission', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadProfile(fixture, () =>
      http.expectOne(ME_URL).flush({ ...PROFILE, permissions: ['customer.read'] }),
    );

    expect(el.querySelector('.admin-section')).toBeNull();
  });

  it('shows a single Administration link with audit.read only', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadProfile(fixture, () =>
      http.expectOne(ME_URL).flush({ ...PROFILE, permissions: ['audit.read'] }),
    );

    expect(el.querySelector('.admin-section .nav-section-label')?.textContent?.trim()).toBe(
      fr.nav.admin,
    );
    expect(adminLinks(el)).toEqual([
      { id: 'audit', href: '/administration/journal', text: fr.nav.adminAudit },
    ]);
  });

  it('shows both Administration links with both permissions', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadProfile(fixture, () =>
      http.expectOne(ME_URL).flush({ ...PROFILE, permissions: ['audit.read', 'user.manage'] }),
    );

    expect(adminLinks(el)).toEqual([
      { id: 'users', href: '/administration/utilisateurs', text: fr.nav.adminUsers },
      { id: 'audit', href: '/administration/journal', text: fr.nav.adminAudit },
    ]);
    expect(menuCodes(el)).toEqual([]);
  });

  it('shows the error banner with a retry button that sends the request again', async () => {
    const fixture = TestBed.createComponent(App);
    const el = await loadModules(fixture, () =>
      http.expectOne(MODULES_URL).flush('boom', { status: 500, statusText: 'Server Error' }),
    );

    expect(menuCodes(el)).toEqual([]);
    expect(el.querySelector('.banner-error span')?.textContent?.trim()).toBe(fr.modules.loadError);
    const retry = el.querySelector<HTMLButtonElement>('.banner-error button.retry');
    expect(retry?.textContent?.trim()).toBe(fr.modules.retry);

    retry!.click();
    http.expectOne(MODULES_URL).flush({ modules: ['VISA'] });
    await settle(fixture);
    await settle(fixture);

    expect(menuCodes(el)).toEqual(['VISA']);
    expect(el.querySelector('.banner')).toBeNull();
  });
});
