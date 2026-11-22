import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageSwitcher } from './core/i18n/language-switcher';

/** Coquille de l'application : en-tete (marque, langue) et zone de contenu. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TranslatePipe, LanguageSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
