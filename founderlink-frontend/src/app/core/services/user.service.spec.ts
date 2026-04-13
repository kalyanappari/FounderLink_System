import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { UserService } from './user.service';
import { AuthService } from './auth.service';

const API = '/api';

// ─── Mock AuthService ─────────────────────────────────────────────────────────
const mockAuthService = {
  userId: () => 42
};

const mockUser = {
  userId: 42,
  name: 'Alice',
  email: 'alice@example.com',
  role: 'FOUNDER' as const,
  skills: 'Angular, Java',
  experience: '3 years',
  bio: 'Builder',
  portfolioLinks: 'https://github.com/alice'
};

// ─── Suite ────────────────────────────────────────────────────────────────────
describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        UserService,
        { provide: AuthService, useValue: mockAuthService }
      ]
    });
    service = TestBed.inject(UserService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── getUser() ──────────────────────────────────────────────────────────────

  describe('getUser()', () => {
    it('should GET /users/:id', () => {
      service.getUser(42).subscribe();
      const req = http.expectOne(`${API}/users/42`);
      expect(req.request.method).toBe('GET');
      req.flush(mockUser);
    });

    it('should return normalizePlain envelope with the user data', () => {
      let result: any;
      service.getUser(42).subscribe(r => result = r);
      http.expectOne(`${API}/users/42`).flush(mockUser);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockUser);
      expect(result.error).toBeNull();
    });

    it('should return error envelope on HTTP failure', () => {
      let error: any;
      service.getUser(99).subscribe({ error: e => error = e });
      http.expectOne(`${API}/users/99`).flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
      expect(error.success).toBe(false);
      expect(error.error).toBeTruthy();
    });
  });

  // ─── updateMyProfile() ──────────────────────────────────────────────────────

  describe('updateMyProfile()', () => {
    it('should PUT /users/:sessionUserId with the payload', () => {
      const payload = { name: 'Alice Updated', bio: 'New bio' };
      service.updateMyProfile(payload).subscribe();
      const req = http.expectOne(`${API}/users/42`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(payload);
      req.flush(mockUser);
    });

    it('should use userId from AuthService (not from user input)', () => {
      service.updateMyProfile({ name: 'Alice' }).subscribe();
      // Should target /users/42 from auth signal, not a user-supplied id
      const req = http.expectOne(`${API}/users/42`);
      expect(req.request.url).toContain('/42');
      req.flush(mockUser);
    });

    it('should return normalizePlain envelope with updated data', () => {
      let result: any;
      service.updateMyProfile({ name: 'Alice' }).subscribe(r => result = r);
      http.expectOne(`${API}/users/42`).flush(mockUser);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockUser);
    });
  });

  // ─── getAllUsers() ──────────────────────────────────────────────────────────

  describe('getAllUsers()', () => {
    it('should GET /users with default page=0 & size=10', () => {
      service.getAllUsers().subscribe();
      const req = http.expectOne(`${API}/users?page=0&size=10`);
      expect(req.request.method).toBe('GET');
      req.flush({ content: [mockUser], totalElements: 1 });
    });

    it('should include custom page and size params', () => {
      service.getAllUsers(2, 5).subscribe();
      const req = http.expectOne(`${API}/users?page=2&size=5`);
      expect(req.request.url).toContain('page=2');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should include search param when provided', () => {
      service.getAllUsers(0, 10, 'alice').subscribe();
      const req = http.expectOne(`${API}/users?page=0&size=10&search=alice`);
      expect(req.request.url).toContain('search=alice');
      req.flush({ content: [mockUser], totalElements: 1 });
    });

    it('should return normalizePage envelope', () => {
      let result: any;
      service.getAllUsers().subscribe(r => result = r);
      http.expectOne(`${API}/users?page=0&size=10`)
        .flush({ content: [mockUser], totalElements: 1, totalPages: 1 });
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockUser]);
      expect(result.totalElements).toBe(1);
    });
  });

  // ─── getUsersByRole() ───────────────────────────────────────────────────────

  describe('getUsersByRole()', () => {
    it('should GET /users/role/:role with pagination params', () => {
      service.getUsersByRole('INVESTOR').subscribe();
      const req = http.expectOne(`${API}/users/role/INVESTOR?page=0&size=10`);
      expect(req.request.method).toBe('GET');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should include search param when provided', () => {
      service.getUsersByRole('FOUNDER', 0, 10, 'bob').subscribe();
      const req = http.expectOne(`${API}/users/role/FOUNDER?page=0&size=10&search=bob`);
      expect(req.request.url).toContain('search=bob');
      req.flush({ content: [], totalElements: 0 });
    });
  });

  // ─── getPublicStats() ──────────────────────────────────────────────────────

  describe('getPublicStats()', () => {
    it('should GET /users/public/stats', () => {
      service.getPublicStats().subscribe();
      const req = http.expectOne(`${API}/users/public/stats`);
      expect(req.request.method).toBe('GET');
      req.flush({ founders: 10, investors: 5, cofounders: 3 });
    });

    it('should emit the raw stats object', () => {
      let result: any;
      service.getPublicStats().subscribe(r => result = r);
      http.expectOne(`${API}/users/public/stats`)
        .flush({ founders: 10, investors: 5, cofounders: 3 });
      expect(result).toEqual({ founders: 10, investors: 5, cofounders: 3 });
    });
  });
});
