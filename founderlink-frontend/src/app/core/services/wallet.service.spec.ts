import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { WalletService } from './wallet.service';

const API = '/api';

const mockWallet = {
  id: 1,
  startupId: 10,
  balance: 75000,
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-06-01T00:00:00Z'
};

const wrapped = (data: any) => ({ message: 'ok', data });

describe('WalletService', () => {
  let service: WalletService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(WalletService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── getWallet() ────────────────────────────────────────────────────────────

  describe('getWallet()', () => {
    it('should GET /wallets/:startupId', () => {
      service.getWallet(10).subscribe();
      const req = http.expectOne(`${API}/wallets/10`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped(mockWallet));
    });

    it('should return normalizeWrapped envelope with wallet data', () => {
      let result: any;
      service.getWallet(10).subscribe(r => result = r);
      http.expectOne(`${API}/wallets/10`).flush(wrapped(mockWallet));
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockWallet);
      expect(result.error).toBeNull();
    });

    it('should return error envelope on HTTP failure', () => {
      let error: any;
      service.getWallet(999).subscribe({ error: e => error = e });
      http.expectOne(`${API}/wallets/999`)
        .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
      expect(error.success).toBe(false);
      expect(error.error).toBeTruthy();
    });
  });

  // ─── createWallet() ─────────────────────────────────────────────────────────

  describe('createWallet()', () => {
    it('should POST /wallets/:startupId with empty body', () => {
      service.createWallet(10).subscribe();
      const req = http.expectOne(`${API}/wallets/10`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(wrapped(mockWallet));
    });

    it('should return normalizeWrapped envelope with wallet data (idempotent create)', () => {
      let result: any;
      service.createWallet(10).subscribe(r => result = r);
      http.expectOne(`${API}/wallets/10`).flush(wrapped(mockWallet));
      expect(result.success).toBe(true);
      expect(result.data?.startupId).toBe(10);
      expect(result.data?.balance).toBe(75000);
    });

    it('should return error envelope on HTTP failure', () => {
      let error: any;
      service.createWallet(10).subscribe({ error: e => error = e });
      http.expectOne(`${API}/wallets/10`)
        .flush({ message: 'Conflict' }, { status: 409, statusText: 'Conflict' });
      expect(error.success).toBe(false);
    });
  });
});
