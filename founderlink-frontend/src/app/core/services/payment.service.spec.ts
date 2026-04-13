import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { PaymentService } from './payment.service';

const API = '/api';

const mockPayment = {
  id: 20,
  investmentId: 7,
  investorId: 99,
  startupId: 1,
  founderId: 42,
  amount: 25000,
  status: 'SUCCESS' as const,
  externalPaymentId: 'pay_abc123',
  failureReason: null,
  createdAt: '2025-06-01T00:00:00Z',
  updatedAt: '2025-06-01T01:00:00Z'
};

const mockOrderResponse = {
  orderId: 'order_xyz',
  amount: 25000,
  currency: 'INR',
  investmentId: 7
};

const mockConfirmResponse = {
  status: 'SUCCESS',
  investmentId: 7
};

const wrapped = (data: any) => ({ message: 'ok', data });

describe('PaymentService', () => {
  let service: PaymentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(PaymentService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── createOrder() ──────────────────────────────────────────────────────────

  describe('createOrder()', () => {
    it('should POST /payments/create-order with the investment id payload', () => {
      service.createOrder({ investmentId: 7 }).subscribe();
      const req = http.expectOne(`${API}/payments/create-order`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ investmentId: 7 });
      req.flush(wrapped(mockOrderResponse));
    });

    it('should return normalizeWrapped envelope with order data', () => {
      let result: any;
      service.createOrder({ investmentId: 7 }).subscribe(r => result = r);
      http.expectOne(`${API}/payments/create-order`).flush(wrapped(mockOrderResponse));
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockOrderResponse);
      expect(result.error).toBeNull();
    });

    it('should return error envelope on HTTP failure', () => {
      let error: any;
      service.createOrder({ investmentId: 7 }).subscribe({ error: e => error = e });
      http.expectOne(`${API}/payments/create-order`)
        .flush({ message: 'Investment not approved' }, { status: 400, statusText: 'Bad Request' });
      expect(error.success).toBe(false);
      expect(error.error).toBeTruthy();
    });
  });

  // ─── confirmPayment() ───────────────────────────────────────────────────────

  describe('confirmPayment()', () => {
    it('should POST /payments/confirm with the Razorpay signature payload', () => {
      const req = {
        razorpayOrderId: 'order_xyz',
        razorpayPaymentId: 'pay_abc123',
        razorpaySignature: 'sig_def456'
      };
      service.confirmPayment(req).subscribe();
      const httpReq = http.expectOne(`${API}/payments/confirm`);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(wrapped(mockConfirmResponse));
    });

    it('should return normalizeWrapped envelope with confirmation data', () => {
      let result: any;
      service.confirmPayment({
        razorpayOrderId: 'order_xyz',
        razorpayPaymentId: 'pay_abc123',
        razorpaySignature: 'sig_def456'
      }).subscribe(r => result = r);
      http.expectOne(`${API}/payments/confirm`).flush(wrapped(mockConfirmResponse));
      expect(result.success).toBe(true);
      expect(result.data?.status).toBe('SUCCESS');
    });
  });

  // ─── getPayment() ───────────────────────────────────────────────────────────

  describe('getPayment()', () => {
    it('should GET /payments/:paymentId', () => {
      service.getPayment(20).subscribe();
      const req = http.expectOne(`${API}/payments/20`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped(mockPayment));
    });

    it('should return normalizeWrapped envelope with payment data', () => {
      let result: any;
      service.getPayment(20).subscribe(r => result = r);
      http.expectOne(`${API}/payments/20`).flush(wrapped(mockPayment));
      expect(result.success).toBe(true);
      expect(result.data?.id).toBe(20);
      expect(result.data?.status).toBe('SUCCESS');
    });
  });

  // ─── getPaymentByInvestment() ───────────────────────────────────────────────

  describe('getPaymentByInvestment()', () => {
    it('should GET /payments/investment/:investmentId', () => {
      service.getPaymentByInvestment(7).subscribe();
      const req = http.expectOne(`${API}/payments/investment/7`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped(mockPayment));
    });

    it('should return normalizeWrapped envelope with payment data', () => {
      let result: any;
      service.getPaymentByInvestment(7).subscribe(r => result = r);
      http.expectOne(`${API}/payments/investment/7`).flush(wrapped(mockPayment));
      expect(result.success).toBe(true);
      expect(result.data?.investmentId).toBe(7);
    });

    it('should return error envelope when payment not found', () => {
      let error: any;
      service.getPaymentByInvestment(999).subscribe({ error: e => error = e });
      http.expectOne(`${API}/payments/investment/999`)
        .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
      expect(error.success).toBe(false);
    });
  });

  // ─── pollPaymentAvailability() — first attempt success ──────────────────────

  describe('pollPaymentAvailability()', () => {
    it('should GET /payments/investment/:investmentId on the first attempt', () => {
      service.pollPaymentAvailability(7).subscribe();
      const req = http.expectOne(`${API}/payments/investment/7`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped(mockPayment));
    });

    it('should return the normalizeWrapped envelope on success', () => {
      let result: any;
      service.pollPaymentAvailability(7).subscribe(r => result = r);
      http.expectOne(`${API}/payments/investment/7`).flush(wrapped(mockPayment));
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockPayment);
    });

    it('should propagate error envelope on hard errors (non-404)', () => {
      let error: any;
      service.pollPaymentAvailability(7).subscribe({ error: e => error = e });
      // Hard 403 error — should stop immediately, no retry
      http.expectOne(`${API}/payments/investment/7`)
        .flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
      expect(error.success).toBe(false);
      http.verify(); // ensures no retry request was made
    });

    it('should retry on 404 until success', () => {
      vi.useFakeTimers();
      let result: any;
      service.pollPaymentAvailability(7).subscribe(r => result = r);

      // Attempt 1: 404
      const req1 = http.expectOne(`${API}/payments/investment/7`);
      req1.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
      
      // Fast forward
      vi.advanceTimersByTime(2000);
      
      // Attempt 2: Success
      const req2 = http.expectOne(`${API}/payments/investment/7`);
      req2.flush(wrapped(mockPayment));

      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockPayment);
      vi.useRealTimers();
    });

    it('should fail after max retries on 404', () => {
      vi.useFakeTimers();
      let error: any;
      service.pollPaymentAvailability(7).subscribe({ error: e => error = e });

      for (let i = 0; i < 5; i++) {
        const req = http.expectOne(`${API}/payments/investment/7`);
        req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
        vi.advanceTimersByTime(2000);
      }

      expect(error.success).toBe(false);
      expect(error.error).toContain('Not found');
      vi.useRealTimers();
    });
  });

  describe('Systematic Error Handling', () => {
    it('should handle error in confirmPayment', () => {
      service.confirmPayment({} as any).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/payments/confirm`).flush({}, { status: 500, statusText: 'Error' });
    });

    it('should handle error in getPayment', () => {
      service.getPayment(20).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/payments/20`).flush({}, { status: 404, statusText: 'Not Found' });
    });
  });
});
