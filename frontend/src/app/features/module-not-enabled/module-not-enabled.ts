import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { map } from 'rxjs';
import { isModuleCode } from '../../core/modules/module-code';

/**
 * Page « module non active ». Le parametre `module` de l'URL n'est affiche que s'il
 * correspond a un code connu (et alors via son nom traduit) : une valeur arbitraire
 * venue de l'URL n'est jamais reinjectee dans la page.
 */
@Component({
  selector: 'app-module-not-enabled',
  imports: [TranslatePipe, RouterLink],
  templateUrl: './module-not-enabled.html',
  styleUrl: './module-not-enabled.scss',
})
export class ModuleNotEnabled {
  private readonly param = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(map((p) => p.get('module'))),
  );

  /** Marqueur (caractere a usage prive) remplace par le nom du module isole dans la phrase traduite. */
  protected readonly slot = '\uE000';

  protected readonly module = computed(() => {
    const code = this.param();
    return isModuleCode(code) ? code : null;
  });
}
