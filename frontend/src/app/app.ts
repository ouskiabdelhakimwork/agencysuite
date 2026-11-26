import { Component, computed, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth/auth.service';
import { CurrentUserService, isAccessDenied } from './core/auth/current-user.service';
import { LanguageSwitcher } from './core/i18n/language-switcher';
import { ModuleAccessService } from './core/modules/module-access.service';
import { ShellNotice } from './core/ui/shell-notice.service';
import { MODULE_CODES, modulePath } from './core/modules/module-code';

/** Ecrans d'administration, dans l'ordre du menu. */
const ADMIN_LINKS = [
  {
    id: 'users',
    path: '/administration/utilisateurs',
    permission: 'user.manage',
    key: 'nav.adminUsers',
  },
  { id: 'audit', path: '/administration/journal', permission: 'audit.read', key: 'nav.adminAudit' },
] as const;

/**
 * Coquille de l'application : en-tete (marque, utilisateur, agence, langue, deconnexion),
 * menu des modules et zone de contenu.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly user = inject(CurrentUserService);
  protected readonly access = inject(ModuleAccessService);
  protected readonly notice = inject(ShellNotice);
  private readonly router = inject(Router);

  /** Modules actifs, dans l'ordre du catalogue (affichage seulement : le serveur controle l'acces). */
  protected readonly menu = computed(() =>
    MODULE_CODES.filter((code) => this.access.has(code)).map((code) => ({
      code,
      path: '/' + modulePath(code),
    })),
  );

  /** Liens d'administration autorises (confort d'affichage : le serveur controle l'acces). */
  protected readonly adminMenu = computed(() =>
    ADMIN_LINKS.filter((link) => this.user.has(link.permission)),
  );

  protected readonly denied = computed(() => isAccessDenied(this.user.state()));

  protected readonly loading = computed(
    () => this.auth.state() === 'initializing' || this.user.state() === 'loading',
  );

  constructor() {
    // Les modules et le profil sont charges par AuthService apres la connexion.
    effect(() => {
      if (this.denied()) {
        void this.router.navigateByUrl('/acces-refuse');
      }
    });
  }

  protected retry(): void {
    void this.access.refresh();
  }

  protected logout(): void {
    this.auth.logout();
  }
}
