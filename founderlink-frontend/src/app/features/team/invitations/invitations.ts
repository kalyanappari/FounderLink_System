import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { TeamService } from '../../../core/services/team.service';
import { StartupService } from '../../../core/services/startup.service';
import { InvitationResponse, InvitationStatus } from '../../../models';

@Component({
  selector: 'app-invitations',
  imports: [CommonModule],
  templateUrl: './invitations.html',
  styleUrl: './invitations.css'
})
export class InvitationsComponent implements OnInit {
  invitations = signal<InvitationResponse[]>([]);
  loading     = signal(true);
  acting      = signal<number | null>(null);
  errorMsg    = signal('');
  successMsg  = signal('');
  startupNames = signal<Map<number, string>>(new Map());

  constructor(
    public authService: AuthService,
    private teamService: TeamService,
    private startupService: StartupService
  ) {}

  ngOnInit(): void { this.loadInvitations(); }

  loadInvitations(): void {
    this.loading.set(true);
    this.teamService.getMyInvitations().subscribe({
      next: env => { this.invitations.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load invitations.'); this.loading.set(false); }
    });

    this.startupService.getAll().subscribe({
      next: env => {
        const map = new Map<number, string>();
        env.data?.forEach(s => map.set(s.id, s.name));
        this.startupNames.set(map);
      }
    });
  }

  accept(invitation: InvitationResponse): void {
    this.acting.set(invitation.id);
    this.errorMsg.set('');
    this.teamService.joinTeam({ invitationId: invitation.id }).subscribe({
      next: () => {
        this.acting.set(null);
        this.invitations.update(list =>
          list.map(i => i.id === invitation.id ? { ...i, status: 'ACCEPTED' as InvitationStatus } : i)
        );
        this.successMsg.set('You have joined the team!');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.acting.set(null);
        this.errorMsg.set(env.error ?? 'Failed to accept invitation.');
      }
    });
  }

  reject(invitation: InvitationResponse): void {
    if (!confirm('Are you sure you want to reject this invitation?')) return;
    this.acting.set(invitation.id);
    this.errorMsg.set('');
    this.teamService.rejectInvitation(invitation.id).subscribe({
      next: () => {
        this.acting.set(null);
        this.invitations.update(list =>
          list.map(i => i.id === invitation.id ? { ...i, status: 'REJECTED' as InvitationStatus } : i)
        );
        this.successMsg.set('Invitation rejected.');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.acting.set(null);
        this.errorMsg.set(env.error ?? 'Failed to reject invitation.');
      }
    });
  }

  pending():   InvitationResponse[] { return this.invitations().filter(i => i.status === 'PENDING'); }
  responded(): InvitationResponse[] { return this.invitations().filter(i => i.status !== 'PENDING'); }

  statusLabel(status: InvitationStatus): string {
    const labels: Record<InvitationStatus, string> = {
      PENDING: 'Awaiting Response', ACCEPTED: 'Accepted',
      REJECTED: 'Rejected', CANCELLED: 'Cancelled'
    };
    return labels[status] ?? status;
  }

  statusClass(status: string): string {
    return status === 'ACCEPTED'  ? 'badge-success'
         : status === 'PENDING'   ? 'badge-warning'
         : status === 'REJECTED'  ? 'badge-danger'
         : 'badge-gray';
  }

  roleLabel(role: string): string {
    const labels: Record<string, string> = {
      CTO: 'CTO', CPO: 'CPO', MARKETING_HEAD: 'Marketing Head', ENGINEERING_LEAD: 'Engineering Lead'
    };
    return labels[role] ?? role.replace(/_/g, ' ');
  }
}
