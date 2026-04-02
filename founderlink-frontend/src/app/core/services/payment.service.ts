import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError, timer, EMPTY } from 'rxjs';
import { map, catchError, switchMap, take, retryWhen, delayWhen, scan } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  ApiEnvelope, ApiResponse,
  CreateOrderRequest, CreateOrderResponse,
  ConfirmPaymentRequest, ConfirmPaymentResponse,
  PaymentResponse
} from '../../models';
import { normalizeWrapped, normalizeError } from './api-normalizer';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_RETRIES = 5;

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /** Investor: create or reuse Razorpay order for an approved investment */
  createOrder(req: CreateOrderRequest): Observable<ApiEnvelope<CreateOrderResponse>> {
    return this.http.post<ApiResponse<CreateOrderResponse>>(`${this.api}/payments/create-order`, req).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Investor: confirm payment after successful Razorpay checkout */
  confirmPayment(req: ConfirmPaymentRequest): Observable<ApiEnvelope<ConfirmPaymentResponse>> {
    return this.http.post<ApiResponse<ConfirmPaymentResponse>>(`${this.api}/payments/confirm`, req).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get payment by payment id */
  getPayment(paymentId: number): Observable<ApiEnvelope<PaymentResponse>> {
    return this.http.get<ApiResponse<PaymentResponse>>(`${this.api}/payments/${paymentId}`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get payment by investment id — may not exist yet (async creation after approval) */
  getPaymentByInvestment(investmentId: number): Observable<ApiEnvelope<PaymentResponse>> {
    return this.http.get<ApiResponse<PaymentResponse>>(`${this.api}/payments/investment/${investmentId}`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /**
   * Poll for the payment row after investment approval.
   * Retries every 2s up to 5 times on 404.
   * Hard errors (403, 502, 503) stop polling immediately.
   * Returns the ApiEnvelope from the first successful response.
   */
  pollPaymentAvailability(investmentId: number): Observable<ApiEnvelope<PaymentResponse>> {
    let attempts = 0;

    const attempt$ = (): Observable<ApiEnvelope<PaymentResponse>> =>
      this.http.get<ApiResponse<PaymentResponse>>(`${this.api}/payments/investment/${investmentId}`).pipe(
        map(normalizeWrapped),
        catchError(err => {
          attempts++;
          if (attempts >= MAX_POLL_RETRIES || (err.status !== 404 && err.status !== 0)) {
            return throwError(() => normalizeError(err));
          }
          return timer(POLL_INTERVAL_MS).pipe(switchMap(() => attempt$()));
        })
      );

    return attempt$();
  }
}
