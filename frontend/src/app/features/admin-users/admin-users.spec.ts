import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import ar from '../../../../public/i18n/ar.json';
import en from '../../../../public/i18n/en.json';
import fr from '../../../../public/i18n/fr.json';
import { UserItem } from '../../core/api/users-api';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { LanguageService } from '../../core/i18n/language.service';
import { ShellNotice } from '../../core/ui/shell-notice.service';
import { registerAppLocales } from '../../core/i18n/locale-data';
import { AdminUsers } from './admin-users';

const USERS_URL = '/api/v1/users';
const ME_ID = '0190a1b2-0000-7000-8000-000000000001';
const SALES_ID = '0190a1b2-0000-7000-8000-000000000011';
const OPS_ID = '0190a1b2-0000-7000-8000-000000000012';
const CONFLICT = { status: 409, statusText: 'Conflict' };

const PROFILE = {
  id: ME_ID,
  email: 'zineb.manager@example.test',
  fullName: 'Zineb Manager',
  agency: { id: '0190a1b2-0000-7000-8000-000000000002', name: 'Agence Test' },
  roles: ['MANAGER'],
  permissions: ['user.manage', 'audit.read'],
};

const USERS: UserItem[] = [
  {
    id: ME_ID,
    email: 'zineb.manager@example.test',
    fullName: 'Zineb Manager',
    status: 'ACTIVE',
    roles: ['MANAGER'],
    lastLoginAt: '2026-11-20T09:30:00Z',
  },
  {
    id: SALES_ID,
    email: 'ali.sales@example.test',
    fullName: 'Ali Sales',
    status: 'INVITED',
    roles: ['SALES'],
    lastLoginAt: null,
  },
  {
    id: OPS_ID,
    email: 'brahim.ops@example.test',
    fullName: 'Brahim Ops',
    status: 'DISABLED',
    roles: ['ACCOUNTANT', 'OPERATIONS'],
    lastLoginAt: '2026-10-01T08:00:00Z',
  },
];

function problem(code: string, detail: string) {
  return { type: `urn:agencysuite:problem:${code}`, title: 'x', status: 409, detail };
}

describe('AdminUsers', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdminUsers>;
  let el: HTMLElement;

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

  async function render(lang = 'fr', users: UserItem[] = USERS): Promise<void> {
    TestBed.configureTestingModule({
      imports: [AdminUsers],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'fr' }),
        provideRouter([]),
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('ar', ar);
    translate.setTranslation('en', en);
    TestBed.inject(LanguageService).use(lang);
    http = TestBed.inject(HttpTestingController);
    const me = TestBed.inject(CurrentUserService).load();
    http.expectOne('/api/v1/me').flush(PROFILE);
    await me;

    fixture = TestBed.createComponent(AdminUsers);
    el = fixture.nativeElement as HTMLElement;
    http.expectOne(USERS_URL).flush(users);
    await settle();
  }

  /** La liste est rechargee apres chaque action reussie. */
  async function expectReload(): Promise<void> {
    const req = http.expectOne((r) => r.url === USERS_URL && r.method === 'GET');
    req.flush(USERS);
    await settle();
  }

  function rows(): HTMLTableRowElement[] {
    return Array.from(el.querySelectorAll<HTMLTableRowElement>('table.users tbody tr'));
  }

  function row(name: string): HTMLTableRowElement {
    const found = rows().find((r) => r.querySelector('.name bdi')?.textContent?.trim() === name);
    if (!found) {
      throw new Error(`row ${name} not found`);
    }
    return found;
  }

  function click(selector: string, root: ParentNode = el): void {
    const button = root.querySelector<HTMLButtonElement>(selector);
    if (!button) {
      throw new Error(`${selector} not found`);
    }
    button.click();
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  async function fillInvite(email = 'new.user@example.test', roles = ['SALES']): Promise<void> {
    click('button.invite');
    await settle();
    type('.invite-form input[name="email"]', email);
    type('.invite-form input[name="firstName"]', 'New');
    type('.invite-form input[name="lastName"]', 'User');
    for (const role of roles) {
      el.querySelector<HTMLInputElement>(`.invite-form input[data-role="${role}"]`)!.click();
    }
    await settle();
  }

  async function submit(form: string): Promise<void> {
    el.querySelector<HTMLFormElement>(form)!.dispatchEvent(new Event('submit'));
    await settle();
  }

  function noticeText(): string | undefined {
    return el.querySelector('.notice .notice-text')?.textContent?.replace(/\s+/g, ' ').trim();
  }

  describe('list', () => {
    it('shows the users sorted by name with translated roles, statuses and last login', async () => {
      await render();

      expect(rows().map((r) => r.querySelector('.name bdi')?.textContent?.trim())).toEqual([
        'Ali Sales',
        'Brahim Ops',
        'Zineb Manager',
      ]);
      expect(row('Ali Sales').querySelector('.email bdi')?.textContent).toBe(
        'ali.sales@example.test',
      );
      expect(row('Ali Sales').querySelector('.badge')?.textContent?.trim()).toBe('Invité');
      expect(row('Brahim Ops').querySelector('.badge')?.textContent?.trim()).toBe('Désactivé');
      expect(row('Zineb Manager').querySelector('.badge')?.textContent?.trim()).toBe('Actif');
      // Ordre des roles du catalogue, libelles traduits.
      expect(
        row('Brahim Ops').querySelector('.roles-cell')?.textContent?.replace(/\s+/g, ' ').trim(),
      ).toBe('Opérations, Comptable');
      expect(row('Ali Sales').querySelector('.last-login')?.textContent?.trim()).toBe('Jamais');
      expect(row('Zineb Manager').querySelector('.last-login bdi')?.textContent).toContain('2026');
    });

    it('marks the current user "(vous)" without a disable button', async () => {
      await render();

      const me = row('Zineb Manager');
      expect(me.querySelector('.me')?.textContent?.trim()).toBe('(vous)');
      expect(me.querySelector('button.disable')).toBeNull();
      expect(row('Ali Sales').querySelector('.me')).toBeNull();
      expect(row('Ali Sales').querySelector('button.disable')).not.toBeNull();
    });

    it('offers resend only for INVITED users and enable only for DISABLED users', async () => {
      await render();

      expect(row('Ali Sales').querySelector('button.resend')).not.toBeNull();
      expect(row('Zineb Manager').querySelector('button.resend')).toBeNull();
      expect(row('Brahim Ops').querySelector('button.resend')).toBeNull();
      expect(row('Brahim Ops').querySelector('button.enable')).not.toBeNull();
      expect(row('Brahim Ops').querySelector('button.disable')).toBeNull();
    });

    it('shows a clear message when the list cannot be loaded, and retries', async () => {
      TestBed.configureTestingModule({
        imports: [AdminUsers],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideTranslateService({ fallbackLang: 'fr' }),
        ],
      });
      TestBed.inject(TranslateService).setTranslation('fr', fr);
      TestBed.inject(TranslateService).use('fr');
      http = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(AdminUsers);
      el = fixture.nativeElement as HTMLElement;
      http.expectOne(USERS_URL).flush({ type: 'about:blank' }, { status: 500, statusText: 'x' });
      await settle();

      expect(el.querySelector('.load-error span')?.textContent?.trim()).toBe(
        fr.admin.users.loadError,
      );
      expect(el.querySelector('table')).toBeNull();

      click('.load-error button.retry');
      await expectReload();
      expect(rows()).toHaveLength(3);
    });
  });

  describe('invitation', () => {
    it('sends the exact POST body and shows the success message with the email isolated', async () => {
      await render();
      await fillInvite('  new.user@example.test ', ['SALES', 'OPERATIONS']);

      await submit('form.invite-form');
      const req = http.expectOne((r) => r.url === USERS_URL && r.method === 'POST');
      expect(req.request.body).toEqual({
        email: 'new.user@example.test',
        firstName: 'New',
        lastName: 'User',
        roles: ['SALES', 'OPERATIONS'],
      });
      req.flush(
        { id: 'new-id', status: 'INVITED', invitationSent: true },
        { status: 201, statusText: 'Created' },
      );
      await settle();
      await expectReload();

      expect(el.querySelector('.notice-success')).not.toBeNull();
      expect(noticeText()).toBe('Invitation envoyée à new.user@example.test.');
      expect(el.querySelector('.notice bdi.msg-value')?.textContent).toBe('new.user@example.test');
      expect(el.querySelector('form.invite-form')).toBeNull();
    });

    it('warns when the email did not go out, with a resend button', async () => {
      await render();
      await fillInvite();
      await submit('form.invite-form');
      http
        .expectOne((r) => r.url === USERS_URL && r.method === 'POST')
        .flush(
          { id: 'new-id', status: 'INVITED', invitationSent: false },
          { status: 201, statusText: 'Created' },
        );
      await settle();
      await expectReload();

      expect(el.querySelector('.notice-warning')).not.toBeNull();
      expect(noticeText()).toBe(
        "L'utilisateur new.user@example.test est créé, mais l'e-mail d'invitation n'est pas parti.",
      );
      const action = el.querySelector<HTMLButtonElement>('.notice button.notice-action')!;
      expect(action.textContent?.trim()).toBe(fr.admin.users.actions.resend);

      action.click();
      const resend = http.expectOne(`${USERS_URL}/new-id/invitation`);
      expect(resend.request.method).toBe('POST');
      resend.flush(null, { status: 202, statusText: 'Accepted' });
      await settle();
      await expectReload();
      expect(noticeText()).toBe('Invitation renvoyée à new.user@example.test.');
    });

    it.each([
      ['email-already-registered', CONFLICT],
      ['user-quota-exceeded', CONFLICT],
      ['identity-admin-unavailable', { status: 503, statusText: 'Service Unavailable' }],
    ])('shows the server detail of %s in the form and keeps it filled', async (code, status) => {
      await render();
      await fillInvite();
      await submit('form.invite-form');
      http
        .expectOne((r) => r.url === USERS_URL && r.method === 'POST')
        .flush(problem(code, `Détail serveur ${code}`), status);
      await settle();

      expect(el.querySelector('.invite-form .form-error')?.textContent?.trim()).toBe(
        `Détail serveur ${code}`,
      );
      expect(el.querySelector<HTMLInputElement>('.invite-form input[name="email"]')?.value).toBe(
        'new.user@example.test',
      );
      expect(
        el.querySelector<HTMLInputElement>('.invite-form input[data-role="SALES"]')?.checked,
      ).toBe(true);
      expect(el.querySelector('.notice')).toBeNull();
    });

    it('shows a generic message, never the raw error, for an unexpected failure', async () => {
      await render();
      await fillInvite();
      await submit('form.invite-form');
      http
        .expectOne((r) => r.url === USERS_URL && r.method === 'POST')
        .flush({ detail: 'org.postgresql.util.PSQLException' }, { status: 500, statusText: 'x' });
      await settle();

      expect(el.querySelector('.invite-form .form-error')?.textContent?.trim()).toBe(
        fr.common.genericError,
      );
      expect(el.textContent).not.toContain('PSQL');
    });

    it('validates email, 80-character names and at least one role before sending', async () => {
      await render();
      await fillInvite('not-an-email', []);
      type('.invite-form input[name="firstName"]', 'x'.repeat(81));
      await submit('form.invite-form');

      http.expectNone((r) => r.method === 'POST');
      expect(el.querySelector('[data-error="email"]')?.textContent?.trim()).toBe(
        fr.admin.users.form.errors.email,
      );
      expect(el.querySelector('[data-error="firstName"]')?.textContent?.trim()).toBe(
        '80 caractères au plus.',
      );
      expect(el.querySelector('[data-error="roles"]')?.textContent?.trim()).toBe(
        fr.admin.users.form.errors.noRole,
      );

      type('.invite-form input[name="email"]', `${'a'.repeat(250)}@example.test`);
      await settle();
      expect(el.querySelector('[data-error="email"]')?.textContent?.trim()).toBe(
        '254 caractères au plus.',
      );

      type('.invite-form input[name="lastName"]', '   ');
      await settle();
      expect(el.querySelector('[data-error="lastName"]')?.textContent?.trim()).toBe(
        fr.admin.users.form.errors.required,
      );
    });

    it('accepts names of exactly 80 characters', async () => {
      await render();
      await fillInvite();
      type('.invite-form input[name="firstName"]', 'x'.repeat(80));
      await submit('form.invite-form');

      const req = http.expectOne((r) => r.url === USERS_URL && r.method === 'POST');
      expect(req.request.body.firstName).toHaveLength(80);
      req.flush(problem('user-quota-exceeded', 'Palier'), CONFLICT);
      await settle();
    });

    it('shows each role with its translated description', async () => {
      await render();
      click('button.invite');
      await settle();

      const options = Array.from(el.querySelectorAll('.invite-form .role-option'));
      expect(options.map((o) => o.querySelector('.role-name')?.textContent?.trim())).toEqual([
        'Gérant',
        'Commercial',
        'Opérations',
        'Comptable',
      ]);
      expect(options[1].querySelector('.role-description')?.textContent?.trim()).toBe(
        fr.role.SALES.description,
      );
    });
  });

  describe('roles', () => {
    it('prefills the checkboxes and sends PUT with the new roles', async () => {
      await render();
      click('button.edit-roles', row('Ali Sales'));
      await settle();

      const box = (role: string) =>
        el.querySelector<HTMLInputElement>(`.roles-form input[data-role="${role}"]`)!;
      expect(box('SALES').checked).toBe(true);
      expect(box('OPERATIONS').checked).toBe(false);

      box('OPERATIONS').click();
      await submit('form.roles-form');
      const req = http.expectOne(`${USERS_URL}/${SALES_ID}/roles`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ roles: ['SALES', 'OPERATIONS'] });
      req.flush({ ...USERS[1], roles: ['OPERATIONS', 'SALES'] });
      await settle();
      await expectReload();

      expect(noticeText()).toBe('Rôles de Ali Sales enregistrés.');
      expect(el.querySelector('form.roles-form')).toBeNull();
    });

    it('asks for confirmation before removing my own manager role, then shows last-manager', async () => {
      await render();
      click('button.edit-roles', row('Zineb Manager'));
      await settle();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="MANAGER"]')!.click();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="SALES"]')!.click();
      await submit('form.roles-form');

      http.expectNone(`${USERS_URL}/${ME_ID}/roles`);
      const dialog = el.querySelector('app-confirm-dialog [role="alertdialog"]');
      expect(dialog?.querySelector('.message')?.textContent?.trim()).toBe(
        'Vous ne pourrez plus gérer les utilisateurs.',
      );

      click('app-confirm-dialog button.confirm');
      await settle();
      const req = http.expectOne(`${USERS_URL}/${ME_ID}/roles`);
      expect(req.request.body).toEqual({ roles: ['SALES'] });
      req.flush(problem('last-manager', 'L’agence doit garder au moins un gérant.'), CONFLICT);
      await settle();

      expect(el.querySelector('app-confirm-dialog')).toBeNull();
      expect(el.querySelector('.roles-form .form-error')?.textContent?.trim()).toBe(
        'L’agence doit garder au moins un gérant.',
      );
    });

    it('does not send anything when the confirmation is cancelled', async () => {
      await render();
      click('button.edit-roles', row('Zineb Manager'));
      await settle();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="MANAGER"]')!.click();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="SALES"]')!.click();
      await submit('form.roles-form');

      click('app-confirm-dialog button.cancel');
      await settle();

      http.expectNone(`${USERS_URL}/${ME_ID}/roles`);
      expect(el.querySelector('app-confirm-dialog')).toBeNull();
    });

    it('requires at least one role', async () => {
      await render();
      click('button.edit-roles', row('Ali Sales'));
      await settle();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="SALES"]')!.click();
      await submit('form.roles-form');

      http.expectNone(`${USERS_URL}/${SALES_ID}/roles`);
      expect(el.querySelector('.roles-form [data-error="roles"]')).not.toBeNull();
    });

    /** Change ses propres roles (ajout ou retrait de SALES, MANAGER retire si `dropManager`). */
    async function changeOwnRoles(dropManager: boolean): Promise<void> {
      click('button.edit-roles', row('Zineb Manager'));
      await settle();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="SALES"]')!.click();
      if (dropManager) {
        el.querySelector<HTMLInputElement>('.roles-form input[data-role="MANAGER"]')!.click();
      }
      await submit('form.roles-form');
      if (dropManager) {
        click('app-confirm-dialog button.confirm');
        await settle();
      }
      http
        .expectOne(`${USERS_URL}/${ME_ID}/roles`)
        .flush({ ...USERS[0], roles: dropManager ? ['SALES'] : ['MANAGER', 'SALES'] });
      await settle();
    }

    it('reloads my profile after changing my own roles and stays when I keep user.manage', async () => {
      await render();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      await changeOwnRoles(false);

      http.expectOne('/api/v1/me').flush({ ...PROFILE, roles: ['MANAGER', 'SALES'] });
      await settle();
      await expectReload();

      expect(TestBed.inject(CurrentUserService).profile()?.roles).toEqual(['MANAGER', 'SALES']);
      expect(navigate).not.toHaveBeenCalled();
      expect(TestBed.inject(ShellNotice).key()).toBeNull();
      expect(noticeText()).toBe('Rôles de Zineb Manager enregistrés.');
    });

    it('goes home with "Vos rôles ont changé" when I no longer have user.manage', async () => {
      await render();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
      await changeOwnRoles(true);

      http
        .expectOne('/api/v1/me')
        .flush({ ...PROFILE, roles: ['SALES'], permissions: ['identity.read'] });
      await settle();

      const current = TestBed.inject(CurrentUserService);
      expect(current.has('user.manage')).toBe(false);
      expect(navigate).toHaveBeenCalledWith('/');
      expect(TestBed.inject(ShellNotice).key()).toBe('admin.users.ownRolesChanged');
      expect(fr.admin.users.ownRolesChanged).toBe('Vos rôles ont changé.');
      // Plus de droit sur la liste : elle n'est pas rechargee (pas de 403 inutile).
      http.expectNone(USERS_URL);
    });

    it('does not reload my profile when the roles of someone else change', async () => {
      await render();
      click('button.edit-roles', row('Ali Sales'));
      await settle();
      el.querySelector<HTMLInputElement>('.roles-form input[data-role="ACCOUNTANT"]')!.click();
      await submit('form.roles-form');
      http.expectOne(`${USERS_URL}/${SALES_ID}/roles`).flush(USERS[1]);
      await settle();

      http.expectNone('/api/v1/me');
      await expectReload();
    });
  });

  describe('status', () => {
    it('disables only after confirmation, then reloads the list', async () => {
      await render();
      click('button.disable', row('Ali Sales'));
      await settle();

      http.expectNone(`${USERS_URL}/${SALES_ID}/disable`);
      expect(el.querySelector('app-confirm-dialog .subject bdi')?.textContent).toBe('Ali Sales');

      click('app-confirm-dialog button.confirm');
      await settle();
      const req = http.expectOne(`${USERS_URL}/${SALES_ID}/disable`);
      expect(req.request.method).toBe('POST');
      req.flush({ identityProviderSynced: true });
      await settle();
      await expectReload();

      expect(noticeText()).toBe('Ali Sales est désactivé.');
    });

    it('warns when the identity provider was not synced, and retries the same action', async () => {
      await render();
      click('button.disable', row('Ali Sales'));
      await settle();
      click('app-confirm-dialog button.confirm');
      await settle();
      http.expectOne(`${USERS_URL}/${SALES_ID}/disable`).flush({ identityProviderSynced: false });
      await settle();
      await expectReload();

      expect(el.querySelector('.notice-warning')).not.toBeNull();
      expect(noticeText()).toBe(
        'Action enregistrée pour Ali Sales ; la synchronisation avec le service de connexion a échoué, réessayez.',
      );

      click('.notice button.notice-action');
      await settle();
      http.expectOne(`${USERS_URL}/${SALES_ID}/disable`).flush({ identityProviderSynced: true });
      await settle();
      await expectReload();
      expect(el.querySelector('.notice-success')).not.toBeNull();
    });

    it('enables without confirmation and shows the quota detail on 409', async () => {
      await render();
      click('button.enable', row('Brahim Ops'));
      await settle();
      http
        .expectOne(`${USERS_URL}/${OPS_ID}/enable`)
        .flush(
          problem('user-quota-exceeded', 'Le palier ne permet pas d’utilisateur supplémentaire.'),
          CONFLICT,
        );
      await settle();

      expect(el.querySelector('.notice-error')).not.toBeNull();
      expect(noticeText()).toBe('Le palier ne permet pas d’utilisateur supplémentaire.');
    });

    it('resends an invitation from the row', async () => {
      await render();
      click('button.resend', row('Ali Sales'));
      await settle();
      http
        .expectOne(`${USERS_URL}/${SALES_ID}/invitation`)
        .flush(null, { status: 202, statusText: 'Accepted' });
      await settle();
      await expectReload();

      expect(noticeText()).toBe('Invitation renvoyée à ali.sales@example.test.');
    });
  });

  describe('Arabic', () => {
    it('is right-to-left with emails and names isolated in <bdi>', async () => {
      await render('ar');

      expect(document.documentElement.dir).toBe('rtl');
      expect(el.querySelector('h1')?.textContent?.trim()).toBe(ar.admin.users.title);
      const ali = row('Ali Sales');
      expect(ali.querySelector('.email bdi')?.textContent).toBe('ali.sales@example.test');
      expect(ali.querySelector('.badge')?.textContent?.trim()).toBe(ar.admin.users.status.INVITED);
      expect(ali.querySelector('.last-login')?.textContent?.trim()).toBe(ar.admin.users.never);
      expect(row('Zineb Manager').querySelector('.me')?.textContent?.trim()).toBe('(أنت)');
    });

    it('isolates the email in the translated success message', async () => {
      await render('ar');
      await fillInvite();
      await submit('form.invite-form');
      http
        .expectOne((r) => r.url === USERS_URL && r.method === 'POST')
        .flush(
          { id: 'new-id', status: 'INVITED', invitationSent: true },
          { status: 201, statusText: 'Created' },
        );
      await settle();
      await expectReload();

      expect(el.querySelector('.notice bdi.msg-value')?.textContent).toBe('new.user@example.test');
      expect(noticeText()).toBe('تم إرسال الدعوة إلى new.user@example.test.');
    });
  });
});
