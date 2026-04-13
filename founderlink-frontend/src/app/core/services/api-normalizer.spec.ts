import { HttpErrorResponse } from '@angular/common/http';
import {
  normalizeWrapped,
  normalizePlain,
  normalizeArray,
  normalizePage,
  normalizeEmpty,
  normalizeError
} from './api-normalizer';

// ─── normalizeWrapped ────────────────────────────────────────────────────────
describe('normalizeWrapped', () => {
  it('should return success envelope with data', () => {
    const result = normalizeWrapped({ message: 'ok', data: { id: 1 } });
    expect(result.success).toBe(true);
    expect(result.data).toEqual({ id: 1 });
    expect(result.error).toBeNull();
  });

  it('should return null data when body.data is null', () => {
    const result = normalizeWrapped({ message: 'ok', data: null as any });
    expect(result.data).toBeNull();
  });

  it('should return null data when body.data is undefined', () => {
    const result = normalizeWrapped({ message: 'ok', data: undefined as any });
    expect(result.data).toBeNull();
  });
});

// ─── normalizePlain ──────────────────────────────────────────────────────────
describe('normalizePlain', () => {
  it('should wrap a DTO object', () => {
    const dto = { name: 'Alice', role: 'FOUNDER' };
    const result = normalizePlain(dto);
    expect(result.success).toBe(true);
    expect(result.data).toBe(dto);
    expect(result.error).toBeNull();
  });

  it('should wrap a string primitive', () => {
    const result = normalizePlain('hello');
    expect(result.data).toBe('hello');
  });

  it('should wrap a number primitive', () => {
    const result = normalizePlain(42);
    expect(result.data).toBe(42);
  });
});

// ─── normalizeArray ──────────────────────────────────────────────────────────
describe('normalizeArray', () => {
  it('should return array body directly when body is an array', () => {
    const body = [{ id: 1 }, { id: 2 }];
    const result = normalizeArray(body);
    expect(result.success).toBe(true);
    expect(result.data).toEqual(body);
    expect(result.error).toBeNull();
  });

  it('should extract content from paginated body', () => {
    const body = { content: [{ id: 3 }], totalElements: 1 };
    const result = normalizeArray(body);
    expect(result.data).toEqual([{ id: 3 }]);
  });

  it('should return empty array for unknown body shape', () => {
    const result = normalizeArray({ total: 0 });
    expect(result.data).toEqual([]);
  });

  it('should return empty array for null body', () => {
    const result = normalizeArray(null);
    expect(result.data).toEqual([]);
  });
});

// ─── normalizePage ───────────────────────────────────────────────────────────
describe('normalizePage', () => {
  it('should return empty page for null/undefined response', () => {
    const result = normalizePage(null);
    expect(result.success).toBe(true);
    expect(result.data).toEqual([]);
    expect(result.totalElements).toBe(0);
  });

  it('should extract data from res.data field', () => {
    const res = { data: [{ id: 1 }], totalElements: 1, totalPages: 1 };
    const result = normalizePage(res);
    expect(result.data).toEqual([{ id: 1 }]);
    expect(result.totalElements).toBe(1);
    expect(result.totalPages).toBe(1);
  });

  it('should extract data from res.content field when res.data is absent', () => {
    const res = { content: [{ id: 2 }], totalElements: 2 };
    const result = normalizePage(res);
    expect(result.data).toEqual([{ id: 2 }]);
  });

  it('should fall back to empty array when neither data nor content exists', () => {
    const result = normalizePage({ totalElements: 0 });
    expect(result.data).toEqual([]);
  });

  it('should resolve pageNumber from res.number alias', () => {
    const res = { content: [], number: 3, size: 10 };
    const result = normalizePage(res);
    expect(result.pageNumber).toBe(3);
    expect(result.pageSize).toBe(10);
  });

  it('should set isLast using res.last alias', () => {
    const res = { content: [], last: false };
    const result = normalizePage(res);
    expect(result.isLast).toBe(false);
  });
});

// ─── normalizeEmpty ──────────────────────────────────────────────────────────
describe('normalizeEmpty', () => {
  it('should return a success envelope with null data (204 responses)', () => {
    const result = normalizeEmpty();
    expect(result.success).toBe(true);
    expect(result.data).toBeNull();
    expect(result.error).toBeNull();
  });
});

// ─── normalizeError ──────────────────────────────────────────────────────────
describe('normalizeError', () => {
  function makeError(status: number, errorBody: any, message = ''): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: errorBody, statusText: message });
  }

  it('should use err.message when body is null/undefined', () => {
    const err = new HttpErrorResponse({ status: 503, error: null, statusText: 'Service Unavailable' });
    const result = normalizeError(err);
    expect(result.success).toBe(false);
    expect(result.error).toContain('503');
  });

  it('should return string body directly (Pattern string)', () => {
    const err = makeError(400, 'Bad request string');
    const result = normalizeError(err);
    expect(result.error).toBe('Bad request string');
  });

  it('should extract body.error when it is a non-HTTP-status string (Pattern G)', () => {
    const err = makeError(422, { message: 'VALIDATION_ERROR', error: 'Email already exists' });
    const result = normalizeError(err);
    expect(result.error).toBe('Email already exists');
  });

  it('should NOT use body.error when it looks like an HTTP status code (3-digit)', () => {
    const err = makeError(400, { message: 'Bad Request', error: '400' });
    const result = normalizeError(err);
    // Falls through to message
    expect(result.error).toBe('Bad Request');
  });

  it('should extract body.message for generic services (Pattern E/F/H)', () => {
    const err = makeError(401, { message: 'Unauthorized access' });
    const result = normalizeError(err);
    expect(result.error).toBe('Unauthorized access');
  });

  it('should join field-level validation map (Pattern I — Bean Validation)', () => {
    const err = makeError(400, { email: 'must not be blank', name: 'too short' });
    const result = normalizeError(err);
    expect(result.error).toContain('email: must not be blank');
    expect(result.error).toContain('name: too short');
  });

  it('should fall back to statusText when everything else is missing', () => {
    const err = new HttpErrorResponse({ status: 502, error: { status: 502 }, statusText: 'Bad Gateway' });
    const result = normalizeError(err);
    expect(result.error).toContain('Bad Gateway');
  });

  it('should return success: false in all error paths', () => {
    const err = makeError(500, { message: 'Internal Server Error' });
    expect(normalizeError(err).success).toBe(false);
  });

  it('should return data: null in all error paths', () => {
    const err = makeError(500, 'crash');
    expect(normalizeError(err).data).toBeNull();
  });
});
