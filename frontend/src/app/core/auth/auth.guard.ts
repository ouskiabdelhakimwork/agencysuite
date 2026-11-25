import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Exige une session : attend l'initialisation OIDC, puis
 * - connecte : autorise ;
 * - non connecte : lance la connexion (redirection) et refuse ;
 * - fournisseur d'identite injoignable : page « connexion indisponible ».
 *
 * Confort d'affichage : sans jeton valide, le serveur refuse de toute facon (401).
 */
export const authGuard: CanMatchFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.init();
  switch (auth.state()) {
    case 'authenticated':
      return true;
    case 'unavailable':
      return router.createUrlTree(['/connexion-indisponible']);
    default:
      auth.login();
      return false;
  }
};
