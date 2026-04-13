import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    // Always start with a clean localStorage
    localStorage.clear();

    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  // ─── Initial State ──────────────────────────────────────────────────────────

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should default to "onyx" when localStorage is empty', () => {
    expect(service.themeMode()).toBe('onyx');
  });

  it('should load "crystal" from localStorage on construction', () => {
    // Simulate a returning user who had crystal theme saved
    localStorage.setItem('fl_theme', 'crystal');

    // Re-create the service so the constructor re-reads localStorage
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const freshService = TestBed.inject(ThemeService);

    expect(freshService.themeMode()).toBe('crystal');
  });

  it('should stay "onyx" when localStorage has an unrecognised value', () => {
    localStorage.setItem('fl_theme', 'dark-mode');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const freshService = TestBed.inject(ThemeService);

    expect(freshService.themeMode()).toBe('onyx');
  });

  // ─── toggleTheme ────────────────────────────────────────────────────────────

  it('should toggle from "onyx" → "crystal"', () => {
    expect(service.themeMode()).toBe('onyx');
    service.toggleTheme();
    expect(service.themeMode()).toBe('crystal');
  });

  it('should toggle from "crystal" → "onyx"', () => {
    service.toggleTheme(); // onyx → crystal
    service.toggleTheme(); // crystal → onyx
    expect(service.themeMode()).toBe('onyx');
  });

  it('should update the signal to "crystal" after one toggle (signal is synchronous)', () => {
    service.toggleTheme();
    // Signal update is always synchronous
    expect(service.themeMode()).toBe('crystal');
  });

  it('should update the signal back to "onyx" after double-toggle (signal is synchronous)', () => {
    service.toggleTheme(); // onyx → crystal
    service.toggleTheme(); // crystal → onyx
    // Signal update is always synchronous
    expect(service.themeMode()).toBe('onyx');
  });

  // ─── isCrystal ──────────────────────────────────────────────────────────────

  it('should return false for isCrystal() when theme is "onyx"', () => {
    expect(service.isCrystal()).toBe(false);
  });

  it('should return true for isCrystal() when theme is "crystal"', () => {
    service.toggleTheme();
    expect(service.isCrystal()).toBe(true);
  });

  it('should return false for isCrystal() after toggling back to "onyx"', () => {
    service.toggleTheme(); // crystal
    service.toggleTheme(); // onyx
    expect(service.isCrystal()).toBe(false);
  });
});
