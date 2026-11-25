import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { acceptLanguageInterceptor } from './core/http/accept-language.interceptor';
import { authTokenInterceptor } from './core/http/auth-token.interceptor';
import { DEFAULT_LANGUAGE, LanguageService } from './core/i18n/language.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Intercepteurs fonctionnels seulement : l'intercepteur de la bibliotheque OIDC, enregistre
    // en HTTP_INTERCEPTORS par provideOAuthClient, n'est pas actif (pas de withInterceptorsFromDi).
    provideHttpClient(withInterceptors([acceptLanguageInterceptor, authTokenInterceptor])),
    // Sans configuration : aucun envoi automatique du jeton (authTokenInterceptor s'en charge).
    provideOAuthClient(),
    // Traductions chargees depuis public/i18n/<langue>.json
    provideTranslateService({
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
      fallbackLang: DEFAULT_LANGUAGE.code,
      lang: DEFAULT_LANGUAGE.code,
    }),
    // Langue appliquee (traductions + lang/dir sur <html>) avant le premier affichage
    provideAppInitializer(() => inject(LanguageService).init()),
    // Connexion OIDC, apres la langue (la langue courante est fixee de facon synchrone par
    // l'initialiseur precedent). Exception assumee au demarrage non bloquant : le retour de
    // connexion doit etre traite avant le routeur. init() ne rejette jamais.
    provideAppInitializer(() => inject(AuthService).init()),
  ],
};
