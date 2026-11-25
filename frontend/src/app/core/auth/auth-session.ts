import { Injectable } from '@angular/core';

/** Ce que AuthService expose a l'intercepteur HTTP. */
export interface AuthSessionSource {
  accessToken(): string | null;
  startLogin(): void;
}

/**
 * Pont sans dependance entre l'intercepteur HTTP et AuthService.
 *
 * L'intercepteur ne doit dependre d'aucun service qui injecte HttpClient ou TranslateService
 * a sa construction (dependance circulaire NG0200) : AuthService injecte OAuthService (HttpClient)
 * et LanguageService (TranslateService). Il s'enregistre donc ici a sa construction, et
 * l'intercepteur n'injecte que ce pont.
 *
 * Il garantit aussi qu'une seule redirection vers l'ecran de connexion est lancee, meme si
 * plusieurs requetes recoivent un 401 en meme temps ou si la garde demande aussi la connexion.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private source: AuthSessionSource | null = null;
  private loginRequested = false;

  connect(source: AuthSessionSource): void {
    this.source = source;
  }

  accessToken(): string | null {
    return this.source?.accessToken() ?? null;
  }

  /** Lance la connexion une seule fois (la page part ensuite vers le fournisseur d'identite). */
  requestLogin(): void {
    if (this.loginRequested || !this.source) {
      return;
    }
    this.loginRequested = true;
    this.source.startLogin();
  }

  /** Autorise une nouvelle demande de connexion (apres « Reessayer »). */
  reset(): void {
    this.loginRequested = false;
  }
}
