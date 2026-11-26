import { HttpErrorResponse } from '@angular/common/http';
import { problemCode, problemDetail } from './problem';

function error(body: unknown, status = 409): HttpErrorResponse {
  return new HttpErrorResponse({ error: body, status });
}

describe('problem', () => {
  it('reads the code and the translated detail of a backend problem', () => {
    const e = error({ type: 'urn:agencysuite:problem:last-manager', detail: 'Dernier gérant.' });

    expect(problemCode(e)).toBe('last-manager');
    expect(problemDetail(e)).toBe('Dernier gérant.');
  });

  it.each([
    ['another type', { type: 'about:blank', detail: 'Internal: SQL state 23505' }],
    ['no type', { detail: 'boom' }],
    ['an empty detail', { type: 'urn:agencysuite:problem:x', detail: '  ' }],
    ['a text body', 'java.lang.NullPointerException'],
    ['no body', null],
  ])('gives no detail for %s', (_label, body) => {
    expect(problemDetail(error(body))).toBeNull();
  });

  it('ignores errors that are not HTTP errors', () => {
    expect(problemCode(new Error('x'))).toBeNull();
    expect(problemDetail({ error: { type: 'urn:agencysuite:problem:x', detail: 'y' } })).toBeNull();
  });
});
