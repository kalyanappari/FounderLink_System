import { HttpErrorResponse } from '@angular/common/http';
import { ApiEnvelope, ApiResponse } from '../../models';

/** Pattern A: Wrapped { message, data } response */
export function normalizeWrapped<T>(body: ApiResponse<T>): ApiEnvelope<T> {
  return { success: true, data: body.data ?? null, error: null };
}

/** Pattern B: Plain DTO or primitive */
export function normalizePlain<T>(body: T): ApiEnvelope<T> {
  return { success: true, data: body, error: null };
}

/** Pattern C: Plain array or Paginated response fallback */
export function normalizeArray<T>(body: any): ApiEnvelope<T[]> {
  if (Array.isArray(body)) {
    return { success: true, data: body, error: null };
  } else if (body && Array.isArray(body.content)) {
    return { success: true, data: body.content, error: null };
  }
  return { success: true, data: [], error: null };
}

/** Pattern D: Spring Data Page structure */
export function normalizePage<T>(body: any): ApiEnvelope<T[]> {
  if (body && Array.isArray(body.content)) {
    return { 
        success: true, 
        data: body.content, 
        error: null,
        totalElements: body.totalElements,
        totalPages: body.totalPages,
        pageNumber: body.pageNumber,
        pageSize: body.pageSize
    };
  }
  return normalizeArray(body); // fallback
}

/** Pattern D: Empty body (204 logout) */
export function normalizeEmpty(): ApiEnvelope<null> {
  return { success: true, data: null, error: null };
}

/** Patterns E–L: Normalize any backend error to a human-readable string */
export function normalizeError(err: HttpErrorResponse): ApiEnvelope<null> {
  const body = err.error;
  let message: string;

  if (!body) {
    message = err.message || `Request failed with status ${err.status}`;
  } else if (typeof body === 'string') {
    message = body;
  } else if (typeof body === 'object') {
    // Pattern G (user-service): { message: 'VALIDATION_ERROR', error: 'human text' }
    if (body.error && typeof body.error === 'string' && !body.error.match(/^\d{3}$/)) {
      message = body.error;
    }
    // Pattern E (gateway), F (auth-service), H (generic services): { message: 'string' }
    else if (body.message && typeof body.message === 'string') {
      message = body.message;
    }
    // Pattern I (Bean Validation field map): { field: 'msg', field2: 'msg2' }
    else if (!body.status && !body.message && !body.timestamp) {
      const entries = Object.entries(body as Record<string, string>);
      message = entries.map(([k, v]) => `${k}: ${v}`).join('; ');
    }
    // Fallback
    else {
      message = body.message || body.error || err.statusText || `Request failed with status ${err.status}`;
    }
  } else {
    message = `Request failed with status ${err.status}`;
  }

  return { success: false, data: null, error: message };
}
