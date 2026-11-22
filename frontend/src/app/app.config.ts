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
import { routes } from './app.routes';
import { acceptLanguageInterceptor } from './core/http/accept-language.interceptor';
import { DEFAULT_LANGUAGE, LanguageService } from './core/i18n/language.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([acceptLanguageInterceptor])),
    // Traductions chargees depuis public/i18n/<langue>.json
    provideTranslateService({
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
      fallbackLang: DEFAULT_LANGUAGE.code,
      lang: DEFAULT_LANGUAGE.code,
    }),
    // Langue appliquee (traductions + lang/dir sur <html>) avant le premier affichage
    provideAppInitializer(() => inject(LanguageService).init()),
  ],
};
