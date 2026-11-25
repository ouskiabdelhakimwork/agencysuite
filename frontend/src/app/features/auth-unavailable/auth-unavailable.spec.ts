import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import ar from '../../../../public/i18n/ar.json';
import fr from '../../../../public/i18n/fr.json';
import { AuthService } from '../../core/auth/auth.service';
import { AuthUnavailable } from './auth-unavailable';

@Component({ template: '' })
class HomeStub {}

describe('AuthUnavailable', () => {
  const retry = vi.fn();

  async function render(url = '/connexion-indisponible', lang = 'fr') {
    retry.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'connexion-indisponible', component: AuthUnavailable },
          { path: '', component: HomeStub },
        ]),
        provideTranslateService({ fallbackLang: 'fr' }),
        { provide: AuthService, useValue: { retry } },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('ar', ar);
    translate.use(lang);
    const harness = await RouterTestingHarness.create(url);
    return { harness, el: harness.routeNativeElement as HTMLElement };
  }

  it('shows the translated message and retry button', async () => {
    const { el } = await render();

    expect(el.querySelector('h1')?.textContent?.trim()).toBe(fr.auth.unavailable.title);
    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(fr.auth.unavailable.detail);
    expect(el.querySelector('button.action')?.textContent?.trim()).toBe(fr.auth.unavailable.retry);
  });

  it('shows the Arabic message', async () => {
    const { el } = await render('/connexion-indisponible', 'ar');

    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(ar.auth.unavailable.detail);
  });

  it('retries the initialization then goes back to the home page', async () => {
    const { harness, el } = await render();

    el.querySelector<HTMLButtonElement>('button.action')!.click();
    await harness.fixture.whenStable();

    expect(retry).toHaveBeenCalledTimes(1);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('never shows a value received in the URL', async () => {
    const value = '<img src=x onerror=alert(1)>';
    const { el } = await render(`/connexion-indisponible?error=${encodeURIComponent(value)}`);

    expect(el.innerHTML).not.toContain('onerror');
    expect(el.textContent).not.toContain(value);
    expect(el.querySelector('img')).toBeNull();
  });
});
