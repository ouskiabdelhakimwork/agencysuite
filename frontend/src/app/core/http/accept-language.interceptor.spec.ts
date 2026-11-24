import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { acceptLanguageInterceptor } from './accept-language.interceptor';

describe('acceptLanguageInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([acceptLanguageInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    document.documentElement.lang = 'fr';
  });

  function headerFor(url: string): string | null {
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    request.flush({});
    return request.request.headers.get('Accept-Language');
  }

  it('sends the language of <html lang> to the API', () => {
    document.documentElement.lang = 'ar';

    expect(headerFor('/api/v1/platform/info')).toBe('ar');
  });

  it('falls back to the default language when <html lang> is empty', () => {
    document.documentElement.lang = '';

    expect(headerFor('/api/v1/me/modules')).toBe('fr');
  });

  it.each(['/i18n/fr.json', 'https://cdn.example.com/api/x', '/apix/y', 'api/v1/relative'])(
    'adds no Accept-Language to a request outside /api/ (%s)',
    (url) => {
      document.documentElement.lang = 'ar';

      expect(headerFor(url)).toBeNull();
    },
  );
});
