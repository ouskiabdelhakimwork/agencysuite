import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { map } from 'rxjs';
import { isModuleCode } from '../../core/modules/module-code';

/**
 * Page provisoire affichee pour un module actif.
 * TODO(module): temporaire, remplacee par les vrais ecrans de chaque module aux etapes suivantes.
 */
@Component({
  selector: 'app-module-placeholder',
  imports: [TranslatePipe],
  templateUrl: './module-placeholder.html',
  styleUrl: './module-placeholder.scss',
})
export class ModulePlaceholder {
  private readonly data = toSignal(
    inject(ActivatedRoute).data.pipe(map((d) => d['module'] as unknown)),
  );

  protected readonly module = computed(() => {
    const code = this.data();
    return isModuleCode(code) ? code : null;
  });
}
