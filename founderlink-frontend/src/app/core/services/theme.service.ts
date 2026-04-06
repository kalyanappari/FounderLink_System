import { Injectable, signal, effect } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  /**
   * The core layout theme of the dashboard body.
   * 'onyx' (dark) | 'crystal' (white)
   */
  themeMode = signal<'onyx' | 'crystal'>('onyx');

  constructor() {
    // Load from localStorage if available
    const saved = localStorage.getItem('fl_theme');
    if (saved === 'crystal') {
      this.themeMode.set('crystal');
    }

    // Persist changes
    effect(() => {
      localStorage.setItem('fl_theme', this.themeMode());
    });
  }

  toggleTheme(): void {
    this.themeMode.update(m => m === 'onyx' ? 'crystal' : 'onyx');
  }

  isCrystal(): boolean {
    return this.themeMode() === 'crystal';
  }
}
