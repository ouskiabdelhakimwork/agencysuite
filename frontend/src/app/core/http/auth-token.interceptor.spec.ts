import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSession } from '../auth/auth-session';
import { authTokenInterceptor } from './auth-token.interceptor';

const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

describe('authTokenInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string | null;
  let startLogin: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    token = 'header.payload.signature';
    startLogin = vi.fn<() => void>();
    TestBed.inject(AuthSession).connect({ accessToken: () => token, startLogin });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function authorizationFor(url: string): string | null {
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    request.flush({});
    return request.request.headers.get('Authorization');
  }

  it('adds the bearer token to an /api/ request', () => {
    expect(authorizationFor('/api/v1/me')).toBe('Bearer header.payload.signature');
  });

  it.each([
    '/i18n/fr.json',
    'https://cdn.example.com/api/x',
    'http://localhost:8180/realms/agencysuite/protocol/openid-connect/token',
    '/apix/y',
    'api/v1/relative',
  ])('adds no token outside /api/ (%s)', (url) => {
    expect(authorizationFor(url)).toBeNull();
  });

  it('adds no header when there is no token', () => {
    token = null;

    expect(authorizationFor('/api/v1/me')).toBeNull();
  });

  it('starts the login on a 401 from /api/ and propagates the error', () => {
    let received: unknown;
    http.get('/api/v1/me').subscribe({ error: (e: unknown) => (received = e) });
    backend.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);

    expect(startLogin).toHaveBeenCalledTimes(1);
    expect(received).toBeInstanceOf(HttpErrorResponse);
    expect((received as HttpErrorResponse).status).toBe(401);
  });

  it('starts the login only once for two simultaneous 401', () => {
    http.get('/api/v1/me').subscribe({ error: () => undefined });
    http.get('/api/v1/me/modules').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);
    backend.expectOne('/api/v1/me/modules').flush(null, UNAUTHORIZED);

    expect(startLogin).toHaveBeenCalledTimes(1);
  });

  it('does not start the login on a 401 outside /api/ nor on a 403', () => {
    http.get('/i18n/fr.json').subscribe({ error: () => undefined });
    http.get('/api/v1/me').subscribe({ error: () => undefined });
    backend.expectOne('/i18n/fr.json').flush(null, UNAUTHORIZED);
    backend.expectOne('/api/v1/me').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(startLogin).not.toHaveBeenCalled();
  });
});
