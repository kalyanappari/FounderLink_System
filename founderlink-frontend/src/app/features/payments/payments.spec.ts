import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PaymentsComponent } from './payments';
import { AuthService } from '../../core/services/auth.service';
import { PaymentService } from '../../core/services/payment.service';
import { InvestmentService } from '../../core/services/investment.service';
import { StartupService } from '../../core/services/startup.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Component, Input, Output, EventEmitter } from '@angular/core';


describe('PaymentsComponent', () => {
  let component: PaymentsComponent;
  let fixture: ComponentFixture<PaymentsComponent>;
  let paymentServiceSpy: any;
  let investmentServiceSpy: any;
  let startupServiceSpy: any;
  let authServiceSpy: any;

  beforeEach(async () => {
    paymentServiceSpy = {
      getPaymentByInvestment: vi.fn(),
      pollPaymentAvailability: vi.fn(),
      createOrder: vi.fn(),
      confirmPayment: vi.fn()
    };
    investmentServiceSpy = {
      getMyPortfolio: vi.fn()
    };
    startupServiceSpy = {
      getAll: vi.fn()
    };
    authServiceSpy = {
      email: vi.fn()
    };

  });

  beforeEach(async () => {
    // Standard TestBed
    await TestBed.configureTestingModule({
      imports: [PaymentsComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: PaymentService, useValue: paymentServiceSpy },
        { provide: InvestmentService, useValue: investmentServiceSpy },
        { provide: StartupService, useValue: startupServiceSpy }
      ]
    }).compileComponents();
  });
  describe('Loading and Mapping logic', () => {
    it('should load initial data and map investments correctly', () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [{ id: 5, name: 'Tesla' }] }));
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({
        data: [
          { id: 10, status: 'PENDING' }, // Ignored by filter
          { id: 11, status: 'APPROVED' }, // Will be mapped and fetched
          { id: 12, status: 'PAYMENT_FAILED' } // Will be mapped and fetched
        ]
      }));
      paymentServiceSpy.getPaymentByInvestment.mockReturnValue(of({ data: { status: 'PENDING' } })); // Fetches successful

      fixture = TestBed.createComponent(PaymentsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges(); // calls ngOnInit

      expect(startupServiceSpy.getAll).toHaveBeenCalled();
      expect(component.startupNames().get(5)).toBe('Tesla');

      const items = component.items();
      expect(items.length).toBe(2);
      expect(items[0].investment.id).toBe(11);
      expect(items[0].payment?.status).toBe('PENDING');
      expect(items[0].paymentLoading).toBe(false);
    });

    it('should correctly handle backend payment polling fallback logic when payment record does not exist', () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({ data: [{ id: 7, status: 'APPROVED' }] }));
      
      // Get fails
      paymentServiceSpy.getPaymentByInvestment.mockReturnValue(throwError(() => new Error()));
      // Polling recovers
      paymentServiceSpy.pollPaymentAvailability.mockReturnValue(of({ data: { status: 'INITIATED' } }));

      fixture = TestBed.createComponent(PaymentsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.items();
      expect(items[0].paymentError).toBe('');
      expect(items[0].payment?.status).toBe('INITIATED');
      expect(items[0].paymentLoading).toBe(false);
    });

    it('should handle ultimate failure from polling fallback', () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({ data: [{ id: 7, status: 'APPROVED' }] }));
      
      paymentServiceSpy.getPaymentByInvestment.mockReturnValue(throwError(() => new Error()));
      paymentServiceSpy.pollPaymentAvailability.mockReturnValue(throwError(() => new Error())); // Fails too

      fixture = TestBed.createComponent(PaymentsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.items();
      expect(items[0].paymentError).toContain('setup is taking longer');
      expect(items[0].paymentLoading).toBe(false);
    });
    
    it('should not poll if investment is not APPROVED and skip to error', () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({ data: [{ id: 7, status: 'COMPLETED' }] }));
      paymentServiceSpy.getPaymentByInvestment.mockReturnValue(throwError(() => new Error()));

      fixture = TestBed.createComponent(PaymentsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(paymentServiceSpy.pollPaymentAvailability).not.toHaveBeenCalled();
      expect(component.items()[0].paymentError).toBe('Payment record not found.');
    });
  });

  describe('Payment operations', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(PaymentsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
      (window as any).Razorpay = undefined;
    });

    it('should abort pay initiation if another processing is active', () => {
      component.processingId = 99;
      component.startPayment({} as any);
      expect(paymentServiceSpy.createOrder).not.toHaveBeenCalled();
    });

    it('should handle order creation failure', () => {
      paymentServiceSpy.createOrder.mockReturnValue(throwError(() => ({ error: 'Error Order' })));
      component.startPayment({ investment: { id: 10 } } as any);
      
      expect(component.processingId).toBeNull();
      expect(component.errorMsg()).toBe('Error Order');
    });

    it('should alert if Razorpay SDK lacks when order works', () => {
      paymentServiceSpy.createOrder.mockReturnValue(of({ data: { amount: 100 } }));
      (window as any).Razorpay = undefined;
      
      component.startPayment({ investment: { id: 10 } } as any);
      expect(component.errorMsg()).toContain('Razorpay SDK not loaded');
    });

    it('should instantiate razorpay and handle success confirmation callback', () => {
      authServiceSpy.email.mockReturnValue('test@test.com');
      paymentServiceSpy.createOrder.mockReturnValue(of({ data: { amount: 100, currency: 'INR', orderId: 'ord_123' } }));
      const openSpy = vi.fn();
      
      // Mock exactly how it operates
      let passedHandler: any;
      function MockRazorpay(this: any, options: any) {
        passedHandler = options.handler;
        this.open = openSpy;
      }
      (window as any).Razorpay = MockRazorpay;

      component.startPayment({ investment: { id: 10 } } as any);

      expect(openSpy).toHaveBeenCalled();
      expect(component.processingId).toBeNull();
      
      // Simulating user returning after completing payment
      paymentServiceSpy.confirmPayment.mockReturnValue(of({}));
      passedHandler({ razorpay_order_id: 'ord_123', razorpay_payment_id: 'pay_123', razorpay_signature: 'sig' });
      
      expect(paymentServiceSpy.confirmPayment).toHaveBeenCalledWith(expect.objectContaining({ razorpayPaymentId: 'pay_123' }));
      expect(component.successMsg()).toContain('Payment confirmed successfully');
      
      // Time advances cause refresh
      investmentServiceSpy.getMyPortfolio.mockClear();
      vi.advanceTimersByTime(2500);
      expect(investmentServiceSpy.getMyPortfolio).toHaveBeenCalled();
    });
  });

  describe('UI Utilities', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(PaymentsComponent);
      component = fixture.componentInstance;
    });

    it('should format currency accurately', () => {
      expect(component.formatCurrency(500)).toContain('500');
    });

    it('should map styles based on payment status', () => {
      expect(component.paymentStatusClass('SUCCESS')).toBe('badge-success');
      expect(component.paymentStatusClass('FAILED')).toBe('badge-danger');
      expect(component.paymentStatusClass(undefined)).toBe('badge-gray');
    });

    it('should evaluate canPay conditionals', () => {
      component.processingId = 5;
      expect(component.canPay({} as any)).toBe(false);

      component.processingId = null;
      expect(component.canPay({ investment: { status: 'COMPLETED' } } as any)).toBe(false);
      
      // Should work for approved + initiated
      expect(component.canPay({ investment: { status: 'APPROVED' }, payment: { status: 'INITIATED' } } as any)).toBe(true);
    });

    it('should assign correct button labels', () => {
      expect(component.payButtonLabel({ payment: { status: 'INITIATED' } } as any)).toBe('Resume Payment');
      expect(component.payButtonLabel({ payment: { status: 'FAILED' } } as any)).toBe('Retry Payment');
      expect(component.payButtonLabel({ payment: { status: 'PENDING' } } as any)).toBe('Pay Now');
    });
  });
});
