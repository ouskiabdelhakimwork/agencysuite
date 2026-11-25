import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthSession } from '../auth/auth-session';

/**
 * Envoie le jeton d'acces (Authorization: Bearer) uniquement a notre API (/api/...) :
 * jamais aux fichiers de traduction, au fournisseur d'identite ni a une URL externe.
 * L'envoi automatique de angular-oauth2-oidc n'est pas utilise : la regle reste ici, testee.
 *
 * Sur un 401 de l'API (jeton absent ou expire), la connexion est relancee une seule fois
 * (AuthSession), puis l'erreur est propagee a l'appelant.
 *
 * N'injecte que AuthSession, qui n'a aucune dependance (voir AuthSession pour NG0200).
 */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) {
    return next(req);
  }
  const session = inject(AuthSession);
  const token = session.accessToken();
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        session.requestLogin();
      }
      return throwError(() => error);
    }),
  );
};
