import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { TeamService } from '../../core/services/team.service';
import { StartupService } from '../../core/services/startup.service';
import { UserService } from '../../core/services/user.service';
import {
  TeamMemberResponse, InvitationRequest, StartupResponse, UserResponse, TeamRole
} from '../../models';

@Component({
  selector: 'app-team',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './team.html',
  styleUrl: './team.css'
})
export class TeamComponent implements OnInit {
  loading       = signal(true);
  errorMsg      = signal('');
  successMsg    = signal('');

  // Founder state
  myStartups        = signal<StartupResponse[]>([]);
  selectedStartupId = signal<number | null>(null);
  teamMembers       = signal<TeamMemberResponse[]>([]);
  removing          = signal<number | null>(null);
  showInviteForm    = signal(false);
  inviting          = signal(false);
  coFounders        = signal<UserResponse[]>([]);
  inviteForm: FormGroup;

  // CoFounder state
  myTeams = signal<TeamMemberResponse[]>([]);

  readonly teamRoles: TeamRole[] = ['CTO', 'CPO', 'MARKETING_HEAD', 'ENGINEERING_LEAD'];
  readonly roleLabels: Record<TeamRole, string> = {
    CTO: 'CTO', CPO: 'CPO', MARKETING_HEAD: 'Marketing Head', ENGINEERING_LEAD: 'Engineering Lead'
  };

  isFounder():   boolean { return this.authService.role() === 'ROLE_FOUNDER'; }
  isCoFounder(): boolean { return this.authService.role() === 'ROLE_COFOUNDER'; }

  constructor(
    private fb: FormBuilder,
    public authService: AuthService,
    private teamService: TeamService,
    private startupService: StartupService,
    private userService: UserService
  ) {
    this.inviteForm = this.fb.group({
      invitedUserId: [null, Validators.required],
      role:          ['',   Validators.required]
    });
  }

  ngOnInit(): void {
    if (this.isFounder())   this.loadFounderData();
    if (this.isCoFounder()) this.loadCoFounderData();
  }

  // ── Founder ──────────────────────────────────────────────────────────────
  loadFounderData(): void {
    this.startupService.getMyStartups().subscribe({
      next: env => {
        this.myStartups.set(env.data ?? []);
        if (env.data?.length) {
          this.selectedStartupId.set(env.data[0].id);
          this.loadTeam(env.data[0].id);
        } else {
          this.loading.set(false);
        }
      },
      error: () => this.loading.set(false)
    });
    this.userService.getUsersByRole('COFOUNDER').subscribe({
      next: env => this.coFounders.set(env.data ?? [])
    });
  }

  loadTeam(startupId: number): void {
    this.loading.set(true);
    this.teamService.getTeamMembers(startupId).subscribe({
      next: env => { this.teamMembers.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load team.'); this.loading.set(false); }
    });
  }

  onStartupChange(id: number): void {
    this.selectedStartupId.set(id);
    this.inviteForm.reset();
    this.showInviteForm.set(false);
    this.loadTeam(id);
  }

  sendInvite(): void {
    if (this.inviteForm.invalid) { this.inviteForm.markAllAsTouched(); return; }
    this.inviting.set(true);
    this.errorMsg.set('');

    const payload: InvitationRequest = {
      startupId:     this.selectedStartupId()!,
      invitedUserId: Number(this.inviteForm.value.invitedUserId),
      role:          this.inviteForm.value.role
    };

    this.teamService.sendInvitation(payload).subscribe({
      next: () => {
        this.inviting.set(false);
        this.showInviteForm.set(false);
        this.inviteForm.reset();
        this.successMsg.set('Invitation sent successfully!');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.inviting.set(false);
        this.errorMsg.set(env.error ?? 'Failed to send invitation.');
      }
    });
  }

  removeMember(memberId: number): void {
    if (!confirm('Remove this team member?')) return;
    this.removing.set(memberId);
    this.teamService.removeMember(memberId).subscribe({
      next: () => {
        this.removing.set(null);
        this.teamMembers.update(list => list.filter(m => m.id !== memberId));
        this.successMsg.set('Member removed successfully.');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.removing.set(null);
        this.errorMsg.set(env.error ?? 'Failed to remove member.');
      }
    });
  }

  // ── CoFounder ────────────────────────────────────────────────────────────
  loadCoFounderData(): void {
    this.teamService.getMyActiveRoles().subscribe({
      next: env => { this.myTeams.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load teams.'); this.loading.set(false); }
    });
  }

  availableCoFounders(): UserResponse[] {
    const memberIds = new Set(this.teamMembers().map(m => m.userId));
    return this.coFounders().filter(u => !memberIds.has(u.userId));
  }

  roleLabel(role: string): string {
    return this.roleLabels[role as TeamRole] ?? role.replace(/_/g, ' ');
  }

  roleClass(role: string): string {
    return role === 'CTO' ? 'badge-purple' : role === 'CPO' ? 'badge-info' : 'badge-gray';
  }
}
