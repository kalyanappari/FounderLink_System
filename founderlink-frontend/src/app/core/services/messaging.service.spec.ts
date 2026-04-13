import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { MessagingService } from './messaging.service';
import { AuthService } from './auth.service';

const API = '/api';

const mockAuthService = { userId: () => 42 };

const mockMessage = {
  id: 1,
  senderId: 42,
  receiverId: 99,
  content: 'Hello!',
  createdAt: '2025-06-01T00:00:00Z'
};

describe('MessagingService', () => {
  let service: MessagingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        MessagingService,
        { provide: AuthService, useValue: mockAuthService }
      ]
    });
    service = TestBed.inject(MessagingService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── sendMessage() ──────────────────────────────────────────────────────────

  describe('sendMessage()', () => {
    it('should POST /messages with senderId from AuthService', () => {
      service.sendMessage(99, 'Hello!').subscribe();
      const req = http.expectOne(`${API}/messages`);
      expect(req.request.method).toBe('POST');
      // senderId must come from session, not from user input
      expect(req.request.body.senderId).toBe(42);
      expect(req.request.body.receiverId).toBe(99);
      expect(req.request.body.content).toBe('Hello!');
      req.flush(mockMessage);
    });

    it('should return normalizePlain envelope with message data', () => {
      let result: any;
      service.sendMessage(99, 'Hi').subscribe(r => result = r);
      http.expectOne(`${API}/messages`).flush(mockMessage);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockMessage);
    });

    it('should return error envelope on HTTP failure', () => {
      let error: any;
      service.sendMessage(99, 'Hello!').subscribe({ error: e => error = e });
      http.expectOne(`${API}/messages`)
        .flush({ message: 'Bad Request' }, { status: 400, statusText: 'Bad Request' });
      expect(error.success).toBe(false);
    });
  });

  // ─── getConversation() ──────────────────────────────────────────────────────

  describe('getConversation()', () => {
    it('should GET /messages/conversation/:userId/:partnerId with pagination', () => {
      service.getConversation(99).subscribe();
      const req = http.expectOne(`${API}/messages/conversation/42/99?page=0&size=50`);
      expect(req.request.method).toBe('GET');
      req.flush({ content: [mockMessage], totalElements: 1 });
    });

    it('should use userId from AuthService in the URL', () => {
      service.getConversation(99).subscribe();
      const req = http.expectOne(`${API}/messages/conversation/42/99?page=0&size=50`);
      expect(req.request.url).toContain('/42/');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should pass custom page and size', () => {
      service.getConversation(99, 1, 20).subscribe();
      const req = http.expectOne(`${API}/messages/conversation/42/99?page=1&size=20`);
      expect(req.request.url).toContain('page=1');
      expect(req.request.url).toContain('size=20');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should return normalizePage envelope with message list', () => {
      let result: any;
      service.getConversation(99).subscribe(r => result = r);
      http.expectOne(`${API}/messages/conversation/42/99?page=0&size=50`)
        .flush({ content: [mockMessage], totalElements: 1, totalPages: 1 });
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockMessage]);
      expect(result.totalElements).toBe(1);
    });
  });

  // ─── getPartnerIds() ────────────────────────────────────────────────────────

  describe('getPartnerIds()', () => {
    it('should GET /messages/partners/:userId', () => {
      service.getPartnerIds().subscribe();
      const req = http.expectOne(`${API}/messages/partners/42`);
      expect(req.request.method).toBe('GET');
      req.flush([99, 100, 101]);
    });

    it('should use userId from AuthService in the URL', () => {
      service.getPartnerIds().subscribe();
      const req = http.expectOne(`${API}/messages/partners/42`);
      expect(req.request.url).toContain('/42');
      req.flush([]);
    });

    it('should return normalizeArray envelope with partner id list', () => {
      let result: any;
      service.getPartnerIds().subscribe(r => result = r);
      http.expectOne(`${API}/messages/partners/42`).flush([99, 100]);
      expect(result.success).toBe(true);
      expect(result.data).toEqual([99, 100]);
    });
  });

  // ─── getById() ──────────────────────────────────────────────────────────────

  describe('getById()', () => {
    it('should GET /messages/:id', () => {
      service.getById(1).subscribe();
      const req = http.expectOne(`${API}/messages/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockMessage);
    });

    it('should return normalizePlain envelope with the message', () => {
      let result: any;
      service.getById(1).subscribe(r => result = r);
      http.expectOne(`${API}/messages/1`).flush(mockMessage);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockMessage);
    });
  });
});
