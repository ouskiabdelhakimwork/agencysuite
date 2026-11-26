import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MeApi, MeResponse } from '../api/me-api';
import { AGENCY_SCOPE_REQUIRED } from '../modules/module-access.service';

export type CurrentUserState =
  'idle' | 'loading' | 'ready' | 'no-agency' | 'not-activated' | 'agency-unavailable' | 'error';

/** Etats pour lesquels l'application est refusee (page « acces refuse »). */
export type AccessDeniedState = Extract<
  CurrentUserState,
  'no-agency' | 'not-activated' | 'agency-unavailable'
>;

/** Types RFC 9457 renvoyes par le backend (TenantScopeFilter). */
export const ACCESS_DENIED = 'urn:agencysuite:problem:access-denied';
export const AGENCY_UNAVAILABLE = 'urn:agencysuite:problem:agency-unavailable';

const DENIED_BY_TYPE: Readonly<Record<string, AccessDeniedState>> = {
  [AGENCY_SCOPE_REQUIRED]: 'no-agency',
  [ACCESS_DENIED]: 'not-activated',
  [AGENCY_UNAVAILABLE]: 'agency-unavailable',
};

export function isAccessDenied(state: CurrentUserState): state is AccessDeniedState {
  return state === 'no-agency' || state === 'not-activated' || state === 'agency-unavailable';
}

/**
 * Profil de l'utilisateur connecte (GET /api/v1/me) : nom, agence, roles et permissions.
 *
 * Ferme par defaut : en cas d'erreur, aucun profil et aucune permission. has() sert au
 * confort d'affichage seulement, le serveur controle chaque acces (@RequiresPermission).
 * L'etat vit seulement en memoire.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly api = inject(MeApi);

  private readonly stateSignal = signal<CurrentUserState>('idle');
  private readonly profileSignal = signal<MeResponse | null>(null);
  private readonly permissions = computed(
    () => new Set<string>(this.profileSignal()?.permissions ?? []),
  );
  private pending: Promise<void> | null = null;

  readonly state = this.stateSignal.asReadonly();
  readonly profile = this.profileSignal.asReadonly();

  /** Lit un signal : se met a jour dans les templates. */
  has(permission: string): boolean {
    return this.permissions().has(permission);
  }

  /** Charge le profil une seule fois (appels paralleles partages). Ne rejette jamais. */
  load(): Promise<void> {
    if (this.pending) {
      return this.pending;
    }
    const state = this.stateSignal();
    if (state === 'ready' || isAccessDenied(state)) {
      return Promise.resolve();
    }
    return this.fetch();
  }

  /**
   * Relit le profil (nouvel appel a GET /api/v1/me), par exemple apres un changement de ses
   * propres roles. Un chargement en cours est attendu d'abord : la reponse obtenue reflete
   * toujours l'etat posterieur a l'appel. Ne rejette jamais.
   */
  reload(): Promise<void> {
    if (!this.pending) {
      return this.fetch();
    }
    return this.pending.then(() => this.pending ?? this.fetch());
  }

  /** Oublie le profil (deconnexion). */
  clear(): void {
    this.profileSignal.set(null);
    this.stateSignal.set('idle');
  }

  private fetch(): Promise<void> {
    this.stateSignal.set('loading');
    this.pending = firstValueFrom(this.api.me())
      .then(
        (profile) => {
          this.profileSignal.set(profile);
          this.stateSignal.set('ready');
        },
        (error: unknown) => {
          this.profileSignal.set(null);
          this.stateSignal.set(deniedState(error) ?? 'error');
        },
      )
      .finally(() => {
        this.pending = null;
      });
    return this.pending;
  }
}

function deniedState(error: unknown): AccessDeniedState | null {
  if (!(error instanceof HttpErrorResponse) || error.status !== 403) {
    return null;
  }
  const body: unknown = error.error;
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const type = (body as { type?: unknown }).type;
  return typeof type === 'string' && Object.hasOwn(DENIED_BY_TYPE, type)
    ? DENIED_BY_TYPE[type]
    : null;
}
