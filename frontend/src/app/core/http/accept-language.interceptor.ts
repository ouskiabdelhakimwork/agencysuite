import { HttpInterceptorFn } from '@angular/common/http';
import { DOCUMENT, inject } from '@angular/core';
import { DEFAULT_LANGUAGE } from '../i18n/language.service';

/**
 * Envoie la langue de l'interface au serveur (en-tete Accept-Language) :
 * les messages d'erreur, e-mails et PDF generes cote serveur suivent ainsi
 * la langue choisie par l'utilisateur.
 *
 * La langue est lue sur <html lang>, que LanguageService tient a jour. L'intercepteur
 * ne doit pas injecter LanguageService : le chargeur de traductions passe par HttpClient
 * pendant la construction de TranslateService, et LanguageService depend de
 * TranslateService (dependance circulaire NG0200, fr.json jamais charge).
 *
 * Seules les requetes vers notre API (/api/...) recoivent l'en-tete.
 */
export const acceptLanguageInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const lang = inject(DOCUMENT).documentElement.lang || DEFAULT_LANGUAGE.code;
  return next(req.clone({ setHeaders: { 'Accept-Language': lang } }));
};
