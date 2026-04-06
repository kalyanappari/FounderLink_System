import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, NotificationResponse } from '../../models';
import { normalizeArray, normalizePlain, normalizeError } from './api-normalizer';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient, private auth: AuthService) {}

  /** Get all notifications for the logged-in user (plain array response) */
  getMyNotifications(): Observable<ApiEnvelope<NotificationResponse[]>> {
    const userId = this.auth.userId()!;
    return this.http.get<NotificationResponse[]>(`${this.api}/notifications/${userId}`).pipe(
      map(normalizeArray),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get unread notifications for the logged-in user (plain array response) */
  getMyUnreadNotifications(): Observable<ApiEnvelope<NotificationResponse[]>> {
    const userId = this.auth.userId()!;
    return this.http.get<NotificationResponse[]>(`${this.api}/notifications/${userId}/unread`).pipe(
      map(normalizeArray),
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
