import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { StartupService } from './startup.service';

const API = '/api';

const mockStartup = {
  id: 1,
  name: 'EcoVent',
  description: 'Green energy startup',
  industry: 'CleanTech',
  problemStatement: 'Carbon emissions',
  solution: 'Wind farms',
  fundingGoal: 500000,
  stage: 'MVP' as const,
  founderId: 42,
  createdAt: '2025-01-01T00:00:00Z'
};

const wrappedStartup = { message: 'ok', data: mockStartup };
const wrappedNullData = { message: 'ok', data: null };

describe('StartupService', () => {
  let service: StartupService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(StartupService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── getAll() ───────────────────────────────────────────────────────────────

  describe('getAll()', () => {
    it('should GET /startup with default page=0, size=10', () => {
      service.getAll().subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('10');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should pass custom page and size', () => {
      service.getAll(2, 5).subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup`);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('5');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should return normalizePage envelope', () => {
      let result: any;
      service.getAll().subscribe(r => result = r);
      http.expectOne(r => r.url === `${API}/startup`)
        .flush({ content: [mockStartup], totalElements: 1, totalPages: 1 });
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockStartup]);
      expect(result.totalElements).toBe(1);
    });
  });

  // ─── search() ───────────────────────────────────────────────────────────────

  describe('search()', () => {
    it('should GET /startup/search with pagination params', () => {
      service.search({}).subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup/search`);
      expect(req.request.method).toBe('GET');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should include industry filter when provided', () => {
      service.search({ industry: 'FinTech' }).subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup/search`);
      expect(req.request.params.get('industry')).toBe('FinTech');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should include stage filter when provided', () => {
      service.search({ stage: 'SCALING' }).subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup/search`);
      expect(req.request.params.get('stage')).toBe('SCALING');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should include minFunding and maxFunding when provided', () => {
      service.search({ minFunding: 1000, maxFunding: 50000 }).subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup/search`);
      expect(req.request.params.get('minFunding')).toBe('1000');
      expect(req.request.params.get('maxFunding')).toBe('50000');
      req.flush({ content: [], totalElements: 0 });
    });

    it('should NOT include unset optional filters', () => {
      service.search({ industry: 'SaaS' }).subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup/search`);
      expect(req.request.params.has('stage')).toBe(false);
      expect(req.request.params.has('minFunding')).toBe(false);
      req.flush({ content: [], totalElements: 0 });
    });
  });

  // ─── getDetails() ───────────────────────────────────────────────────────────

  describe('getDetails()', () => {
    it('should GET /startup/details/:id', () => {
      service.getDetails(1).subscribe();
      const req = http.expectOne(`${API}/startup/details/1`);
      expect(req.request.method).toBe('GET');
      req.flush(wrappedStartup);
    });

    it('should return normalizeWrapped envelope with startup data', () => {
      let result: any;
      service.getDetails(1).subscribe(r => result = r);
      http.expectOne(`${API}/startup/details/1`).flush(wrappedStartup);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockStartup);
    });

    it('should return error envelope on 404', () => {
      let error: any;
      service.getDetails(999).subscribe({ error: e => error = e });
      http.expectOne(`${API}/startup/details/999`)
        .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
      expect(error.success).toBe(false);
    });
  });

  // ─── getMyStartups() ────────────────────────────────────────────────────────

  describe('getMyStartups()', () => {
    it('should GET /startup/founder with pagination', () => {
      service.getMyStartups().subscribe();
      const req = http.expectOne(r => r.url === `${API}/startup/founder`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('0');
      req.flush({ content: [mockStartup], totalElements: 1 });
    });
  });

  // ─── create() ───────────────────────────────────────────────────────────────

  describe('create()', () => {
    it('should POST /startup with the request payload', () => {
      const req = { name: 'EcoVent', description: 'Green', industry: 'CleanTech', problemStatement: 'CO2', solution: 'Wind', fundingGoal: 500000, stage: 'MVP' as const };
      service.create(req).subscribe();
      const httpReq = http.expectOne(`${API}/startup`);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(wrappedStartup);
    });

    it('should return normalizeWrapped envelope', () => {
      let result: any;
      const req = { name: 'X', description: 'Y', industry: 'Z', problemStatement: 'P', solution: 'S', fundingGoal: 1000, stage: 'IDEA' as const };
      service.create(req).subscribe(r => result = r);
      http.expectOne(`${API}/startup`).flush(wrappedStartup);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockStartup);
    });
  });

  // ─── update() ───────────────────────────────────────────────────────────────

  describe('update()', () => {
    it('should PUT /startup/:id with updated payload', () => {
      const req = { name: 'EcoVent v2', description: 'Updated', industry: 'CleanTech', problemStatement: 'CO2', solution: 'Solar', fundingGoal: 750000, stage: 'SCALING' as const };
      service.update(1, req).subscribe();
      const httpReq = http.expectOne(`${API}/startup/1`);
      expect(httpReq.request.method).toBe('PUT');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(wrappedStartup);
    });
  });

  // ─── delete() ───────────────────────────────────────────────────────────────

  describe('delete()', () => {
    it('should DELETE /startup/:id', () => {
      service.delete(1).subscribe();
      const req = http.expectOne(`${API}/startup/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(wrappedNullData);
    });

    it('should return normalizeWrapped envelope with null data', () => {
      let result: any;
      service.delete(1).subscribe(r => result = r);
      http.expectOne(`${API}/startup/1`).flush(wrappedNullData);
      expect(result.success).toBe(true);
      expect(result.data).toBeNull();
    });
  });
});
