import { HttpErrorResponse } from '@angular/common/http';

/** Prefixe des types RFC 9457 emis par le backend (urn:agencysuite:problem:<code>). */
export const PROBLEM_TYPE_PREFIX = 'urn:agencysuite:problem:';

interface ProblemBody {
  readonly type?: unknown;
  readonly detail?: unknown;
}

function problemBody(error: unknown): ProblemBody | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = error.error;
  return typeof body === 'object' && body !== null ? (body as ProblemBody) : null;
}

/** Code du probleme (ex. « last-manager »), ou null si la reponse n'est pas un probleme du backend. */
export function problemCode(error: unknown): string | null {
  const type = problemBody(error)?.type;
  return typeof type === 'string' && type.startsWith(PROBLEM_TYPE_PREFIX)
    ? type.slice(PROBLEM_TYPE_PREFIX.length)
    : null;
}

/**
 * Message traduit par le serveur (champ `detail`, selon Accept-Language), seulement pour un
 * probleme du backend. Sinon null : l'appelant affiche un message generique traduit, jamais
 * un message technique brut.
 */
export function problemDetail(error: unknown): string | null {
  if (problemCode(error) === null) {
    return null;
  }
  const detail = problemBody(error)?.detail;
  return typeof detail === 'string' && detail.trim() !== '' ? detail : null;
}
