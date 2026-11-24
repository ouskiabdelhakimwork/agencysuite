import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MeApi } from '../api/me-api';
import { ModuleCode, isModuleCode } from './module-code';

export type ModuleAccessState = 'idle' | 'loading' | 'ready' | 'no-agency' | 'error';

/** Type RFC 9457 renvoye par le backend quand la requete n'a pas de perimetre agence. */
export const AGENCY_SCOPE_REQUIRED = 'urn:agencysuite:problem:agency-scope-required';

const NONE: ReadonlySet<ModuleCode> = new Set<ModuleCode>();

/**
 * Modules actifs de l'agence courante, pour adapter l'interface (menu, routes).
 *
 * Confort d'affichage uniquement : le controle d'acces est fait par le serveur
 * (@RequiresModule et RLS). Ferme par defaut : en cas de doute, aucun module.
 * L'etat vit seulement en memoire (rien dans le stockage du navigateur).
 */
@Injectable({ providedIn: 'root' })
export class ModuleAccessService {
  private readonly api = inject(MeApi);

  private readonly stateSignal = signal<ModuleAccessState>('idle');
  private readonly enabledSignal = signal<ReadonlySet<ModuleCode>>(NONE);
  private pending: Promise<void> | null = null;

  readonly state = this.stateSignal.asReadonly();
  readonly enabled = this.enabledSignal.asReadonly();

  /** Lit un signal : se met a jour dans les templates. */
  has(code: ModuleCode): boolean {
    return this.enabledSignal().has(code);
  }

  /** Charge les modules une seule fois (appels paralleles partages). Ne rejette jamais. */
  load(): Promise<void> {
    if (this.pending) {
      return this.pending;
    }
    const state = this.stateSignal();
    if (state === 'ready' || state === 'no-agency') {
      return Promise.resolve();
    }
    return this.fetch();
  }

  /** Force un nouvel appel (servira apres la connexion, etape 3). Ne rejette jamais. */
  refresh(): Promise<void> {
    return this.pending ?? this.fetch();
  }

  private fetch(): Promise<void> {
    this.stateSignal.set('loading');
    this.pending = firstValueFrom(this.api.myModules())
      .then(
        (response) => {
          const modules = Array.isArray(response?.modules) ? response.modules : [];
          this.enabledSignal.set(new Set(modules.filter(isModuleCode)));
          this.stateSignal.set('ready');
        },
        (error: unknown) => {
          this.enabledSignal.set(NONE);
          this.stateSignal.set(isAgencyScopeRequired(error) ? 'no-agency' : 'error');
        },
      )
      .finally(() => {
        this.pending = null;
      });
    return this.pending;
  }
}

function isAgencyScopeRequired(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse) || error.status !== 403) {
    return false;
  }
  const body: unknown = error.error;
  return (
    typeof body === 'object' &&
    body !== null &&
    (body as { type?: unknown }).type === AGENCY_SCOPE_REQUIRED
  );
}
