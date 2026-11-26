import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { CurrentUserService } from './current-user.service';

/**
 * N'autorise la route que si le profil courant porte la permission ; sinon redirige vers
 * l'accueil (l'interface ne propose de toute facon pas le lien). Attend le chargement du
 * profil. Confort d'affichage : le serveur reste seul juge (@RequiresPermission).
 */
export function requiresPermission(code: string): CanMatchFn {
  return async () => {
    const user = inject(CurrentUserService);
    const router = inject(Router);
    await user.load();
    return user.has(code) ? true : router.createUrlTree(['/']);
  };
}
