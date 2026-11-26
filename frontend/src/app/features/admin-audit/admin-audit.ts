import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import {
  AUDIT_PAGE_SIZE,
  AuditApi,
  AuditEvent,
  AuditEventsResponse,
  isAuditAction,
} from '../../core/api/audit-api';
import { SYSTEM_ROLES, SystemRole, UsersApi, isSystemRole } from '../../core/api/users-api';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { LanguageService } from '../../core/i18n/language.service';
import { DATE_TIME_FORMAT } from '../../core/i18n/locale-data';
import { MODULE_CODES, ModuleCode, isModuleCode } from '../../core/modules/module-code';

type LoadState = 'loading' | 'ready' | 'error';

/** Auteur : nom connu (isole dans un <bdi>) ou libelle traduit. Jamais d'identifiant. */
type Actor = { readonly name: string } | { readonly key: string };

/** Objet de l'action : module (nom traduit) ou utilisateur (nom connu). */
type Target = { readonly module: ModuleCode } | { readonly name: string };

interface Row {
  readonly id: number;
  readonly occurredAt: string;
  readonly actor: Actor;
  readonly actionKey: string;
  readonly target: Target | null;
  readonly modules: readonly ModuleCode[];
  readonly roles: readonly SystemRole[];
}

const ACTOR_KEYS: Readonly<Record<string, string>> = {
  AGENCY_USER: 'audit.actor.agencyUser',
  PLATFORM_USER: 'audit.actor.platform',
  SYSTEM: 'audit.actor.system',
};

/**
 * Ecran « Journal d'activite » (permission audit.read) : 50 entrees par page, de la plus
 * recente a la plus ancienne. Seules les valeurs connues sont affichees, traduites (actions,
 * modules, roles, noms d'utilisateurs) ; ni identifiant technique ni JSON brut.
 */
@Component({
  selector: 'app-admin-audit',
  imports: [TranslatePipe, DatePipe],
  templateUrl: './admin-audit.html',
  styleUrls: ['../admin-page.scss', './admin-audit.scss'],
})
export class AdminAudit {
  private readonly api = inject(AuditApi);
  private readonly usersApi = inject(UsersApi);
  private readonly currentUser = inject(CurrentUserService);
  private readonly language = inject(LanguageService);

  protected readonly dateFormat = DATE_TIME_FORMAT;
  protected readonly lang = computed(() => this.language.current().code);

  protected readonly state = signal<LoadState>('loading');
  protected readonly loading = signal(false);
  private readonly result = signal<AuditEventsResponse | null>(null);
  /** Noms des utilisateurs de l'agence, si l'ecran a la permission user.manage. */
  private readonly names = signal<ReadonlyMap<string, string>>(new Map());

  protected readonly page = computed(() => this.result()?.page ?? 0);
  protected readonly totalPages = computed(() => this.result()?.totalPages ?? 0);
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());

  protected readonly rows = computed<Row[]>(() => {
    const names = this.names();
    return (this.result()?.items ?? []).map((event, index) => toRow(event, index, names));
  });

  constructor() {
    void this.loadNames();
    void this.load(0);
  }

  protected isName(value: Actor | Target): value is { readonly name: string } {
    return 'name' in value;
  }

  protected isModule(value: Target): value is { readonly module: ModuleCode } {
    return 'module' in value;
  }

  protected previous(): void {
    if (this.hasPrevious()) {
      void this.load(this.page() - 1);
    }
  }

  protected next(): void {
    if (this.hasNext()) {
      void this.load(this.page() + 1);
    }
  }

  protected retry(): void {
    void this.load(this.page());
  }

  private async load(page: number): Promise<void> {
    this.loading.set(true);
    try {
      this.result.set(await firstValueFrom(this.api.list(page, AUDIT_PAGE_SIZE)));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    } finally {
      this.loading.set(false);
    }
  }

  /** Facultatif : sans la permission ou en cas d'erreur, l'auteur reste « Utilisateur de l'agence ». */
  private async loadNames(): Promise<void> {
    if (!this.currentUser.has('user.manage')) {
      return;
    }
    try {
      const users = await firstValueFrom(this.usersApi.list());
      this.names.set(new Map(users.map((u) => [u.id, u.fullName])));
    } catch {
      /* noms indisponibles : libelle generique */
    }
  }
}

function toRow(event: AuditEvent, index: number, names: ReadonlyMap<string, string>): Row {
  const details = event.details ?? {};
  const target = targetOf(event, names);
  const modules = codes([details['modules'], details['module']], isModuleCode).filter(
    (code) => !(target && 'module' in target && target.module === code),
  );
  return {
    id: index,
    occurredAt: event.occurredAt,
    actor: actorOf(event, names),
    actionKey: isAuditAction(event.action) ? `audit.action.${event.action}` : 'audit.otherAction',
    target,
    // Ordre du catalogue, comme dans le menu.
    modules: MODULE_CODES.filter((code) => modules.includes(code)),
    roles: SYSTEM_ROLES.filter((role) =>
      codes([details['roles'], details['role']], isSystemRole).includes(role),
    ),
  };
}

function actorOf(event: AuditEvent, names: ReadonlyMap<string, string>): Actor {
  if (event.actorType === 'AGENCY_USER' && event.actorId) {
    const name = names.get(event.actorId);
    if (name) {
      return { name };
    }
  }
  return { key: ACTOR_KEYS[event.actorType] ?? 'audit.actor.unknown' };
}

function targetOf(event: AuditEvent, names: ReadonlyMap<string, string>): Target | null {
  if (event.entityType === 'MODULE' && isModuleCode(event.entityId)) {
    return { module: event.entityId };
  }
  if (event.entityType === 'USER' && event.entityId) {
    const name = names.get(event.entityId);
    return name ? { name } : null;
  }
  return null;
}

/** Codes valides parmi des valeurs isolees ou des tableaux ; le reste est ignore. */
function codes<T extends string>(values: unknown[], valid: (v: unknown) => v is T): T[] {
  return values.flatMap((v) => (Array.isArray(v) ? v : [v])).filter(valid);
}
