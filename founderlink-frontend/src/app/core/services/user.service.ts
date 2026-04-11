import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, UserResponse, UserUpdateRequest } from '../../models';
import { normalizeArray, normalizePlain, normalizeError, normalizePage } from './api-normalizer';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient, private auth: AuthService) {}

  /** Get user by id */
  getUser(id: number): Observable<ApiEnvelope<UserResponse>> {
    return this.http.get<UserResponse>(`${this.api}/users/${id}`).pipe(
      map(normalizePlain),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Update current user's profile — always uses session user id */
  updateMyProfile(payload: UserUpdateRequest): Observable<ApiEnvelope<UserResponse>> {
    const userId = this.auth.userId()!;
    return this.http.put<UserResponse>(`${this.api}/users/${userId}`, payload).pipe(
      map(normalizePlain),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get all users paged with search */
  getAllUsers(page: number = 0, size: number = 10, search?: string): Observable<ApiEnvelope<UserResponse[]>> {
    let params = `?page=${page}&size=${size}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    return this.http.get<any>(`${this.api}/users${params}`).pipe(
      map(res => normalizePage<UserResponse>(res)),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  getUsersByRole(role: string, page: number = 0, size: number = 10, search?: string): Observable<ApiEnvelope<UserResponse[]>> {
    let params = `?page=${page}&size=${size}`;
    if (search) params += `&search=${encodeURIComponent(search)}`;
    return this.http.get<any>(`${this.api}/users/role/${role}${params}`).pipe(
      map(res => normalizePage<UserResponse>(res)),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get aggregated public stats for landing page */
  getPublicStats(): Observable<{founders: number, investors: number, cofounders: number}> {
    return this.http.get<{founders: number, investors: number, cofounders: number}>(`${this.api}/users/public/stats`);
  }
}
