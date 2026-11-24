import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import fr from '../../public/i18n/fr.json';
import { App } from './app';
import { appConfig } from './app.config';

/**
 * Monte la vraie configuration de l'application (chargeur HTTP des traductions,
 * intercepteur Accept-Language, initialiseur de langue) : protege contre la
 * dependance circulaire NG0200 qui empechait le chargement de fr.json.
 */
describe('appConfig (real translation loader and interceptor)', () => {
  const logged: string[] = [];

  beforeEach(() => {
    logged.length = 0;
    for (const level of ['error', 'warn', 'log', 'info'] as const) {
      vi.spyOn(console, level).mockImplementation((...args: unknown[]) => {
        logged.push(args.map(String).join(' '));
      });
    }
    localStorage.clear();
    localStorage.setItem('agencysuite.lang', 'fr');
    document.documentElement.lang = 'fr';

    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('loads fr.json and shows the French texts without NG0200', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);

    const translations = http.match('/i18n/fr.json');
    expect(translations.length).toBeGreaterThan(0);
    for (const request of translations) {
      expect(request.request.headers.has('Accept-Language')).toBe(false);
      request.flush(fr);
    }

    const modules = http.expectOne('/api/v1/me/modules');
    expect(modules.request.headers.get('Accept-Language')).toBe('fr');
    modules.flush({ modules: ['TRAVEL'] });

    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.brand-tagline')?.textContent?.trim()).toBe(fr.app.tagline);
    expect(el.querySelector('a.nav-link')?.textContent?.trim()).toBe(fr.module.TRAVEL.name);
    expect(logged.filter((line) => line.includes('NG0200'))).toEqual([]);
    expect(logged.filter((line) => line.includes('error loading translation'))).toEqual([]);
    http.verify();
  });
});
