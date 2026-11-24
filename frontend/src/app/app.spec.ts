import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import fr from '../../public/i18n/fr.json';
import { App } from './app';
import { AGENCY_SCOPE_REQUIRED } from './core/modules/module-access.service';

const URL = '/api/v1/me/modules';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
      ],
    }).compileComponents();
    TestBed.inject(TranslateService).setTranslation('fr', fr);
    TestBed.inject(TranslateService).use('fr');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(flush: (fixture: ComponentFixture<App>) => void): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(App);
    flush(fixture);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function menuCodes(el: HTMLElement): (string | null)[] {
    return Array.from(el.querySelectorAll('nav.shell-nav a.nav-link')).map((a) =>
      a.getAttribute('data-module'),
    );
  }

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
    http.expectOne(URL).flush({ modules: [] });
  });

  it('should offer the three V1 languages', async () => {
    const el = await render(() => http.expectOne(URL).flush({ modules: [] }));
    const buttons = el.querySelectorAll('app-language-switcher button');
    expect(Array.from(buttons).map((b) => b.getAttribute('lang'))).toEqual(['fr', 'ar', 'en']);
  });

  it('shows only the enabled modules, in catalogue order', async () => {
    const el = await render(() =>
      http.expectOne(URL).flush({ modules: ['MICE', 'TRAVEL', 'UNKNOWN', 'FLEET'] }),
    );

    expect(menuCodes(el)).toEqual(['TRAVEL', 'MICE', 'FLEET']);
    const links = Array.from(el.querySelectorAll('nav.shell-nav a.nav-link'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/travel', '/mice', '/fleet']);
    expect(links[0].textContent?.trim()).toBe('Voyages');
    expect(el.querySelector('nav.shell-nav')?.getAttribute('aria-label')).toBe(fr.nav.label);
    expect(el.querySelector('.banner')).toBeNull();
  });

  it('shows the no-agency banner and no module', async () => {
    const el = await render(() =>
      http
        .expectOne(URL)
        .flush({ type: AGENCY_SCOPE_REQUIRED }, { status: 403, statusText: 'Forbidden' }),
    );

    expect(menuCodes(el)).toEqual([]);
    expect(el.querySelector('.banner-info')?.textContent?.trim()).toBe(fr.modules.noAgency);
    expect(el.querySelector('.banner-error')).toBeNull();
  });

  it('shows the error banner with a retry button that sends the request again', async () => {
    const fixture = TestBed.createComponent(App);
    http.expectOne(URL).flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(menuCodes(el)).toEqual([]);
    expect(el.querySelector('.banner-error span')?.textContent?.trim()).toBe(fr.modules.loadError);
    const retry = el.querySelector<HTMLButtonElement>('.banner-error button.retry');
    expect(retry?.textContent?.trim()).toBe(fr.modules.retry);

    retry!.click();
    http.expectOne(URL).flush({ modules: ['VISA'] });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(menuCodes(el)).toEqual(['VISA']);
    expect(el.querySelector('.banner')).toBeNull();
  });
});
