import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { NotificationService } from './notification.service';
import { AuthService } from './auth.service';

const API = '/api';

const mockAuthService = { userId: () => 42 };

const mockNotification = {
  id: 3,
  userId: 42,
  type: 'INVESTMENT',
  message: 'You received a new investment',
  read: false,
  createdAt: '2025-06-01T00:00:00Z'
};

describe('NotificationService', () => {
  let service: NotificationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        NotificationService,
        { provide: AuthService, useValue: mockAuthService }
      ]
    });
    service = TestBed.inject(NotificationService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── getMyNotifications() ───────────────────────────────────────────────────

  describe('getMyNotifications()', () => {
    it('should GET /notifications/:userId with default page=0 & size=10', () => {
      service.getMyNotifications().subscribe();
      const req = http.expectOne(`${API}/notifications/42?page=0&size=10`);
      expect(req.request.method).toBe('GET');
      req.flush({ content: [mockNotification], totalElements: 1 });
    });

    it('should use userId from AuthService', () => {
      service.getMyNotifications().subscribe();
      // URL should include the session userId (42)
      const req = http.expectOne(`${API}/notifications/42?page=0&size=10`);
      expect(req.request.url).toContain('/42');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should include custom page and size', () => {
      service.getMyNotifications(1, 20).subscribe();
      const req = http.expectOne(`${API}/notifications/42?page=1&size=20`);
      expect(req.request.url).toContain('page=1');
      expect(req.request.url).toContain('size=20');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should return normalizePage envelope with notification list', () => {
      let result: any;
      service.getMyNotifications().subscribe(r => result = r);
      http.expectOne(`${API}/notifications/42?page=0&size=10`)
        .flush({ content: [mockNotification], totalElements: 1, totalPages: 1 });
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockNotification]);
      expect(result.totalElements).toBe(1);
    });
  });

  // ─── getMyUnreadNotifications() ─────────────────────────────────────────────

  describe('getMyUnreadNotifications()', () => {
    it('should GET /notifications/:userId/unread with pagination', () => {
      service.getMyUnreadNotifications().subscribe();
      const req = http.expectOne(`${API}/notifications/42/unread?page=0&size=10`);
      expect(req.request.method).toBe('GET');
      req.flush({ content: [mockNotification], totalElements: 1 });
    });

    it('should include custom page and size', () => {
      service.getMyUnreadNotifications(2, 5).subscribe();
      const req = http.expectOne(`${API}/notifications/42/unread?page=2&size=5`);
      expect(req.request.url).toContain('page=2');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should return normalizePage envelope', () => {
      let result: any;
      service.getMyUnreadNotifications().subscribe(r => result = r);
      http.expectOne(`${API}/notifications/42/unread?page=0&size=10`)
        .flush({ content: [mockNotification], totalElements: 1 });
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockNotification]);
    });
  });

  // ─── getUnreadCount() ───────────────────────────────────────────────────────

  describe('getUnreadCount()', () => {
    it('should GET /notifications/:userId/unread/count', () => {
      service.getUnreadCount().subscribe();
      const req = http.expectOne(`${API}/notifications/42/unread/count`);
      expect(req.request.method).toBe('GET');
      req.flush(5);
    });

    it('should return normalizePlain envelope with the count', () => {
      let result: any;
      service.getUnreadCount().subscribe(r => result = r);
      http.expectOne(`${API}/notifications/42/unread/count`).flush(7);
      expect(result.success).toBe(true);
      expect(result.data).toBe(7);
      expect(result.error).toBeNull();
    });
  });

  // ─── markAsRead() ───────────────────────────────────────────────────────────

  describe('markAsRead()', () => {
    it('should PUT /notifications/:id/read with empty body', () => {
      service.markAsRead(3).subscribe();
      const req = http.expectOne(`${API}/notifications/3/read`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({});
      req.flush({ ...mockNotification, read: true });
    });

    it('should return normalizePlain envelope with the updated notification', () => {
      let result: any;
      service.markAsRead(3).subscribe(r => result = r);
      http.expectOne(`${API}/notifications/3/read`)
        .flush({ ...mockNotification, read: true });
      expect(result.success).toBe(true);
      expect(result.data?.read).toBe(true);
    });
  });

  // ─── Error handling ─────────────────────────────────────────────────────────

  describe('Error handling', () => {
    it('should return normalizeError envelope on HTTP failure', () => {
      let error: any;
      service.getMyNotifications().subscribe({ error: e => error = e });
      http.expectOne(`${API}/notifications/42?page=0&size=10`)
        .flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
      expect(error.success).toBe(false);
      expect(error.error).toBeTruthy();
    });
  });
});
