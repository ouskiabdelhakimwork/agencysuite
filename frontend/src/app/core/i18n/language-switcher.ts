import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageService } from './language.service';

/** Selecteur de langue FR / AR / EN affiche dans l'en-tete. */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslatePipe],
  template: `
    <nav class="lang-switcher" [attr.aria-label]="'shell.language' | translate">
      @for (lang of language.languages; track lang.code) {
        <button
          type="button"
          [attr.lang]="lang.code"
          [class.active]="lang.code === language.current().code"
          [attr.aria-pressed]="lang.code === language.current().code"
          (click)="language.use(lang.code)"
        >
          {{ lang.label }}
        </button>
      }
    </nav>
  `,
  styles: `
    .lang-switcher {
      display: flex;
      gap: 0.25rem;
    }
    button {
      padding-block: 0.25rem;
      padding-inline: 0.75rem;
      border: 1px solid var(--color-border);
      border-radius: 999px;
      background: transparent;
      color: inherit;
      font: inherit;
      cursor: pointer;
    }
    button.active {
      background: var(--color-primary);
      border-color: var(--color-primary);
      color: #fff;
    }
  `,
})
export class LanguageSwitcher {
  protected readonly language = inject(LanguageService);
}
