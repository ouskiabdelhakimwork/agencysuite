import { Component, ElementRef, afterNextRender, inject, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Petit dialogue de confirmation (jamais window.confirm). Le parent l'affiche avec @if et
 * reagit a `confirmed` ou `cancelled`. Les textes sont des cles de traduction ; `subject`
 * (nom, e-mail) est affiche isole dans un <bdi>, jamais en HTML.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [TranslatePipe],
  template: `
    <div class="backdrop" (click)="cancelled.emit()"></div>
    <div
      class="dialog"
      role="alertdialog"
      aria-modal="true"
      [attr.aria-labelledby]="'confirm-title'"
      (keydown.escape)="cancelled.emit()"
    >
      <h2 id="confirm-title" class="title">{{ titleKey() | translate }}</h2>
      @if (subject(); as s) {
        <p class="subject">
          <bdi>{{ s }}</bdi>
        </p>
      }
      <p class="message">{{ messageKey() | translate }}</p>
      <div class="buttons">
        <button type="button" class="cancel" (click)="cancelled.emit()">
          {{ 'common.cancel' | translate }}
        </button>
        <button type="button" class="confirm" (click)="confirmed.emit()">
          {{ confirmKey() | translate }}
        </button>
      </div>
    </div>
  `,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      background: rgb(0 0 0 / 35%);
      z-index: 10;
    }
    .dialog {
      position: fixed;
      inset-block-start: 20%;
      inset-inline: 0;
      margin-inline: auto;
      inline-size: min(28rem, calc(100% - 2rem));
      padding-block: 1.25rem;
      padding-inline: 1.5rem;
      border-radius: 0.5rem;
      background: var(--color-surface);
      box-shadow: 0 0.5rem 2rem rgb(0 0 0 / 20%);
      z-index: 11;
    }
    .title {
      margin-block: 0 0.75rem;
      font-size: 1.125rem;
    }
    .subject {
      margin-block: 0 0.5rem;
      font-weight: 600;
    }
    .message {
      margin-block: 0 1.25rem;
    }
    .buttons {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }
    button {
      padding-block: 0.375rem;
      padding-inline: 1rem;
      border: 1px solid var(--color-border);
      border-radius: 0.25rem;
      background: transparent;
      color: inherit;
      font: inherit;
      cursor: pointer;
    }
    .confirm {
      border-color: var(--color-danger);
      background: var(--color-danger);
      color: #fff;
    }
  `,
})
export class ConfirmDialog {
  readonly titleKey = input.required<string>();
  readonly messageKey = input.required<string>();
  readonly confirmKey = input.required<string>();
  readonly subject = input<string | null>(null);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    // Focus sur « Annuler » : une action sensible n'est jamais confirmee par un Entree reflexe.
    afterNextRender(() => host.nativeElement.querySelector<HTMLButtonElement>('.cancel')?.focus());
  }
}
