import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  ApiEnvelope, ApiResponse,
  InvitationRequest, InvitationResponse,
  JoinTeamRequest, TeamMemberResponse
} from '../../models';
import { normalizeWrapped, normalizeError } from './api-normalizer';

@Injectable({ providedIn: 'root' })
export class TeamService {
  private readonly api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /** Founder: send invitation */
  sendInvitation(req: InvitationRequest): Observable<ApiEnvelope<InvitationResponse>> {
    return this.http.post<ApiResponse<InvitationResponse>>(`${this.api}/teams/invite`, req).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Founder: cancel pending invitation */
  cancelInvitation(id: number): Observable<ApiEnvelope<InvitationResponse>> {
    return this.http.put<ApiResponse<InvitationResponse>>(`${this.api}/teams/invitations/${id}/cancel`, {}).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** CoFounder: reject pending invitation */
  rejectInvitation(id: number): Observable<ApiEnvelope<InvitationResponse>> {
    return this.http.put<ApiResponse<InvitationResponse>>(`${this.api}/teams/invitations/${id}/reject`, {}).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** CoFounder: accept invitation and join team */
  joinTeam(req: JoinTeamRequest): Observable<ApiEnvelope<TeamMemberResponse>> {
    return this.http.post<ApiResponse<TeamMemberResponse>>(`${this.api}/teams/join`, req).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** CoFounder: get own pending invitations */
  getMyInvitations(): Observable<ApiEnvelope<InvitationResponse[]>> {
    return this.http.get<ApiResponse<InvitationResponse[]>>(`${this.api}/teams/invitations/user`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Founder: get invitations for a startup */
  getStartupInvitations(startupId: number): Observable<ApiEnvelope<InvitationResponse[]>> {
    return this.http.get<ApiResponse<InvitationResponse[]>>(`${this.api}/teams/invitations/startup/${startupId}`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Get active team members for a startup */
  getTeamMembers(startupId: number): Observable<ApiEnvelope<TeamMemberResponse[]>> {
    return this.http.get<ApiResponse<TeamMemberResponse[]>>(`${this.api}/teams/startup/${startupId}`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** Founder: remove a team member */
  removeMember(teamMemberId: number): Observable<ApiEnvelope<null>> {
    return this.http.delete<ApiResponse<null>>(`${this.api}/teams/${teamMemberId}`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** CoFounder / Admin: get active roles for current user */
  getMyActiveRoles(): Observable<ApiEnvelope<TeamMemberResponse[]>> {
    return this.http.get<ApiResponse<TeamMemberResponse[]>>(`${this.api}/teams/member/active`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }

  /** CoFounder / Admin: get full member history for current user */
  getMemberHistory(): Observable<ApiEnvelope<TeamMemberResponse[]>> {
    return this.http.get<ApiResponse<TeamMemberResponse[]>>(`${this.api}/teams/member/history`).pipe(
      map(normalizeWrapped),
      catchError(err => throwError(() => normalizeError(err)))
    );
  }
}
