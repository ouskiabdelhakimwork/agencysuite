import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Reponse de GET /api/v1/me/modules (voir MeModulesController cote backend).
 * Les codes restent des chaines : le filtrage des codes connus se fait dans ModuleAccessService.
 */
export interface MyModulesResponse {
  readonly modules: readonly string[];
}

/** Reponse de GET /api/v1/me (voir MeController cote backend). */
export interface MeResponse {
  readonly id: string;
  readonly email: string;
  readonly fullName: string;
  readonly agency: { readonly id: string; readonly name: string };
  readonly roles: readonly string[];
  readonly permissions: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class MeApi {
  private readonly http = inject(HttpClient);

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>('/api/v1/me');
  }

  /** Signale une connexion reussie (POST /api/v1/me/login-events, 204) : journal d'audit et derniere connexion. */
  loginEvent(): Observable<void> {
    return this.http.post<void>('/api/v1/me/login-events', null);
  }

  myModules(): Observable<MyModulesResponse> {
    return this.http.get<MyModulesResponse>('/api/v1/me/modules');
  }
}
