import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, NotificationResponse } from '../../models';
import { normalizeArray, normalizePlain, normalizeError, normalizePage } from './api-normalizer';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient, private auth: AuthService) {}

  /** Get all notifications for the logged-in user */
  getMyNotifications(page: number = 0, size: number = 10): Observable<ApiEnvelope<NotificationResponse[]>> {
    const userId = this.auth.userId()!;
    return this.http.get<any>(`${this.api}/notifications/${userId}?page=${page}&size=${size}`).pipe(
      map(res => normalizePage<NotificationResponse>(res)),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get unread notifications for the logged-in user */
  getMyUnreadNotifications(page: number = 0, size: number = 10): Observable<ApiEnvelope<NotificationResponse[]>> {
    const userId = this.auth.userId()!;
    return this.http.get<any>(`${this.api}/notifications/${userId}/unread?page=${page}&size=${size}`).pipe(
      map(res => normalizePage<NotificationResponse>(res)),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get extremely fast raw integer count for unread badges */
  getUnreadCount(): Observable<ApiEnvelope<number>> {
    const userId = this.auth.userId()!;
    return this.http.get<number>(`${this.api}/notifications/${userId}/unread/count`).pipe(
      map(normalizePlain),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Mark a notification as read */
  markAsRead(id: number): Observable<ApiEnvelope<NotificationResponse>> {
    return this.http.put<NotificationResponse>(`${this.api}/notifications/${id}/read`, {}).pipe(
      map(normalizePlain),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }
}
