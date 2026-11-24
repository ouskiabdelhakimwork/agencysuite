import ar from '../../../../public/i18n/ar.json';
import en from '../../../../public/i18n/en.json';
import fr from '../../../../public/i18n/fr.json';
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

  it.each(LANGUAGES)(
    '%s keeps the {{module}} parameter of moduleNotEnabled.detail',
    (_lang, file) => {
      expect(file.moduleNotEnabled.detail).toContain('{{module}}');
    },
  );
});
