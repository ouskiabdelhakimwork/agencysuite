import { AuthConfig } from 'angular-oauth2-oidc';

/**
 * Configuration OIDC du realm des agences (code + PKCE, client public).
 *
 * Options verifiees dans les types de angular-oauth2-oidc 22.0.2 (classe AuthConfig) :
 * - requireHttps n'est pas renseigne : sa valeur par defaut 'remoteOnly' n'autorise HTTP
 *   que pour localhost ;
 * - PKCE est actif par defaut avec responseType 'code' (disablePKCE = false) ;
 * - le stockage des jetons est sessionStorage par defaut (aucun OAuthStorage fourni).
 *
 * TODO(etape-12): lire cette configuration au demarrage (fichier servi avec l'application)
 * pour la recette et la production, au lieu de valeurs ecrites dans le code.
 */
export function createAuthConfig(origin: string): AuthConfig {
  return {
    issuer: 'http://localhost:8180/realms/agencysuite',
    clientId: 'agencysuite-frontend',
    responseType: 'code',
    scope: 'openid profile email organization',
    redirectUri: origin + '/',
    postLogoutRedirectUri: origin + '/',
  };
}
