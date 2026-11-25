import { Component, computed, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { CurrentUserService } from '../../core/auth/current-user.service';

const MESSAGE_BY_STATE: Readonly<Record<string, string>> = {
  'no-agency': 'auth.denied.noAgency',
  'not-activated': 'auth.denied.notActivated',
  'agency-unavailable': 'auth.denied.agencyUnavailable',
};

/**
 * Page « acces refuse » : le texte depend uniquement de l'etat de CurrentUserService
 * (jamais d'une valeur venue de l'URL). Publique : aucune garde.
 */
@Component({
  selector: 'app-access-denied',
  imports: [TranslatePipe],
  template: `
    <section class="auth-page">
      <h1>{{ 'auth.denied.title' | translate }}</h1>
      <p class="detail" role="alert">{{ message() | translate }}</p>
      <button type="button" class="action" (click)="logout()">
        {{ 'auth.logout' | translate }}
      </button>
    </section>
  `,
  styleUrl: '../auth-page.scss',
})
export class AccessDenied {
  private readonly auth = inject(AuthService);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly message = computed(
    () => MESSAGE_BY_STATE[this.currentUser.state()] ?? 'auth.denied.generic',
  );

  protected logout(): void {
    this.auth.logout();
  }
}
