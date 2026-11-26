import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { requiresPermission } from './core/auth/requires-permission.guard';
import { MODULE_CODES, modulePath } from './core/modules/module-code';
import { requiresModule } from './core/modules/requires-module.guard';

/** Une route par module activable, dans l'ordre du catalogue. */
const moduleRoutes: Routes = MODULE_CODES.map((code) => ({
  path: modulePath(code),
  canMatch: [requiresModule(code)],
  data: { module: code },
  // TODO(module): remplacer par le vrai module quand il sera construit.
  loadComponent: () =>
    import('./features/module-placeholder/module-placeholder').then((m) => m.ModulePlaceholder),
}));

export const routes: Routes = [
  // Pages publiques (sans garde), placees avant la route protegee : elles s'affichent
  // meme sans session ou quand le fournisseur d'identite est injoignable.
  {
    path: 'connexion-indisponible',
    loadComponent: () =>
      import('./features/auth-unavailable/auth-unavailable').then((m) => m.AuthUnavailable),
  },
  {
    path: 'acces-refuse',
    loadComponent: () =>
      import('./features/access-denied/access-denied').then((m) => m.AccessDenied),
  },
  // Application : la garde de connexion du parent est evaluee avant celles des enfants
  // (requiresModule), donc aucun appel a l'API n'est fait sans session.
  {
    path: '',
    canMatch: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      ...moduleRoutes,
      // Administration : confort d'affichage, le serveur controle chaque appel (@RequiresPermission).
      {
        path: 'administration/utilisateurs',
        canMatch: [requiresPermission('user.manage')],
        loadComponent: () => import('./features/admin-users/admin-users').then((m) => m.AdminUsers),
      },
      {
        path: 'administration/journal',
        canMatch: [requiresPermission('audit.read')],
        loadComponent: () => import('./features/admin-audit/admin-audit').then((m) => m.AdminAudit),
      },
      {
        path: 'module-non-active',
        loadComponent: () =>
          import('./features/module-not-enabled/module-not-enabled').then(
            (m) => m.ModuleNotEnabled,
          ),
      },
    ],
  },
  // Doit rester la derniere route.
  { path: '**', redirectTo: '' },
];
