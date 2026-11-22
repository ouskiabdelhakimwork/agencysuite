import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { PlatformApi, PlatformInfo } from '../../core/api/platform-api';
import { LanguageService } from '../../core/i18n/language.service';

type BackendState =
  | { readonly status: 'loading' }
  | { readonly status: 'ok'; readonly info: PlatformInfo }
  | { readonly status: 'error' };

/**
 * Page d'accueil provisoire : verifie de bout en bout le socle de l'etape 1
 * (traductions, sens d'ecriture, appel a l'API dans la langue choisie).
 */
@Component({
  selector: 'app-home',
  imports: [TranslatePipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly api = inject(PlatformApi);
  protected readonly language = inject(LanguageService);

  // L'API est rappelee a chaque changement de langue pour recuperer le message traduit cote serveur.
  protected readonly backend = toSignal(
    toObservable(this.language.current).pipe(
      switchMap(() =>
        this.api.info().pipe(
          map((info): BackendState => ({ status: 'ok', info })),
          catchError(() => of<BackendState>({ status: 'error' })),
          startWith({ status: 'loading' } as BackendState),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as BackendState },
  );
}
