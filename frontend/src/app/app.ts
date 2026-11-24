import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageSwitcher } from './core/i18n/language-switcher';
import { ModuleAccessService } from './core/modules/module-access.service';
import { MODULE_CODES, modulePath } from './core/modules/module-code';

/** Coquille de l'application : en-tete (marque, langue), menu des modules et zone de contenu. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly access = inject(ModuleAccessService);

  /** Modules actifs, dans l'ordre du catalogue (affichage seulement : le serveur controle l'acces). */
  protected readonly menu = computed(() =>
    MODULE_CODES.filter((code) => this.access.has(code)).map((code) => ({
      code,
      path: '/' + modulePath(code),
    })),
  );

  constructor() {
    // Chargement non bloquant : load() ne rejette jamais, une erreur passe dans l'etat 'error'.
    void this.access.load();
  }

  protected retry(): void {
    void this.access.refresh();
  }
}
