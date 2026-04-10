import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, MessageResponse, UserResponse } from '../../models';
import { normalizeArray, normalizePlain, normalizeError, normalizePage } from './api-normalizer';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class MessagingService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient, private auth: AuthService) {}

  /**
   * Send a message.
   * senderId is ALWAYS derived from the authenticated session — never user-controlled.
   */
  sendMessage(receiverId: number, content: string): Observable<ApiEnvelope<MessageResponse>> {
    const senderId = this.auth.userId()!;
    return this.http.post<MessageResponse>(`${this.api}/messages`, { senderId, receiverId, content }).pipe(
      map(normalizePlain),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get full conversation between current user and a partner */
  getConversation(partnerId: number, page: number = 0, size: number = 50): Observable<ApiEnvelope<MessageResponse[]>> {
    const userId = this.auth.userId()!;
    return this.http.get<any>(`${this.api}/messages/conversation/${userId}/${partnerId}?page=${page}&size=${size}`).pipe(
      map(normalizePage),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /**
   * Get distinct user IDs that have messaged with current user.
   * Returns plain number[] — not objects.
   */
  getPartnerIds(): Observable<ApiEnvelope<number[]>> {
    const userId = this.auth.userId()!;
    return this.http.get<number[]>(`${this.api}/messages/partners/${userId}`).pipe(
      map(normalizeArray),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get message by id */
  getById(id: number): Observable<ApiEnvelope<MessageResponse>> {
    return this.http.get<MessageResponse>(`${this.api}/messages/${id}`).pipe(
      map(normalizePlain),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }
}
