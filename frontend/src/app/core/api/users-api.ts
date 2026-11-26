import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Roles systeme, alignes sur l'enum backend SystemRole (ordre d'affichage). */
export const SYSTEM_ROLES = ['MANAGER', 'SALES', 'OPERATIONS', 'ACCOUNTANT'] as const;
export type SystemRole = (typeof SYSTEM_ROLES)[number];

export function isSystemRole(value: unknown): value is SystemRole {
  return typeof value === 'string' && (SYSTEM_ROLES as readonly string[]).includes(value);
}

/** Etats d'un compte, alignes sur l'enum backend UserStatus. */
export const USER_STATUSES = ['INVITED', 'ACTIVE', 'DISABLED'] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

/** Utilisateur de GET /api/v1/users (record UserItem de UsersController). */
export interface UserItem {
  readonly id: string;
  readonly email: string;
  readonly fullName: string;
  readonly status: string;
  readonly roles: readonly string[];
  /** Instant ISO-8601, null si jamais connecte. */
  readonly lastLoginAt: string | null;
}

/** Corps de POST /api/v1/users (record InviteUserRequest). */
export interface InviteUserRequest {
  readonly email: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly roles: readonly SystemRole[];
}

/** Reponse 201 de POST /api/v1/users (record InvitationResponse). */
export interface InvitationResponse {
  readonly id: string;
  readonly status: string;
  readonly invitationSent: boolean;
}

/** Reponse de POST /api/v1/users/{id}/disable|enable (record StatusChangeResponse). */
export interface StatusChangeResponse {
  readonly identityProviderSynced: boolean;
}

const BASE = '/api/v1/users';

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);

  list(): Observable<UserItem[]> {
    return this.http.get<UserItem[]>(BASE);
  }

  invite(request: InviteUserRequest): Observable<InvitationResponse> {
    return this.http.post<InvitationResponse>(BASE, request);
  }

  changeRoles(userId: string, roles: readonly SystemRole[]): Observable<UserItem> {
    return this.http.put<UserItem>(`${BASE}/${encodeURIComponent(userId)}/roles`, { roles });
  }

  /** 202 sans corps. */
  resendInvitation(userId: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${encodeURIComponent(userId)}/invitation`, null);
  }

  disable(userId: string): Observable<StatusChangeResponse> {
    return this.http.post<StatusChangeResponse>(
      `${BASE}/${encodeURIComponent(userId)}/disable`,
      null,
    );
  }

  enable(userId: string): Observable<StatusChangeResponse> {
    return this.http.post<StatusChangeResponse>(
      `${BASE}/${encodeURIComponent(userId)}/enable`,
      null,
    );
  }
}
