import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiEnvelope, UserResponse, UserUpdateRequest } from '../../models';
import { normalizeArray, normalizePlain, normalizeError } from './api-normalizer';
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

  /** Get all users (plain array response) */
  getAllUsers(): Observable<ApiEnvelope<UserResponse[]>> {
    return this.http.get<UserResponse[]>(`${this.api}/users`).pipe(
      map(normalizeArray),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get users by role (plain array response) */
  getUsersByRole(role: string): Observable<ApiEnvelope<UserResponse[]>> {
    return this.http.get<UserResponse[]>(`${this.api}/users/role/${role}`).pipe(
      map(normalizeArray),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }
}
