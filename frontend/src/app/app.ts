import { Component, computed, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth/auth.service';
import { CurrentUserService, isAccessDenied } from './core/auth/current-user.service';
import { LanguageSwitcher } from './core/i18n/language-switcher';
import { ModuleAccessService } from './core/modules/module-access.service';
import { MODULE_CODES, modulePath } from './core/modules/module-code';

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
  private readonly router = inject(Router);

  /** Modules actifs, dans l'ordre du catalogue (affichage seulement : le serveur controle l'acces). */
  protected readonly menu = computed(() =>
    MODULE_CODES.filter((code) => this.access.has(code)).map((code) => ({
      code,
      path: '/' + modulePath(code),
    })),
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
