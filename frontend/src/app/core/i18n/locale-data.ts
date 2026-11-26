import { registerLocaleData } from '@angular/common';
import localeAr from '@angular/common/locales/ar';
import localeFr from '@angular/common/locales/fr';

/**
 * Donnees de langue pour le DatePipe d'Angular. L'anglais (en-US) est inclus par defaut ;
 * le francais et l'arabe doivent etre enregistres, sinon `date: ... : 'fr'` leve une erreur.
 * Idempotent.
 */
export function registerAppLocales(): void {
  registerLocaleData(localeFr, 'fr');
  registerLocaleData(localeAr, 'ar');
}

/** Format commun des dates et heures affichees (date courte + heure). */
export const DATE_TIME_FORMAT = 'short';

// TODO(etape-5): afficher les dates dans le fuseau de l'agence (parametrage) ; pour l'instant
// le DatePipe utilise le fuseau du navigateur.
