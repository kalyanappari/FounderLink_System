import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { TeamService } from './team.service';

const API = '/api';

const mockInvitation = {
  id: 10,
  startupId: 1,
  founderId: 42,
  invitedUserId: 99,
  role: 'CTO' as const,
  status: 'PENDING' as const,
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: null
};

const mockMember = {
  id: 5,
  startupId: 1,
  userId: 99,
  role: 'CTO' as const,
  isActive: true,
  joinedAt: '2025-01-02T00:00:00Z',
  leftAt: null
};

const wrapped = (data: any) => ({ message: 'ok', data });

describe('TeamService', () => {
  let service: TeamService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(TeamService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ─── sendInvitation() ───────────────────────────────────────────────────────

  describe('sendInvitation()', () => {
    it('should POST /teams/invite with the invitation payload', () => {
      const req = { startupId: 1, invitedUserId: 99, role: 'CTO' as const };
      service.sendInvitation(req).subscribe();
      const httpReq = http.expectOne(`${API}/teams/invite`);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(wrapped(mockInvitation));
    });

    it('should return normalizeWrapped envelope with invitation data', () => {
      let result: any;
      service.sendInvitation({ startupId: 1, invitedUserId: 99, role: 'CTO' }).subscribe(r => result = r);
      http.expectOne(`${API}/teams/invite`).flush(wrapped(mockInvitation));
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockInvitation);
    });
  });

  // ─── cancelInvitation() ─────────────────────────────────────────────────────

  describe('cancelInvitation()', () => {
    it('should PUT /teams/invitations/:id/cancel with empty body', () => {
      service.cancelInvitation(10).subscribe();
      const req = http.expectOne(`${API}/teams/invitations/10/cancel`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({});
      req.flush(wrapped({ ...mockInvitation, status: 'CANCELLED' }));
    });
  });

  // ─── rejectInvitation() ─────────────────────────────────────────────────────

  describe('rejectInvitation()', () => {
    it('should PUT /teams/invitations/:id/reject with empty body', () => {
      service.rejectInvitation(10).subscribe();
      const req = http.expectOne(`${API}/teams/invitations/10/reject`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({});
      req.flush(wrapped({ ...mockInvitation, status: 'REJECTED' }));
    });

    it('should return normalizeWrapped envelope with rejected invitation', () => {
      let result: any;
      service.rejectInvitation(10).subscribe(r => result = r);
      http.expectOne(`${API}/teams/invitations/10/reject`)
        .flush(wrapped({ ...mockInvitation, status: 'REJECTED' }));
      expect(result.data?.status).toBe('REJECTED');
    });
  });

  // ─── joinTeam() ─────────────────────────────────────────────────────────────

  describe('joinTeam()', () => {
    it('should POST /teams/join with the join request', () => {
      service.joinTeam({ invitationId: 10 }).subscribe();
      const req = http.expectOne(`${API}/teams/join`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ invitationId: 10 });
      req.flush(wrapped(mockMember));
    });

    it('should return normalizeWrapped envelope with team member data', () => {
      let result: any;
      service.joinTeam({ invitationId: 10 }).subscribe(r => result = r);
      http.expectOne(`${API}/teams/join`).flush(wrapped(mockMember));
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockMember);
    });
  });

  // ─── getMyInvitations() ─────────────────────────────────────────────────────

  describe('getMyInvitations()', () => {
    it('should GET /teams/invitations/user', () => {
      service.getMyInvitations().subscribe();
      const req = http.expectOne(`${API}/teams/invitations/user`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockInvitation]));
    });

    it('should return the invitation list in the envelope', () => {
      let result: any;
      service.getMyInvitations().subscribe(r => result = r);
      http.expectOne(`${API}/teams/invitations/user`).flush(wrapped([mockInvitation]));
      expect(result.data).toEqual([mockInvitation]);
    });
  });

  // ─── getStartupInvitations() ────────────────────────────────────────────────

  describe('getStartupInvitations()', () => {
    it('should GET /teams/invitations/startup/:startupId', () => {
      service.getStartupInvitations(1).subscribe();
      const req = http.expectOne(`${API}/teams/invitations/startup/1`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockInvitation]));
    });
  });

  // ─── getTeamMembers() ───────────────────────────────────────────────────────

  describe('getTeamMembers()', () => {
    it('should GET /teams/startup/:startupId', () => {
      service.getTeamMembers(1).subscribe();
      const req = http.expectOne(`${API}/teams/startup/1`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockMember]));
    });

    it('should return normalizWrapped envelope with member list', () => {
      let result: any;
      service.getTeamMembers(1).subscribe(r => result = r);
      http.expectOne(`${API}/teams/startup/1`).flush(wrapped([mockMember]));
      expect(result.success).toBe(true);
      expect(result.data).toEqual([mockMember]);
    });
  });

  // ─── removeMember() ─────────────────────────────────────────────────────────

  describe('removeMember()', () => {
    it('should DELETE /teams/:teamMemberId', () => {
      service.removeMember(5).subscribe();
      const req = http.expectOne(`${API}/teams/5`);
      expect(req.request.method).toBe('DELETE');
      req.flush({ message: 'ok', data: null });
    });
  });

  // ─── getMyActiveRoles() ─────────────────────────────────────────────────────

  describe('getMyActiveRoles()', () => {
    it('should GET /teams/member/active', () => {
      service.getMyActiveRoles().subscribe();
      const req = http.expectOne(`${API}/teams/member/active`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockMember]));
    });
  });

  // ─── getMemberHistory() ─────────────────────────────────────────────────────

  describe('getMemberHistory()', () => {
    it('should GET /teams/member/history', () => {
      service.getMemberHistory().subscribe();
      const req = http.expectOne(`${API}/teams/member/history`);
      expect(req.request.method).toBe('GET');
      req.flush(wrapped([mockMember]));
    });

    it('should return normalizeWrapped envelope with history list', () => {
      let result: any;
      service.getMemberHistory().subscribe(r => result = r);
      http.expectOne(`${API}/teams/member/history`).flush(wrapped([mockMember]));
      expect(result.success).toBe(true);
      expect(Array.isArray(result.data)).toBe(true);
    });
  });

  // ─── Error handling (systematic) ───────────────────────────────────────────

  describe('Systematic Error Handling', () => {
    const errorResponse = { message: 'Forbidden' };
    const errorStatus = { status: 403, statusText: 'Forbidden' };

    it('should handle error in sendInvitation', () => {
      service.sendInvitation({ startupId: 1, invitedUserId: 1, role: 'CTO' })
        .subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/invite`).flush(errorResponse, errorStatus);
    });

    it('should handle error in cancelInvitation', () => {
      service.cancelInvitation(1).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/invitations/1/cancel`).flush(errorResponse, errorStatus);
    });

    it('should handle error in rejectInvitation', () => {
      service.rejectInvitation(1).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/invitations/1/reject`).flush(errorResponse, errorStatus);
    });

    it('should handle error in joinTeam', () => {
      service.joinTeam({ invitationId: 1 }).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/join`).flush(errorResponse, errorStatus);
    });

    it('should handle error in getMyInvitations', () => {
      service.getMyInvitations().subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/invitations/user`).flush(errorResponse, errorStatus);
    });

    it('should handle error in getStartupInvitations', () => {
      service.getStartupInvitations(1).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/invitations/startup/1`).flush(errorResponse, errorStatus);
    });

    it('should handle error in getTeamMembers', () => {
      service.getTeamMembers(1).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/startup/1`).flush(errorResponse, errorStatus);
    });

    it('should handle error in removeMember', () => {
      service.removeMember(1).subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/1`).flush(errorResponse, errorStatus);
    });

    it('should handle error in getMyActiveRoles', () => {
      service.getMyActiveRoles().subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/member/active`).flush(errorResponse, errorStatus);
    });

    it('should handle error in getMemberHistory', () => {
      service.getMemberHistory().subscribe({ error: e => expect(e.success).toBe(false) });
      http.expectOne(`${API}/teams/member/history`).flush(errorResponse, errorStatus);
    });
  });
});
