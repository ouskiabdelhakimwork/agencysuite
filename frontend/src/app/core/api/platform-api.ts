import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Reponse de GET /api/v1/platform/info (voir PlatformInfoController cote backend). */
export interface PlatformInfo {
  readonly name: string;
  readonly version: string;
  readonly supportedLocales: readonly string[];
  readonly locale: string;
  readonly direction: 'ltr' | 'rtl';
  readonly welcome: string;
}

@Injectable({ providedIn: 'root' })
export class PlatformApi {
  private readonly http = inject(HttpClient);

  info(): Observable<PlatformInfo> {
    return this.http.get<PlatformInfo>('/api/v1/platform/info');
  }
}
