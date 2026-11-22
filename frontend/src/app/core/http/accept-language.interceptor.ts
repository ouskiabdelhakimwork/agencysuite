import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { LanguageService } from '../i18n/language.service';

/**
 * Envoie la langue de l'interface au serveur (en-tete Accept-Language) :
 * les messages d'erreur, e-mails et PDF generes cote serveur suivent ainsi
 * la langue choisie par l'utilisateur.
 */
export const acceptLanguageInterceptor: HttpInterceptorFn = (req, next) => {
  const lang = inject(LanguageService).current().code;
  return next(req.clone({ setHeaders: { 'Accept-Language': lang } }));
};
