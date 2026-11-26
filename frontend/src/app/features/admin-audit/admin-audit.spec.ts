import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import ar from '../../../../public/i18n/ar.json';
import fr from '../../../../public/i18n/fr.json';
import { AuditEvent, AuditEventsResponse } from '../../core/api/audit-api';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { LanguageService } from '../../core/i18n/language.service';
import { registerAppLocales } from '../../core/i18n/locale-data';
import { AdminAudit } from './admin-audit';

const AUDIT_URL = '/api/v1/audit-events';
const USERS_URL = '/api/v1/users';
const MANAGER_ID = '0190a1b2-0000-7000-8000-000000000001';
const SALES_ID = '0190a1b2-0000-7000-8000-000000000011';
const UNKNOWN_ID = '0190a1b2-0000-7000-8000-0000000000ff';
const PLATFORM_ID = '0190a1b2-0000-7000-8000-0000000000aa';
const AGENCY_ID = '0190a1b2-0000-7000-8000-000000000002';

const USERS = [
  {
    id: MANAGER_ID,
    email: 'z@example.test',
    fullName: 'Zineb Manager',
    status: 'ACTIVE',
    roles: ['MANAGER'],
    lastLoginAt: null,
  },
  {
    id: SALES_ID,
    email: 'a@example.test',
    fullName: 'Ali Sales',
    status: 'INVITED',
    roles: ['SALES'],
    lastLoginAt: null,
  },
];

function event(partial: Partial<AuditEvent>): AuditEvent {
  return {
    occurredAt: '2026-11-20T09:30:00Z',
    actorType: 'AGENCY_USER',
    actorId: MANAGER_ID,
    action: 'USER_LOGGED_IN',
    entityType: 'USER',
    entityId: MANAGER_ID,
    details: {},
    ...partial,
  };
}

const EVENTS: AuditEvent[] = [
  event({
    action: 'USER_INVITED',
    entityId: SALES_ID,
    details: { roles: ['SALES', 'OPERATIONS'] },
  }),
  event({ action: 'ROLE_REVOKED', entityId: SALES_ID, details: { role: 'OPERATIONS' } }),
  event({
    actorType: 'PLATFORM_USER',
    actorId: PLATFORM_ID,
    action: 'MODULE_ENABLED',
    entityType: 'MODULE',
    entityId: 'MICE',
    details: { module: 'MICE' },
  }),
  event({
    actorType: 'SYSTEM',
    actorId: null,
    action: 'AGENCY_CREATED',
    entityType: 'AGENCY',
    entityId: AGENCY_ID,
    details: { plan: 'ESSENTIAL', modules: ['TRAVEL', 'UNKNOWN', '<b>x</b>', 'VISA'] },
  }),
  event({
    actorId: UNKNOWN_ID,
    action: 'SOMETHING_NEW',
    entityId: UNKNOWN_ID,
    details: { secret: 'raw-json' },
  }),
];

function page(items: AuditEvent[], number = 0, totalPages = 1): AuditEventsResponse {
  return { items, page: number, size: 50, totalElements: items.length, totalPages };
}

describe('AdminAudit', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdminAudit>;
  let el: HTMLElement;
  let firstRequest: string;

  beforeAll(() => registerAppLocales());

  afterEach(() => {
    http.verify();
    document.documentElement.lang = 'fr';
    document.documentElement.dir = 'ltr';
  });

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function render(
    options: { permissions?: string[]; lang?: string; result?: AuditEventsResponse } = {},
  ) {
    const permissions = options.permissions ?? ['audit.read', 'user.manage'];
    TestBed.configureTestingModule({
      imports: [AdminAudit],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('ar', ar);
    TestBed.inject(LanguageService).use(options.lang ?? 'fr');
    http = TestBed.inject(HttpTestingController);
    const me = TestBed.inject(CurrentUserService).load();
    http.expectOne('/api/v1/me').flush({
      id: MANAGER_ID,
      email: 'z@example.test',
      fullName: 'Zineb Manager',
      agency: { id: AGENCY_ID, name: 'Agence Test' },
      roles: [],
      permissions,
    });
    await me;

    fixture = TestBed.createComponent(AdminAudit);
    el = fixture.nativeElement as HTMLElement;
    if (permissions.includes('user.manage')) {
      http.expectOne(USERS_URL).flush(USERS);
    } else {
      http.expectNone(USERS_URL);
    }
    const req = http.expectOne((r) => r.url === AUDIT_URL);
    firstRequest = req.request.urlWithParams;
    req.flush(options.result ?? page(EVENTS));
    await settle();
  }

  function cells(column: string): string[] {
    return Array.from(el.querySelectorAll(`table.audit tbody td.${column}`)).map(
      (td) => td.textContent?.replace(/\s+/g, ' ').trim() ?? '',
    );
  }

  it('requests the first page of 50 entries', async () => {
    await render();

    expect(firstRequest).toBe(`${AUDIT_URL}?page=0&size=50`);
    expect(el.querySelectorAll('table.audit tbody tr')).toHaveLength(5);
  });

  it('translates known actions and shows "Autre action" for an unknown one', async () => {
    await render();

    expect(cells('action')).toEqual([
      'Utilisateur invité',
      'Rôle retiré',
      'Module activé',
      'Agence créée',
      'Autre action',
    ]);
    expect(el.textContent).not.toContain('SOMETHING_NEW');
  });

  it('names the authors without any technical identifier', async () => {
    await render();

    expect(cells('actor')).toEqual([
      'Zineb Manager',
      'Zineb Manager',
      'Équipe AgencySuite',
      'Système',
      "Utilisateur de l'agence",
    ]);
    expect(el.querySelector('td.actor bdi')?.textContent).toBe('Zineb Manager');
    for (const id of [MANAGER_ID, SALES_ID, UNKNOWN_ID, PLATFORM_ID, AGENCY_ID]) {
      expect(el.textContent).not.toContain(id);
    }
  });

  it('uses the generic author label without the user.manage permission', async () => {
    await render({ permissions: ['audit.read'] });

    expect(cells('actor')[0]).toBe("Utilisateur de l'agence");
    expect(cells('target')[0]).toBe('');
  });

  it('shows translated modules, roles and the target user, never raw JSON', async () => {
    await render();

    expect(cells('target')).toEqual(['Ali Sales', 'Ali Sales', 'MICE', '', '']);
    expect(cells('details')[0]).toBe('Rôles : Commercial, Opérations');
    expect(cells('details')[1]).toBe('Rôles : Opérations');
    // Le module est deja l'objet de l'action : pas de doublon dans les details.
    expect(cells('details')[2]).toBe('');
    expect(cells('details')[3]).toBe('Modules : Voyages, Visas');
    expect(cells('details')[4]).toBe('');
    expect(el.textContent).not.toContain('ESSENTIAL');
    expect(el.textContent).not.toContain('raw-json');
    expect(el.textContent).not.toContain('UNKNOWN');
    expect(el.querySelector('table.audit b')).toBeNull();
  });

  it('pages forward with page=1 and back with page=0', async () => {
    await render({ result: page(EVENTS, 0, 3) });

    expect(el.querySelector('.page-number bdi')?.textContent).toBe('1 / 3');
    expect(el.querySelector<HTMLButtonElement>('button.previous')?.disabled).toBe(true);

    el.querySelector<HTMLButtonElement>('button.next')!.click();
    const next = http.expectOne((r) => r.url === AUDIT_URL);
    expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('size')).toBe('50');
    next.flush(page([event({})], 1, 3));
    await settle();

    expect(el.querySelector('.page-number bdi')?.textContent).toBe('2 / 3');
    el.querySelector<HTMLButtonElement>('button.previous')!.click();
    http.expectOne(`${AUDIT_URL}?page=0&size=50`).flush(page(EVENTS, 0, 3));
    await settle();
  });

  it('disables "next" on the last page and hides the pagination for a single page', async () => {
    await render({ result: page(EVENTS, 2, 3) });
    expect(el.querySelector<HTMLButtonElement>('button.next')?.disabled).toBe(true);
    TestBed.resetTestingModule();

    await render({ result: page(EVENTS, 0, 1) });
    expect(el.querySelector('.pagination')).toBeNull();
  });

  it('shows an empty state', async () => {
    await render({ result: page([], 0, 0) });

    expect(el.querySelector('.empty')?.textContent?.trim()).toBe(fr.admin.audit.empty);
  });

  it('shows a clear message when the log cannot be loaded', async () => {
    TestBed.configureTestingModule({
      imports: [AdminAudit],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
      ],
    });
    TestBed.inject(TranslateService).setTranslation('fr', fr);
    TestBed.inject(TranslateService).use('fr');
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminAudit);
    el = fixture.nativeElement as HTMLElement;
    http.expectOne((r) => r.url === AUDIT_URL).flush('boom', { status: 500, statusText: 'x' });
    await settle();

    expect(el.querySelector('.load-error span')?.textContent?.trim()).toBe(
      fr.admin.audit.loadError,
    );
  });

  it('is right-to-left in Arabic with translated labels and isolated names', async () => {
    await render({ lang: 'ar' });

    expect(document.documentElement.dir).toBe('rtl');
    expect(cells('action')[0]).toBe(ar.audit.action.USER_INVITED);
    expect(cells('actor')[2]).toBe(ar.audit.actor.platform);
    expect(el.querySelector('td.actor bdi')?.textContent).toBe('Zineb Manager');
    expect(el.querySelector('td.date bdi')?.textContent?.trim()).not.toBe('');
  });
});
