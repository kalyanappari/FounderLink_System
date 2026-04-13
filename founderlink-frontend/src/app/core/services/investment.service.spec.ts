import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { InvestmentService } from './investment.service';

const API = '/api';

const mockInvestment = {
  id: 7,
  startupId: 1,
  investorId: 99,
  amount: 25000,
  status: 'PENDING' as const,
  createdAt: '2025-06-01T00:00:00Z'
};

const wrapped = (data: any) => ({ message: 'ok', data });

describe('InvestmentService', () => {
  let service: InvestmentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(InvestmentService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── create() ───────────────────────────────────────────────────────────────

  describe('create()', () => {
    it('should POST /investments with the investment request', () => {
      const req = { startupId: 1, amount: 25000 };
      service.create(req).subscribe();
      const httpReq = http.expectOne(`${API}/investments`);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(wrapped(mockInvestment));
    });

    it('should return normalizeWrapped envelope with investment data', () => {
      let result: any;
      service.create({ startupId: 1, amount: 25000 }).subscribe(r => result = r);
      http.expectOne(`${API}/investments`).flush(wrapped(mockInvestment));
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockInvestment);
      expect(result.error).toBeNull();
    });

    it('should propagate error envelope on HTTP failure', () => {
      let error: any;
      service.create({ startupId: 1, amount: 25000 }).subscribe({ error: e => error = e });
      http.expectOne(`${API}/investments`)
        .flush({ message: 'Insufficient funds' }, { status: 400, statusText: 'Bad Request' });
      expect(error.success).toBe(false);
      expect(error.error).toBeTruthy();
    });
  });

  // ─── getMyPortfolio() ───────────────────────────────────────────────────────

  describe('getMyPortfolio()', () => {
    it('should GET /investments/investor', () => {
      service.getMyPortfolio().subscribe();
      const req = http.expectOne(`${API}/investments/investor`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockInvestment]));
    });

    it('should return normalizeWrapped envelope with portfolio list', () => {
      let result: any;
      service.getMyPortfolio().subscribe(r => result = r);
      http.expectOne(`${API}/investments/investor`).flush(wrapped([mockInvestment]));
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockInvestment]);
    });
  });

  // ─── getStartupInvestments() ────────────────────────────────────────────────

  describe('getStartupInvestments()', () => {
    it('should GET /investments/startup/:startupId', () => {
      service.getStartupInvestments(1).subscribe();
      const req = http.expectOne(`${API}/investments/startup/1`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockInvestment]));
    });

    it('should return normalizeWrapped envelope with investment list', () => {
      let result: any;
      service.getStartupInvestments(1).subscribe(r => result = r);
      http.expectOne(`${API}/investments/startup/1`).flush(wrapped([mockInvestment]));
      expect(result.data).toEqual([mockInvestment]);
    });
  });

  // ─── getById() ──────────────────────────────────────────────────────────────

  describe('getById()', () => {
    it('should GET /investments/:id', () => {
      service.getById(7).subscribe();
      const req = http.expectOne(`${API}/investments/7`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped(mockInvestment));
    });

    it('should return normalizeWrapped envelope with a single investment', () => {
      let result: any;
      service.getById(7).subscribe(r => result = r);
      http.expectOne(`${API}/investments/7`).flush(wrapped(mockInvestment));
      expect(result.success).toBe(true);
      expect(result.data?.id).toBe(7);
    });
  });

  // ─── updateStatus() ─────────────────────────────────────────────────────────

  describe('updateStatus()', () => {
    it('should PUT /investments/:id/status with the status payload', () => {
      service.updateStatus(7, { status: 'APPROVED' }).subscribe();
      const req = http.expectOne(`${API}/investments/7/status`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ status: 'APPROVED' });
      req.flush(wrapped({ ...mockInvestment, status: 'APPROVED' }));
    });

    it('should return normalizeWrapped envelope with updated investment', () => {
      let result: any;
      service.updateStatus(7, { status: 'REJECTED' }).subscribe(r => result = r);
      http.expectOne(`${API}/investments/7/status`)
        .flush(wrapped({ ...mockInvestment, status: 'REJECTED' }));
      expect(result.success).toBe(true);
      expect(result.data?.status).toBe('REJECTED');
    });

    it('should support all valid status values', () => {
      const statuses: Array<'APPROVED' | 'REJECTED' | 'COMPLETED'> = ['APPROVED', 'REJECTED', 'COMPLETED'];
      statuses.forEach(status => {
        service.updateStatus(7, { status }).subscribe();
        http.expectOne(`${API}/investments/7/status`)
          .flush(wrapped({ ...mockInvestment, status }));
      });
    });
  });
});
