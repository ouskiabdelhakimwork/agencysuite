import { Routes } from '@angular/router';
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
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  ...moduleRoutes,
  {
    path: 'module-non-active',
    loadComponent: () =>
      import('./features/module-not-enabled/module-not-enabled').then((m) => m.ModuleNotEnabled),
  },
  // Doit rester la derniere route.
  { path: '**', redirectTo: '' },
];
