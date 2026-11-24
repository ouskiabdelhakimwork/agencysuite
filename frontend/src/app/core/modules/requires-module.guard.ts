import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { ModuleAccessService } from './module-access.service';
import { ModuleCode } from './module-code';

/**
 * N'autorise la route que si le module est actif pour l'agence ; sinon redirige vers
 * la page « module non active ». CanMatch : le code charge en differe du module n'est
 * meme pas telecharge. Confort d'affichage : le serveur reste seul juge de l'acces.
 */
export function requiresModule(code: ModuleCode): CanMatchFn {
  return async () => {
    const access = inject(ModuleAccessService);
    const router = inject(Router);
    await access.load();
    return access.has(code)
      ? true
      : router.createUrlTree(['/module-non-active'], { queryParams: { module: code } });
  };
}
