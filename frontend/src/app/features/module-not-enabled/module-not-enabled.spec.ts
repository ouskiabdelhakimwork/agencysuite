import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import ar from '../../../../public/i18n/ar.json';
import fr from '../../../../public/i18n/fr.json';
import { ModuleNotEnabled } from './module-not-enabled';

describe('ModuleNotEnabled', () => {
  async function render(module: string | null, lang = 'fr'): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [ModuleNotEnabled],
      providers: [
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'fr' }),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap(module === null ? {} : { module })) },
        },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('ar', ar);
    translate.use(lang);
    const fixture = TestBed.createComponent(ModuleNotEnabled);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the translated module name for a valid code', async () => {
    const el = await render('TRAVEL');

    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(
      "Le module Voyages n'est pas activé pour votre agence. Contactez votre administrateur ou l'éditeur pour le souscrire.",
    );
    expect(el.querySelector('bdi.module-name')?.textContent).toBe('Voyages');
    expect(el.querySelector('a.back')?.getAttribute('href')).toBe('/');
  });

  it('isolates the Arabic module name that contains a Latin acronym', async () => {
    const el = await render('MICE', 'ar');

    expect(el.querySelector('bdi.module-name')?.textContent).toBe('المؤتمرات والفعاليات (MICE)');
    expect(el.querySelector('.detail')?.textContent).toContain('غير مفعّلة');
  });

  it.each(['<img src=x onerror=alert(1)>', 'travel', 'UNKNOWN'])(
    'shows a generic text and never the received value for %s',
    async (value) => {
      const el = await render(value);

      expect(el.querySelector('.detail')?.textContent?.trim()).toBe(fr.moduleNotEnabled.generic);
      expect(el.innerHTML).not.toContain(value);
      expect(el.textContent).not.toContain(value);
      expect(el.querySelector('img')).toBeNull();
    },
  );

  it('shows a generic text when no module is given', async () => {
    const el = await render(null);

    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(fr.moduleNotEnabled.generic);
  });
});
