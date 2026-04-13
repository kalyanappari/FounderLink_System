import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Minimal stubs so we don't need to boot the full Angular test DI. */
function createMockAuthService(isLoggedIn: boolean) {
  return { isLoggedIn: () => isLoggedIn };
}

/** Execute the functional guard inside TestBed's injector. */
function runGuard(isLoggedIn: boolean) {
  return TestBed.runInInjectionContext(() =>
    authGuard(
      {} as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot
    )
  );
}

// ─── Suite ───────────────────────────────────────────────────────────────────

describe('authGuard', () => {
  let router: Router;

  function setup(isLoggedIn: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: createMockAuthService(isLoggedIn) }
      ]
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  afterEach(() => vi.restoreAllMocks());

  // ─── Authenticated user ───────────────────────────────────────────────────

  describe('when user IS logged in', () => {
    beforeEach(() => setup(true));

    it('should return true (allow navigation)', () => {
      const result = runGuard(true);
      expect(result).toBe(true);
    });

    it('should NOT navigate away', () => {
      runGuard(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  // ─── Unauthenticated user ─────────────────────────────────────────────────

  describe('when user is NOT logged in', () => {
    beforeEach(() => setup(false));

    it('should return false (block navigation)', () => {
      const result = runGuard(false);
      expect(result).toBe(false);
    });

    it('should redirect to /auth/login', () => {
      runGuard(false);
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
    });

    it('should call navigate exactly once', () => {
      runGuard(false);
      expect(router.navigate).toHaveBeenCalledTimes(1);
    });
  });
});
