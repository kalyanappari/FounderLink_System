import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function createMockAuthService(token: string | null, overrides: Partial<{
  refresh: () => any
  clearSession: () => void
}> = {}) {
  return {
    token:        () => token,
    role:         () => null,
    userId:       () => null,
    email:        () => null,
    isLoggedIn:   () => !!token,
    refresh:      overrides.refresh       ?? vi.fn(() => of({ token: 'new-token', userId: 1, role: 'FOUNDER', email: 'a@a.com' })),
    clearSession: overrides.clearSession  ?? vi.fn()
  };
}

// ─── Suite ───────────────────────────────────────────────────────────────────

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;

  function setup(token: string | null, refreshOverride?: () => any) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: createMockAuthService(token, refreshOverride ? { refresh: refreshOverride } : {})
        }
      ]
    });
    http     = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router   = TestBed.inject(Router);
  }

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  // ─── Token attachment ─────────────────────────────────────────────────────

  describe('Token attachment', () => {
    it('should attach Authorization header when token exists', () => {
      setup('my-jwt-token');
      http.get('/api/users/42').subscribe();
      const req = httpMock.expectOne('/api/users/42');
      expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt-token');
      req.flush({});
    });

    it('should NOT attach Authorization header when token is null', () => {
      setup(null);
      http.get('/api/users/42').subscribe();
      const req = httpMock.expectOne('/api/users/42');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  // ─── Public auth endpoints — no token attached ────────────────────────────

  describe('Public auth endpoints (never attach token)', () => {
    const publicEndpoints = [
      '/api/auth/login',
      '/api/auth/register',
      '/api/auth/forgot-password',
      '/api/auth/reset-password',
      '/api/auth/refresh'
    ];

    publicEndpoints.forEach(url => {
      it(`should NOT attach Authorization header to ${url}`, () => {
        setup('my-jwt-token');
        http.post(url, {}).subscribe();
        const req = httpMock.expectOne(url);
        expect(req.request.headers.has('Authorization')).toBe(false);
        req.flush({});
      });
    });
  });

  // ─── 401 on protected endpoint → token refresh ────────────────────────────

  describe('401 handling on protected endpoints', () => {
    it('should call authService.refresh() on 401 from a protected endpoint', () => {
      const refreshFn = vi.fn(() => of({ token: 'refreshed-token', userId: 1, role: 'FOUNDER', email: 'a@a.com' }));
      setup('old-token', refreshFn);

      http.get('/api/investments/investor').subscribe();
      // First request returns 401
      const req = httpMock.expectOne('/api/investments/investor');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      // After refresh, the original request is retried with the new token
      const retryReq = httpMock.expectOne('/api/investments/investor');
      expect(retryReq.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
      retryReq.flush({});

      expect(refreshFn).toHaveBeenCalledTimes(1);
    });

    it('should retry the original request with the new token after a successful refresh', () => {
      const refreshFn = vi.fn(() => of({ token: 'new-token-xyz', userId: 1, role: 'FOUNDER', email: 'a@a.com' }));
      setup('expired-token', refreshFn);

      http.get('/api/team/1').subscribe();
      httpMock.expectOne('/api/team/1').flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      const retried = httpMock.expectOne('/api/team/1');
      expect(retried.request.headers.get('Authorization')).toContain('Bearer new-token-xyz');
      retried.flush({});
    });
  });

  // ─── 401 on public/ignored endpoints → no refresh ────────────────────────

  describe('401 on ignored endpoints (no refresh / no redirect)', () => {
    const ignoredEndpoints = [
      '/api/startup/1',
      '/api/startup/search',
      '/api/users/public/stats'
    ];

    ignoredEndpoints.forEach(url => {
      it(`should NOT trigger refresh on 401 from ${url}`, () => {
        const refreshFn = vi.fn(() => of({ token: 'x', userId: 1, role: 'FOUNDER', email: 'a@a.com' }));
        setup('my-jwt-token', refreshFn);

        http.get(url).subscribe({ error: () => {} });
        httpMock.expectOne(url).flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

        expect(refreshFn).not.toHaveBeenCalled();
        // No retry request should be pending
        httpMock.verify();
      });
    });
  });

  // ─── Refresh failure → navigate to /auth/login ───────────────────────────

  describe('when token refresh fails', () => {
    it('should navigate to /auth/login when refresh itself returns 401', () => {
      const refreshFn = vi.fn(() =>
        throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }))
      );
      setup('bad-token', refreshFn);
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      http.get('/api/investments/investor').subscribe({ error: () => {} });
      // Original 401
      httpMock.expectOne('/api/investments/investor')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

      expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  // ─── Non-401 errors pass through ─────────────────────────────────────────

  describe('Non-401 errors', () => {
    it('should pass through 403 without attempting a refresh', () => {
      const refreshFn = vi.fn();
      setup('my-jwt-token', refreshFn);

      let error: any;
      http.get('/api/investments/investor').subscribe({ error: e => error = e });
      httpMock.expectOne('/api/investments/investor')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });

      expect(refreshFn).not.toHaveBeenCalled();
      expect(error.status).toBe(403);
    });

    it('should pass through 500 without attempting a refresh', () => {
      const refreshFn = vi.fn();
      setup('my-jwt-token', refreshFn);

      let error: any;
      http.get('/api/wallets/1').subscribe({ error: e => error = e });
      httpMock.expectOne('/api/wallets/1')
        .flush('Server Error', { status: 500, statusText: 'Internal Server Error' });

      expect(refreshFn).not.toHaveBeenCalled();
      expect(error.status).toBe(500);
    });
  });
});
