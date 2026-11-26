import ar from '../../../../public/i18n/ar.json';
import en from '../../../../public/i18n/en.json';
import fr from '../../../../public/i18n/fr.json';
import { AUDIT_ACTIONS } from '../api/audit-api';
import { SYSTEM_ROLES } from '../api/users-api';
import { MODULE_CODES } from '../modules/module-code';

/** Toutes les cles feuilles d'un fichier de traduction, en notation pointee. */
function keys(node: unknown, prefix = ''): string[] {
  if (typeof node !== 'object' || node === null) {
    return [prefix];
  }
  return Object.entries(node).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k));
}

function valueAt(node: unknown, path: string): unknown {
  return path
    .split('.')
    .reduce<unknown>((n, k) => (n as Record<string, unknown> | undefined)?.[k], node);
}

const LANGUAGES = Object.entries({ fr, ar, en });

describe('i18n files', () => {
  it('fr, ar and en have exactly the same keys', () => {
    const reference = keys(fr).sort();
    expect(keys(ar).sort()).toEqual(reference);
    expect(keys(en).sort()).toEqual(reference);
  });

  it.each(LANGUAGES)('%s names the ten modules', (_lang, file) => {
    for (const code of MODULE_CODES) {
      const name = valueAt(file, `module.${code}.name`);
      expect(typeof name, `module.${code}.name`).toBe('string');
      expect((name as string).trim(), `module.${code}.name`).not.toBe('');
    }
  });

  it.each(LANGUAGES)('%s has no empty translation', (_lang, file) => {
    for (const key of keys(file)) {
      const text = valueAt(file, key);
      expect(typeof text, key).toBe('string');
      expect((text as string).trim(), key).not.toBe('');
    }
  });

  it.each(LANGUAGES)('%s labels every known audit action', (_lang, file) => {
    expect(AUDIT_ACTIONS.length).toBeGreaterThan(0);
    for (const action of AUDIT_ACTIONS) {
      const label = valueAt(file, `audit.action.${action}`);
      expect(typeof label, `audit.action.${action}`).toBe('string');
      expect((label as string).trim(), `audit.action.${action}`).not.toBe('');
    }
  });

  it.each(LANGUAGES)('%s has no audit.action key for an unknown action', (_lang, file) => {
    expect(Object.keys(file.audit.action).sort()).toEqual([...AUDIT_ACTIONS].sort());
  });

  it.each(LANGUAGES)('%s names and describes every system role', (_lang, file) => {
    for (const role of SYSTEM_ROLES) {
      for (const leaf of ['name', 'description']) {
        expect(typeof valueAt(file, `role.${role}.${leaf}`), `role.${role}.${leaf}`).toBe('string');
      }
    }
  });

  it.each(LANGUAGES)('%s keeps the {{user}} and {{max}} parameters', (_lang, file) => {
    const users = file.admin.users;
    for (const text of [
      users.invite.sent,
      users.invite.notSent,
      users.roles.saved,
      users.resend.sent,
      users.disable.done,
      users.enable.done,
      users.syncFailed,
    ]) {
      expect(text).toContain('{{user}}');
    }
    expect(users.form.errors.maxLength).toContain('{{max}}');
  });

  it.each(LANGUAGES)(
    '%s keeps the {{module}} parameter of moduleNotEnabled.detail',
    (_lang, file) => {
      expect(file.moduleNotEnabled.detail).toContain('{{module}}');
    },
  );
});
