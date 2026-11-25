import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import ar from '../../../../public/i18n/ar.json';
import fr from '../../../../public/i18n/fr.json';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService, CurrentUserState } from '../../core/auth/current-user.service';
import { AccessDenied } from './access-denied';

describe('AccessDenied', () => {
  const logout = vi.fn();

  async function render(state: CurrentUserState, url = '/acces-refuse', lang = 'fr') {
    logout.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'acces-refuse', component: AccessDenied }]),
        provideTranslateService({ fallbackLang: 'fr' }),
        { provide: AuthService, useValue: { logout } },
        { provide: CurrentUserService, useValue: { state: signal(state) } },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('ar', ar);
    translate.use(lang);
    const harness = await RouterTestingHarness.create(url);
    return harness.routeNativeElement as HTMLElement;
  }

  it.each<[CurrentUserState, string]>([
    ['no-agency', fr.auth.denied.noAgency],
    ['not-activated', fr.auth.denied.notActivated],
    ['agency-unavailable', fr.auth.denied.agencyUnavailable],
    ['error', fr.auth.denied.generic],
    ['ready', fr.auth.denied.generic],
  ])('shows the text for the %s state', async (state, text) => {
    const el = await render(state);

    expect(el.querySelector('h1')?.textContent?.trim()).toBe(fr.auth.denied.title);
    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(text);
  });

  it('shows the Arabic text', async () => {
    const el = await render('not-activated', '/acces-refuse', 'ar');

    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(ar.auth.denied.notActivated);
  });

  it('logs out with the button', async () => {
    const el = await render('no-agency');
    const button = el.querySelector<HTMLButtonElement>('button.action');
    expect(button?.textContent?.trim()).toBe(fr.auth.logout);

    button!.click();

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('never shows a value received in the URL', async () => {
    const value = '<img src=x onerror=alert(1)>';
    const el = await render('no-agency', `/acces-refuse?state=${encodeURIComponent(value)}#x`);

    expect(el.innerHTML).not.toContain('onerror');
    expect(el.textContent).not.toContain(value);
    expect(el.querySelector('img')).toBeNull();
    expect(el.querySelector('.detail')?.textContent?.trim()).toBe(fr.auth.denied.noAgency);
  });
});
