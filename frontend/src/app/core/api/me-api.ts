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

@Injectable({ providedIn: 'root' })
export class MeApi {
  private readonly http = inject(HttpClient);

  myModules(): Observable<MyModulesResponse> {
    return this.http.get<MyModulesResponse>('/api/v1/me/modules');
  }
}
