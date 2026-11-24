import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { Home } from './home';

describe('Home', () => {
  it('isolates version, locale and direction left-to-right for the Arabic layout', async () => {
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
      ],
    });
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/platform/info')
      .flush({
        name: 'AgencySuite',
        version: '0.1.0-SNAPSHOT',
        supportedLocales: ['fr', 'ar', 'en'],
        locale: 'ar',
        direction: 'rtl',
        welcome: 'مرحبًا',
      });
    await fixture.whenStable();
    fixture.detectChanges();

    const isolated = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('dd > bdi[dir="ltr"]'),
    ).map((b) => b.textContent);
    expect(isolated).toEqual(['0.1.0-SNAPSHOT', 'ar', 'rtl']);
  });
});
