import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export type LangCode = 'fr' | 'ar' | 'en';
export type TextDirection = 'ltr' | 'rtl';

export interface SupportedLanguage {
  readonly code: LangCode;
  readonly dir: TextDirection;
  /** Nom de la langue dans sa propre langue (non traduit, par convention). */
  readonly label: string;
}

/** Langues de la V1 (ADR-16). Le francais est la langue par defaut. */
export const SUPPORTED_LANGUAGES: readonly SupportedLanguage[] = [
  { code: 'fr', dir: 'ltr', label: 'Français' },
  { code: 'ar', dir: 'rtl', label: 'العربية' },
  { code: 'en', dir: 'ltr', label: 'English' },
];

export const DEFAULT_LANGUAGE = SUPPORTED_LANGUAGES[0];

const STORAGE_KEY = 'agencysuite.lang';

/**
 * Langue courante de l'interface : charge les traductions et applique
 * lang et dir sur la balise <html>, ce qui bascule toute la mise en page en
 * droite-a-gauche pour l'arabe (les styles utilisent des proprietes CSS logiques).
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  readonly languages = SUPPORTED_LANGUAGES;

  private readonly currentLanguage = signal<SupportedLanguage>(DEFAULT_LANGUAGE);
  readonly current = this.currentLanguage.asReadonly();
  readonly direction = computed(() => this.currentLanguage().dir);

  /** Appele au demarrage : langue memorisee, sinon langue du navigateur, sinon francais. */
  init() {
    return this.use(this.detect());
  }

  /** Change de langue. Un code non pris en charge retombe sur le francais. */
  use(code: string) {
    const language = SUPPORTED_LANGUAGES.find((l) => l.code === code) ?? DEFAULT_LANGUAGE;
    this.currentLanguage.set(language);
    this.applyToDocument(language);
    this.remember(language.code);
    return this.translate.use(language.code);
  }

  private detect(): LangCode {
    const stored = this.read();
    if (stored && isSupported(stored)) {
      return stored;
    }
    const browser = this.document.defaultView?.navigator?.language?.slice(0, 2).toLowerCase();
    if (browser && isSupported(browser)) {
      return browser;
    }
    return DEFAULT_LANGUAGE.code;
  }

  private applyToDocument(language: SupportedLanguage): void {
    const html = this.document.documentElement;
    html.lang = language.code;
    html.dir = language.dir;
  }

  // Le stockage local peut etre indisponible (navigation privee, politique d'entreprise) :
  // la langue n'est alors simplement pas memorisee.
  private remember(code: LangCode): void {
    try {
      this.document.defaultView?.localStorage?.setItem(STORAGE_KEY, code);
    } catch {
      /* stockage indisponible */
    }
  }

  private read(): string | null {
    try {
      return this.document.defaultView?.localStorage?.getItem(STORAGE_KEY) ?? null;
    } catch {
      return null;
    }
  }
}

function isSupported(code: string): code is LangCode {
  return SUPPORTED_LANGUAGES.some((l) => l.code === code);
}
