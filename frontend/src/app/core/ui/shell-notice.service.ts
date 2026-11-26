import { Injectable, signal } from '@angular/core';

/**
 * Message d'information affiche par la coquille, au-dessus du contenu, jusqu'a sa fermeture.
 * Ne porte qu'une cle de traduction : aucune valeur venue du serveur ou de l'URL.
 */
@Injectable({ providedIn: 'root' })
export class ShellNotice {
  private readonly keySignal = signal<string | null>(null);

  readonly key = this.keySignal.asReadonly();

  show(key: string): void {
    this.keySignal.set(key);
  }

  dismiss(): void {
    this.keySignal.set(null);
  }
}
