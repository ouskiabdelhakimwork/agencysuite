import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { LanguageService } from './language.service';

describe('LanguageService', () => {
  let service: LanguageService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideTranslateService({ fallbackLang: 'fr' })],
    });
    service = TestBed.inject(LanguageService);
  });

  it('switches the whole page to right-to-left for Arabic', () => {
    service.use('ar');

    expect(service.current().code).toBe('ar');
    expect(service.direction()).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
  });

  it('switches back to left-to-right for French and English', () => {
    service.use('ar');
    service.use('en');

    expect(document.documentElement.dir).toBe('ltr');
    expect(document.documentElement.lang).toBe('en');
  });

  it('falls back to French for an unsupported language', () => {
    service.use('de');

    expect(service.current().code).toBe('fr');
  });

  it('remembers the chosen language for the next visit', () => {
    service.use('ar');
    expect(localStorage.getItem('agencysuite.lang')).toBe('ar');

    service.init();
    expect(service.current().code).toBe('ar');
  });
});
