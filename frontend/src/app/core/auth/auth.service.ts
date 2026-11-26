import { DOCUMENT, Injectable, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { firstValueFrom } from 'rxjs';
import { MeApi } from '../api/me-api';
import { LanguageService } from '../i18n/language.service';
import { ModuleAccessService } from '../modules/module-access.service';
import { createAuthConfig } from './auth.config';
import { AuthSession } from './auth-session';
import { CurrentUserService } from './current-user.service';

export type AuthState = 'initializing' | 'authenticated' | 'unauthenticated' | 'unavailable';

/**
 * Connexion OIDC (code + PKCE) aupres du fournisseur d'identite, via angular-oauth2-oidc.
 *
 * Le frontend ne sait rien de Keycloak (ADR-08) : l'agence et les droits sont lus par le
 * backend (GET /api/v1/me). Les jetons sont gardes en sessionStorage par la bibliotheque
 * (stockage par defaut) : ils disparaissent a la fermeture de l'onglet.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly language = inject(LanguageService);
  private readonly session = inject(AuthSession);
  private readonly currentUser = inject(CurrentUserService);
  private readonly modules = inject(ModuleAccessService);
  private readonly document = inject(DOCUMENT);
  private readonly meApi = inject(MeApi);

  private readonly stateSignal = signal<AuthState>('initializing');
  private initialization: Promise<void> | null = null;

  readonly state = this.stateSignal.asReadonly();

  constructor() {
    this.session.connect({
      accessToken: () => this.accessToken(),
      startLogin: () => this.startLogin(),
    });
  }

  /**
   * Charge le document de decouverte et traite le retour de connexion (code dans l'URL).
   * Appele une seule fois au demarrage (appels suivants partages). Ne rejette jamais :
   * si le fournisseur d'identite est injoignable, l'etat passe a 'unavailable'.
   */
  init(): Promise<void> {
    this.initialization ??= this.initialize();
    return this.initialization;
  }

  /** Recommence l'initialisation (bouton « Reessayer » de la page de connexion indisponible). */
  retry(): Promise<void> {
    this.initialization = null;
    this.session.reset();
    this.stateSignal.set('initializing');
    return this.init();
  }

  /** Redirige vers l'ecran de connexion, dans la langue de l'interface (une seule fois). */
  login(): void {
    this.session.requestLogin();
  }

  /** Vide les etats applicatifs puis deconnecte la session OIDC (retour sur l'application). */
  logout(): void {
    this.currentUser.clear();
    this.modules.clear();
    this.stateSignal.set('unauthenticated');
    this.oauth.logOut();
  }

  /** Jeton d'acces encore valide, sinon null. */
  accessToken(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() || null : null;
  }

  private async initialize(): Promise<void> {
    const origin = this.document.defaultView?.location.origin ?? '';
    this.oauth.configure(createAuthConfig(origin));
    try {
      await this.oauth.loadDiscoveryDocument();
    } catch {
      this.stateSignal.set('unavailable');
      return;
    }
    // Retour du fournisseur d'identite : code d'autorisation dans l'URL, lu avant que tryLogin() le retire.
    const returningFromLogin = hasAuthorizationCode(
      this.document.defaultView?.location.search ?? '',
    );
    let codeExchanged = false;
    try {
      await this.oauth.tryLogin();
      codeExchanged = returningFromLogin;
    } catch {
      // Retour de connexion invalide (etat ou code refuse) : traite comme non connecte,
      // la garde relancera la connexion. Rien n'est journalise ici (pas de jeton dans les logs).
    }
    if (!this.oauth.hasValidAccessToken()) {
      this.stateSignal.set('unauthenticated');
      return;
    }
    // Code + PKCE : le renouvellement utilise le jeton de rafraichissement (useSilentRefresh non active).
    this.oauth.setupAutomaticSilentRefresh();
    this.stateSignal.set('authenticated');
    void this.currentUser.load();
    void this.modules.refresh();
    if (codeExchanged) {
      // Une seule fois par connexion (pas a chaque rechargement) ; un echec n'empeche rien.
      void firstValueFrom(this.meApi.loginEvent()).catch(() => undefined);
    }
  }

  private startLogin(): void {
    this.oauth.initCodeFlow('', { ui_locales: this.language.current().code });
  }
}

/** Vrai si l'URL porte un code d'autorisation et son etat (retour du flux code + PKCE). */
function hasAuthorizationCode(search: string): boolean {
  const params = new URLSearchParams(search);
  return params.has('code') && params.has('state');
}
