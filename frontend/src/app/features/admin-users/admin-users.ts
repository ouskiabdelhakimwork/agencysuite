import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Observable, firstValueFrom } from 'rxjs';
import {
  StatusChangeResponse,
  SYSTEM_ROLES,
  SystemRole,
  USER_STATUSES,
  UserItem,
  UsersApi,
} from '../../core/api/users-api';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { problemDetail } from '../../core/http/problem';
import { LanguageService } from '../../core/i18n/language.service';
import { DATE_TIME_FORMAT } from '../../core/i18n/locale-data';
import { ConfirmDialog } from '../../core/ui/confirm-dialog';
import { ShellNotice } from '../../core/ui/shell-notice.service';

const USER_MANAGE = 'user.manage';

/** Limites alignees sur InviteUserRequest (UsersController). */
export const EMAIL_MAX_LENGTH = 254;
export const NAME_MAX_LENGTH = 80;

type LoadState = 'loading' | 'ready' | 'error';

/** Message : cle de traduction (parametre `user` isole dans un <bdi>) ou `detail` du serveur. */
interface Message {
  readonly key?: string;
  readonly detail?: string;
  readonly user?: string;
}

interface Notice extends Message {
  readonly kind: 'success' | 'warning' | 'error';
  readonly action?: { readonly key: string; readonly run: () => void };
}

type Panel = { readonly mode: 'invite' } | { readonly mode: 'roles'; readonly user: UserItem };

interface Confirmation {
  readonly titleKey: string;
  readonly messageKey: string;
  readonly confirmKey: string;
  readonly subject: string;
  readonly run: () => void;
}

type RolesGroup = FormGroup<Record<SystemRole, FormControl<boolean>>>;

const GENERIC_ERROR: Message = { key: 'common.genericError' };

/** Au moins une case cochee. */
const atLeastOneRole: ValidatorFn = (group: AbstractControl): ValidationErrors | null =>
  Object.values((group as RolesGroup).getRawValue()).some(Boolean) ? null : { noRole: true };

/** Equivalent de @NotBlank : des espaces seuls sont refuses. */
const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim() === '' && control.value !== ''
    ? { required: true }
    : null;

function errorMessage(error: unknown): Message {
  const detail = problemDetail(error);
  return detail ? { detail } : GENERIC_ERROR;
}

/**
 * Ecran « Utilisateurs » (permission user.manage) : liste, invitation, roles, renvoi
 * d'invitation, desactivation et reactivation. Les boutons masques sont du confort : le
 * serveur controle chaque action (@RequiresPermission, dernier gerant, soi-meme).
 */
@Component({
  selector: 'app-admin-users',
  imports: [ReactiveFormsModule, NgTemplateOutlet, TranslatePipe, DatePipe, ConfirmDialog],
  templateUrl: './admin-users.html',
  styleUrls: ['../admin-page.scss', './admin-users.scss'],
})
export class AdminUsers {
  private readonly api = inject(UsersApi);
  private readonly currentUser = inject(CurrentUserService);
  private readonly language = inject(LanguageService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly shellNotice = inject(ShellNotice);

  protected readonly roles = SYSTEM_ROLES;
  protected readonly dateFormat = DATE_TIME_FORMAT;
  protected readonly emailMax = EMAIL_MAX_LENGTH;
  protected readonly nameMax = NAME_MAX_LENGTH;
  /** Marqueur (caractere a usage prive) remplace par la valeur isolee dans la phrase traduite. */
  protected readonly slot = '';

  protected readonly state = signal<LoadState>('loading');
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  private readonly users = signal<readonly UserItem[]>([]);
  protected readonly panel = signal<Panel | null>(null);
  protected readonly formError = signal<Message | null>(null);
  protected readonly notice = signal<Notice | null>(null);
  protected readonly confirmation = signal<Confirmation | null>(null);

  protected readonly lang = computed(() => this.language.current().code);
  private readonly meId = computed(() => this.currentUser.profile()?.id ?? null);

  /** Tri par nom, selon la langue courante. */
  protected readonly sortedUsers = computed(() => {
    const lang = this.lang();
    return [...this.users()].sort((a, b) => a.fullName.localeCompare(b.fullName, lang));
  });

  protected readonly inviteForm = this.fb.group({
    email: [
      '',
      [Validators.required, notBlank, Validators.email, Validators.maxLength(EMAIL_MAX_LENGTH)],
    ],
    firstName: ['', [Validators.required, notBlank, Validators.maxLength(NAME_MAX_LENGTH)]],
    lastName: ['', [Validators.required, notBlank, Validators.maxLength(NAME_MAX_LENGTH)]],
    roles: this.rolesGroup(),
  });

  protected readonly rolesForm = this.fb.group({ roles: this.rolesGroup() });

  constructor() {
    void this.reload();
  }

  protected isMe(user: UserItem): boolean {
    return user.id === this.meId();
  }

  protected statusKey(status: string): string {
    return (USER_STATUSES as readonly string[]).includes(status)
      ? `admin.users.status.${status}`
      : 'admin.users.status.unknown';
  }

  /** Roles connus seulement : un code inattendu n'est jamais affiche brut. */
  protected knownRoles(user: UserItem): SystemRole[] {
    return SYSTEM_ROLES.filter((role) => user.roles.includes(role));
  }

  protected roleControl(group: RolesGroup, role: SystemRole): FormControl<boolean> {
    return group.controls[role];
  }

  // --- Liste ---------------------------------------------------------------------------

  protected async reload(): Promise<void> {
    this.loading.set(true);
    try {
      this.users.set(await firstValueFrom(this.api.list()));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    } finally {
      this.loading.set(false);
    }
  }

  // --- Invitation ----------------------------------------------------------------------

  protected openInvite(): void {
    this.inviteForm.reset();
    this.formError.set(null);
    this.panel.set({ mode: 'invite' });
  }

  protected closePanel(): void {
    this.panel.set(null);
    this.formError.set(null);
  }

  protected async submitInvite(): Promise<void> {
    if (this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }
    const value = this.inviteForm.getRawValue();
    const email = value.email.trim();
    this.formError.set(null);
    this.busy.set(true);
    try {
      const result = await firstValueFrom(
        this.api.invite({
          email,
          firstName: value.firstName.trim(),
          lastName: value.lastName.trim(),
          roles: selectedRoles(value.roles),
        }),
      );
      this.panel.set(null);
      this.notice.set(
        result.invitationSent
          ? { kind: 'success', key: 'admin.users.invite.sent', user: email }
          : {
              kind: 'warning',
              key: 'admin.users.invite.notSent',
              user: email,
              action: {
                key: 'admin.users.actions.resend',
                run: () => this.resend(result.id, email),
              },
            },
      );
      void this.reload();
    } catch (error) {
      // Le formulaire reste rempli : l'utilisateur corrige et renvoie.
      this.formError.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  // --- Roles ---------------------------------------------------------------------------

  protected openRoles(user: UserItem): void {
    const roles = this.rolesForm.controls.roles;
    roles.reset();
    for (const role of SYSTEM_ROLES) {
      roles.controls[role].setValue(user.roles.includes(role));
    }
    this.formError.set(null);
    this.panel.set({ mode: 'roles', user });
  }

  protected submitRoles(user: UserItem): void {
    if (this.rolesForm.invalid) {
      this.rolesForm.markAllAsTouched();
      return;
    }
    const roles = selectedRoles(this.rolesForm.controls.roles.getRawValue());
    const dropsOwnManager =
      this.isMe(user) && user.roles.includes('MANAGER') && !roles.includes('MANAGER');
    if (dropsOwnManager) {
      this.confirmation.set({
        titleKey: 'admin.users.confirm.selfManager.title',
        messageKey: 'admin.users.confirm.selfManager.message',
        confirmKey: 'admin.users.confirm.selfManager.confirm',
        subject: user.fullName,
        run: () => void this.saveRoles(user, roles),
      });
      return;
    }
    void this.saveRoles(user, roles);
  }

  private async saveRoles(user: UserItem, roles: SystemRole[]): Promise<void> {
    this.formError.set(null);
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.changeRoles(user.id, roles));
      this.panel.set(null);
      if (this.isMe(user)) {
        // Ses propres roles : les permissions du profil (menu, gardes) doivent suivre.
        await this.currentUser.reload();
        if (!this.currentUser.has(USER_MANAGE)) {
          this.shellNotice.show('admin.users.ownRolesChanged');
          void this.router.navigateByUrl('/');
          return;
        }
      }
      this.notice.set({ kind: 'success', key: 'admin.users.roles.saved', user: user.fullName });
      void this.reload();
    } catch (error) {
      this.formError.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  // --- Renvoi d'invitation ---------------------------------------------------------------

  protected async resend(userId: string, email: string): Promise<void> {
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.resendInvitation(userId));
      this.notice.set({ kind: 'success', key: 'admin.users.resend.sent', user: email });
      void this.reload();
    } catch (error) {
      this.notice.set({ kind: 'error', ...errorMessage(error) });
    } finally {
      this.busy.set(false);
    }
  }

  // --- Desactivation et reactivation -----------------------------------------------------

  protected askDisable(user: UserItem): void {
    this.confirmation.set({
      titleKey: 'admin.users.confirm.disable.title',
      messageKey: 'admin.users.confirm.disable.message',
      confirmKey: 'admin.users.confirm.disable.confirm',
      subject: user.fullName,
      run: () => void this.changeStatus(user, 'disable'),
    });
  }

  protected enable(user: UserItem): void {
    void this.changeStatus(user, 'enable');
  }

  /** Une nouvelle tentative refait la meme action, idempotente cote serveur. */
  private async changeStatus(user: UserItem, action: 'disable' | 'enable'): Promise<void> {
    const call: Observable<StatusChangeResponse> =
      action === 'disable' ? this.api.disable(user.id) : this.api.enable(user.id);
    this.busy.set(true);
    try {
      const result = await firstValueFrom(call);
      this.notice.set(
        result.identityProviderSynced
          ? { kind: 'success', key: `admin.users.${action}.done`, user: user.fullName }
          : {
              kind: 'warning',
              key: 'admin.users.syncFailed',
              user: user.fullName,
              action: { key: 'common.retry', run: () => void this.changeStatus(user, action) },
            },
      );
      void this.reload();
    } catch (error) {
      this.notice.set({ kind: 'error', ...errorMessage(error) });
    } finally {
      this.busy.set(false);
    }
  }

  // --- Confirmation et messages ------------------------------------------------------------

  protected confirm(): void {
    const pending = this.confirmation();
    this.confirmation.set(null);
    pending?.run();
  }

  protected cancelConfirmation(): void {
    this.confirmation.set(null);
  }

  protected dismissNotice(): void {
    this.notice.set(null);
  }

  protected runNoticeAction(notice: Notice): void {
    this.notice.set(null);
    notice.action?.run();
  }

  private rolesGroup(): RolesGroup {
    const controls = Object.fromEntries(
      SYSTEM_ROLES.map((role) => [role, this.fb.control(false)]),
    ) as Record<SystemRole, FormControl<boolean>>;
    return new FormGroup(controls, { validators: atLeastOneRole });
  }
}

function selectedRoles(value: Partial<Record<string, boolean>>): SystemRole[] {
  return SYSTEM_ROLES.filter((role) => value[role] === true);
}
