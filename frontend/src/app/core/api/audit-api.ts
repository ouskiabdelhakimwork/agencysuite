import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Actions connues du journal, alignees sur l'enum backend AuditAction (meme ordre). Une valeur
 * absente de cette liste s'affiche « Autre action », jamais en code brut.
 */
export const AUDIT_ACTIONS = [
  'AGENCY_CREATED',
  'MODULE_ENABLED',
  'MODULE_DISABLED',
  'USER_LOGGED_IN',
  'ORGANIZATION_PROVISIONED',
  'USER_INVITED',
  'USER_ACTIVATED',
  'INVITATION_RESENT',
  'ROLE_ASSIGNED',
  'ROLE_REVOKED',
  'USER_DISABLED',
  'USER_ENABLED',
] as const;
export type AuditAction = (typeof AUDIT_ACTIONS)[number];

export function isAuditAction(value: unknown): value is AuditAction {
  return typeof value === 'string' && (AUDIT_ACTIONS as readonly string[]).includes(value);
}

/** Origine d'une action, alignee sur l'enum backend ActorType. */
export type ActorType = 'AGENCY_USER' | 'PLATFORM_USER' | 'SYSTEM';

/** Entree du journal (record AuditEvent de AuditEventsController), sans adresse IP. */
export interface AuditEvent {
  /** Instant ISO-8601. */
  readonly occurredAt: string;
  readonly actorType: string;
  /** Nul pour une action du systeme. */
  readonly actorId: string | null;
  readonly action: string;
  readonly entityType: string;
  readonly entityId: string | null;
  readonly details: Readonly<Record<string, unknown>> | null;
}

/** Page du journal (record AuditEventsResponse). */
export interface AuditEventsResponse {
  readonly items: readonly AuditEvent[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

export const AUDIT_PAGE_SIZE = 50;

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);

  /** Page `page` (a partir de 0), de la plus recente a la plus ancienne. */
  list(page: number, size = AUDIT_PAGE_SIZE): Observable<AuditEventsResponse> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<AuditEventsResponse>('/api/v1/audit-events', { params });
  }
}
