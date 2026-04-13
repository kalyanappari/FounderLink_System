import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function createMockAuthService(role: string | null) {
  return { role: () => role };
}

/** Invoke the factory-returned CanActivateFn inside the injector. */
function runGuard(allowedRoles: string[], userRole: string | null) {
  return TestBed.runInInjectionContext(() =>
    roleGuard(allowedRoles)(
      {} as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot
    )
  );
}

// ─── Suite ────────────────────────────────────────────────────────────────────

describe('roleGuard', () => {
  let router: Router;

  function setup(role: string | null) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: createMockAuthService(role) }
      ]
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  afterEach(() => vi.restoreAllMocks());

  // ─── No role / unauthenticated ────────────────────────────────────────────

  describe('when role is null (not logged in)', () => {
    beforeEach(() => setup(null));

    it('should return false', () => {
      expect(runGuard(['FOUNDER'], null)).toBe(false);
    });

    it('should redirect to /auth/login', () => {
      runGuard(['FOUNDER'], null);
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  // ─── Exact role match (no prefix) ─────────────────────────────────────────

  describe('when role matches (no ROLE_ prefix)', () => {
    beforeEach(() => setup('FOUNDER'));

    it('should return true for FOUNDER in allowed list', () => {
      expect(runGuard(['FOUNDER', 'INVESTOR'], 'FOUNDER')).toBe(true);
    });

    it('should not navigate when access is granted', () => {
      runGuard(['FOUNDER'], 'FOUNDER');
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  // ─── ROLE_ prefix stripping ───────────────────────────────────────────────

  describe('when JWT role has ROLE_ prefix', () => {
    beforeEach(() => setup('ROLE_INVESTOR'));

    it('should strip ROLE_ prefix and still grant access', () => {
      expect(runGuard(['INVESTOR'], 'ROLE_INVESTOR')).toBe(true);
    });

    it('should strip ROLE_ prefix and block if not in allowed list', () => {
      expect(runGuard(['FOUNDER'], 'ROLE_INVESTOR')).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });
  });

  // ─── Role mismatch ────────────────────────────────────────────────────────

  describe('when role does not match allowed list', () => {
    beforeEach(() => setup('COFOUNDER'));

    it('should return false', () => {
      expect(runGuard(['FOUNDER', 'INVESTOR'], 'COFOUNDER')).toBe(false);
    });

    it('should redirect to /dashboard (not /auth/login)', () => {
      runGuard(['FOUNDER'], 'COFOUNDER');
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('should NOT redirect to /auth/login on role mismatch', () => {
      runGuard(['FOUNDER'], 'COFOUNDER');
      expect(router.navigate).not.toHaveBeenCalledWith(['/auth/login']);
    });
  });

  // ─── All supported roles ──────────────────────────────────────────────────

  describe('supported roles matrix', () => {
    const roles = ['FOUNDER', 'INVESTOR', 'COFOUNDER', 'ADMIN'];

    roles.forEach(role => {
      it(`should allow ${role} when it is the only entry in allowedRoles`, () => {
        setup(role);
        expect(runGuard([role], role)).toBe(true);
      });
    });

    it('should allow ADMIN when multiple roles are in the allowed list', () => {
      setup('ADMIN');
      expect(runGuard(['FOUNDER', 'INVESTOR', 'ADMIN'], 'ADMIN')).toBe(true);
    });
  });
});
