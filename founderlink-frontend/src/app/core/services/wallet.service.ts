import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, ApiResponse, WalletResponse } from '../../models';
import { normalizeWrapped, normalizeError } from './api-normalizer';

@Injectable({ providedIn: 'root' })
export class WalletService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /** Get wallet balance for a startup (wallet is funded automatically by payments) */
  getWallet(startupId: number): Observable<ApiEnvelope<WalletResponse>> {
    return this.http.get<ApiResponse<WalletResponse>>(`${this.api}/wallets/${startupId}`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Create or return existing wallet for a startup (idempotent) */
  createWallet(startupId: number): Observable<ApiEnvelope<WalletResponse>> {
    return this.http.post<ApiResponse<WalletResponse>>(`${this.api}/wallets/${startupId}`, {}).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }
}
