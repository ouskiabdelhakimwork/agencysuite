import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Page « service de connexion indisponible » (fournisseur d'identite injoignable).
 * Publique : aucune garde. « Reessayer » relance l'initialisation puis retourne a l'accueil,
 * dont la garde decide (connexion, application ou retour ici).
 */
@Component({
  selector: 'app-auth-unavailable',
  imports: [TranslatePipe],
  template: `
    <section class="auth-page">
      <h1>{{ 'auth.unavailable.title' | translate }}</h1>
      <p class="detail" role="alert">{{ 'auth.unavailable.detail' | translate }}</p>
      <button type="button" class="action" [disabled]="retrying()" (click)="retry()">
        {{ 'auth.unavailable.retry' | translate }}
      </button>
    </section>
  `,
  styleUrl: '../auth-page.scss',
})
export class AuthUnavailable {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly retrying = signal(false);

  protected async retry(): Promise<void> {
    this.retrying.set(true);
    try {
      await this.auth.retry();
      await this.router.navigateByUrl('/');
    } finally {
      this.retrying.set(false);
    }
  }
}
