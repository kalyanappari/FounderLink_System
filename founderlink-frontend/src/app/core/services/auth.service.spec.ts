import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse } from '../../models';

// ─── Helpers ─────────────────────────────────────────────────────────────────

const API = '/api';

const mockAuthResponse: AuthResponse = {
  token: 'jwt-token-abc',
  email: 'alice@example.com',
  role: 'FOUNDER',
  userId: 42
};

function seedLocalStorage() {
  localStorage.setItem('token',  mockAuthResponse.token);
  localStorage.setItem('userId', String(mockAuthResponse.userId));
  localStorage.setItem('role',   mockAuthResponse.role);
  localStorage.setItem('email',  mockAuthResponse.email);
}

// ─── Suite ───────────────────────────────────────────────────────────────────

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: Router;

  function setup() {
    service = TestBed.inject(AuthService);
    http    = TestBed.inject(HttpTestingController);
    router  = TestBed.inject(Router);
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [provideRouter([])]
    });
  });

  afterEach(() => {
    http?.verify();   // assert no unmatched HTTP requests
    localStorage.clear();
  });

  // ─── Construction / Signal Initialization ─────────────────────────────────

  describe('Initialization', () => {
    it('should be created', () => {
      setup();
      expect(service).toBeTruthy();
    });

    it('should initialize all signals as null when localStorage is empty', () => {
      setup();
      expect(service.token()).toBeNull();
      expect(service.userId()).toBeNull();
      expect(service.role()).toBeNull();
      expect(service.email()).toBeNull();
    });

    it('should load token from localStorage on construction', () => {
      seedLocalStorage();
      setup();
      expect(service.token()).toBe('jwt-token-abc');
    });

    it('should parse userId as a number from localStorage', () => {
      seedLocalStorage();
      setup();
      expect(service.userId()).toBe(42);
      expect(typeof service.userId()).toBe('number');
    });

    it('should load role from localStorage', () => {
      seedLocalStorage();
      setup();
      expect(service.role()).toBe('FOUNDER');
    });

    it('should load email from localStorage', () => {
      seedLocalStorage();
      setup();
      expect(service.email()).toBe('alice@example.com');
    });
  });

  // ─── isLoggedIn (computed) ────────────────────────────────────────────────

  describe('isLoggedIn (computed)', () => {
    it('should be false when no token in localStorage', () => {
      setup();
      expect(service.isLoggedIn()).toBe(false);
    });

    it('should be true when token exists in localStorage', () => {
      seedLocalStorage();
      setup();
      expect(service.isLoggedIn()).toBe(true);
    });

    it('should become true after a successful login (signal updated synchronously by tap)', () => {
      setup();
      expect(service.isLoggedIn()).toBe(false);

      // Subscribe to trigger the HTTP call
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();
      // Flush makes the tap() run synchronously
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(service.isLoggedIn()).toBe(true);
    });

    it('should become false after clearSession is called', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      expect(service.isLoggedIn()).toBe(true);

      service.clearSession();

      expect(service.isLoggedIn()).toBe(false);
      navigateSpy.mockRestore();
    });
  });

  // ─── register() ───────────────────────────────────────────────────────────

  describe('register()', () => {
    it('should POST to /auth/register with the request payload', () => {
      setup();
      const req = { name: 'Alice', email: 'alice@example.com', password: 'Secret1!', role: 'FOUNDER' as const };

      service.register(req).subscribe();

      const testReq = http.expectOne(`${API}/auth/register`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(req);
      testReq.flush({ message: 'Registered' });
    });

    it('should emit the server response', () => {
      setup();
      let result: any;

      service.register({ name: 'Bob', email: 'bob@example.com', password: 'Pass1!', role: 'INVESTOR' })
        .subscribe(r => result = r);
      http.expectOne(`${API}/auth/register`).flush({ message: 'Registered' });

      expect(result).toEqual({ message: 'Registered' });
    });
  });

  // ─── login() ──────────────────────────────────────────────────────────────

  describe('login()', () => {
    it('should POST to /auth/login with withCredentials=true', () => {
      setup();
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();

      const testReq = http.expectOne(`${API}/auth/login`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.withCredentials).toBe(true);
      testReq.flush(mockAuthResponse);
    });

    it('should store token in localStorage after login', () => {
      setup();
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(localStorage.getItem('token')).toBe('jwt-token-abc');
    });

    it('should store userId as string in localStorage after login', () => {
      setup();
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(localStorage.getItem('userId')).toBe('42');
    });

    it('should store role and email in localStorage after login', () => {
      setup();
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(localStorage.getItem('role')).toBe('FOUNDER');
      expect(localStorage.getItem('email')).toBe('alice@example.com');
    });

    it('should update token signal after login', () => {
      setup();
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(service.token()).toBe('jwt-token-abc');
    });

    it('should update userId, role, email signals after login', () => {
      setup();
      service.login({ email: 'alice@example.com', password: 'Secret1!' }).subscribe();
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(service.userId()).toBe(42);
      expect(service.role()).toBe('FOUNDER');
      expect(service.email()).toBe('alice@example.com');
    });

    it('should emit the AuthResponse from the observable', () => {
      setup();
      let emitted: AuthResponse | undefined;

      service.login({ email: 'alice@example.com', password: 'Secret1!' })
        .subscribe(r => emitted = r);
      http.expectOne(`${API}/auth/login`).flush(mockAuthResponse);

      expect(emitted).toEqual(mockAuthResponse);
    });
  });

  // ─── refresh() ────────────────────────────────────────────────────────────

  describe('refresh()', () => {
    it('should POST to /auth/refresh with empty body and withCredentials=true', () => {
      setup();
      service.refresh().subscribe();

      const testReq = http.expectOne(`${API}/auth/refresh`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual({});
      expect(testReq.request.withCredentials).toBe(true);
      testReq.flush(mockAuthResponse);
    });

    it('should update the token signal after a successful refresh', () => {
      setup();
      const refreshedResponse: AuthResponse = { ...mockAuthResponse, token: 'new-jwt-token' };

      service.refresh().subscribe();
      http.expectOne(`${API}/auth/refresh`).flush(refreshedResponse);

      expect(service.token()).toBe('new-jwt-token');
      expect(localStorage.getItem('token')).toBe('new-jwt-token');
    });
  });

  // ─── logout() ─────────────────────────────────────────────────────────────

  describe('logout()', () => {
    it('should POST to /auth/logout with withCredentials=true', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.logout();

      const testReq = http.expectOne(`${API}/auth/logout`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.withCredentials).toBe(true);
      testReq.flush({});
      navigateSpy.mockRestore();
    });

    it('should clear all signals on successful logout (complete path)', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.logout();
      http.expectOne(`${API}/auth/logout`).flush({});

      expect(service.token()).toBeNull();
      expect(service.userId()).toBeNull();
      expect(service.role()).toBeNull();
      expect(service.email()).toBeNull();
      navigateSpy.mockRestore();
    });

    it('should clear localStorage on successful logout', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.logout();
      http.expectOne(`${API}/auth/logout`).flush({});

      expect(localStorage.getItem('token')).toBeNull();
      navigateSpy.mockRestore();
    });

    it('should clear session even when the logout HTTP call fails (error path)', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.logout();
      // Simulate network error
      http.expectOne(`${API}/auth/logout`).error(new ProgressEvent('error'));

      // Session must still be wiped
      expect(service.token()).toBeNull();
      expect(service.isLoggedIn()).toBe(false);
      expect(localStorage.getItem('token')).toBeNull();
      navigateSpy.mockRestore();
    });
  });

  // ─── forgotPassword() ─────────────────────────────────────────────────────

  describe('forgotPassword()', () => {
    it('should POST to /auth/forgot-password with the email payload', () => {
      setup();
      service.forgotPassword({ email: 'alice@example.com' }).subscribe();

      const testReq = http.expectOne(`${API}/auth/forgot-password`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual({ email: 'alice@example.com' });
      testReq.flush({ message: 'PIN sent' });
    });

    it('should emit the server response', () => {
      setup();
      let result: any;

      service.forgotPassword({ email: 'alice@example.com' })
        .subscribe(r => result = r);
      http.expectOne(`${API}/auth/forgot-password`).flush({ message: 'PIN sent' });

      expect(result).toEqual({ message: 'PIN sent' });
    });
  });

  // ─── resetPassword() ──────────────────────────────────────────────────────

  describe('resetPassword()', () => {
    it('should POST to /auth/reset-password with the full reset payload', () => {
      setup();
      const payload = { email: 'alice@example.com', pin: '123456', newPassword: 'NewPass1!' };

      service.resetPassword(payload).subscribe();

      const testReq = http.expectOne(`${API}/auth/reset-password`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(payload);
      testReq.flush({ message: 'Password reset' });
    });

    it('should emit the server response', () => {
      setup();
      let result: any;
      const payload = { email: 'alice@example.com', pin: '654321', newPassword: 'Newer1!' };

      service.resetPassword(payload).subscribe(r => result = r);
      http.expectOne(`${API}/auth/reset-password`).flush({ message: 'Password reset' });

      expect(result).toEqual({ message: 'Password reset' });
    });
  });

  // ─── clearSession() ───────────────────────────────────────────────────────

  describe('clearSession()', () => {
    it('should set all signals to null', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.clearSession();

      expect(service.token()).toBeNull();
      expect(service.userId()).toBeNull();
      expect(service.role()).toBeNull();
      expect(service.email()).toBeNull();
      navigateSpy.mockRestore();
    });

    it('should clear all localStorage keys', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.clearSession();

      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('userId')).toBeNull();
      expect(localStorage.getItem('role')).toBeNull();
      expect(localStorage.getItem('email')).toBeNull();
      navigateSpy.mockRestore();
    });

    it('should navigate to /auth/login', () => {
      seedLocalStorage();
      setup();
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      service.clearSession();

      expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
      navigateSpy.mockRestore();
    });
  });
});
