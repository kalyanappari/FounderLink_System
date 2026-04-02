import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, catchError, filter, switchMap, take, throwError, Observable } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';
import { AuthResponse } from '../../models';

// Shared state to prevent concurrent refresh races
let isRefreshing = false;
const refreshSubject = new BehaviorSubject<AuthResponse | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.token();

  // Public endpoints that must never have auth headers or trigger refresh
  const isPublicEndpoint =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/register') ||
    req.url.includes('/auth/forgot-password') ||
    req.url.includes('/auth/reset-password') ||
    req.url.includes('/auth/refresh');   // ← fixed: prevents infinite refresh loop

  const authReq = token && !isPublicEndpoint
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isPublicEndpoint) {
        return handle401(req, next, authService, router);
      }
      return throwError(() => error);
    })
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService,
  router: Router
): Observable<any> {
  if (isRefreshing) {
    // Another refresh is already in-flight — wait for it to complete
    return refreshSubject.pipe(
      filter(res => res !== null),
      take(1),
      switchMap(res => {
        const retryReq = req.clone({
          setHeaders: { Authorization: `Bearer ${res!.token}` }
        });
        return next(retryReq);
      })
    );
  }

  isRefreshing = true;
  refreshSubject.next(null);

  return authService.refresh().pipe(
    switchMap(res => {
      isRefreshing = false;
      refreshSubject.next(res);
      const retryReq = req.clone({
        setHeaders: { Authorization: `Bearer ${res.token}` }
      });
      return next(retryReq);
    }),
    catchError(err => {
      isRefreshing = false;
      refreshSubject.next(null);
      router.navigate(['/auth/login']);
      return throwError(() => err);
    })
  );
}
