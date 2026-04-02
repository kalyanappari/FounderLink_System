import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { TeamService } from '../../core/services/team.service';
import { StartupService } from '../../core/services/startup.service';
import { UserService } from '../../core/services/user.service';
import {
  TeamMemberResponse, StartupResponse, UserResponse, TeamRole, InvitationRequest
} from '../../models';

@Component({
  selector: 'app-team',
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './team.html',
  styleUrl: './team.css'
})
export class TeamComponent implements OnInit {
  loading       = signal(true);
  errorMsg      = signal('');
  successMsg    = signal('');

  // ── Founder state ─────────────────────────────────────────────────────────
  myStartups        = signal<StartupResponse[]>([]);
  selectedStartupId = signal<number | null>(null);
  teamMembers       = signal<TeamMemberResponse[]>([]);
  removing          = signal<number | null>(null);

  // User discovery panel
  showDiscovery    = signal(false);
  allUsers         = signal<UserResponse[]>([]);
  usersLoading     = signal(false);
  roleFilter       = signal<string>('COFOUNDER');
  searchQuery      = signal('');
  selectedUser     = signal<UserResponse | null>(null);
  selectedRole     = signal<TeamRole | ''>('');
  inviting         = signal(false);

  // ── CoFounder state ───────────────────────────────────────────────────────
  myTeams = signal<TeamMemberResponse[]>([]);

  readonly teamRoles: TeamRole[] = ['CTO', 'CPO', 'MARKETING_HEAD', 'ENGINEERING_LEAD'];
  readonly roleLabels: Record<TeamRole, string> = {
    CTO: 'Chief Technology Officer',
    CPO: 'Chief Product Officer',
    MARKETING_HEAD: 'Marketing Head',
    ENGINEERING_LEAD: 'Engineering Lead'
  };
  readonly roleShort: Record<TeamRole, string> = {
    CTO: 'CTO', CPO: 'CPO', MARKETING_HEAD: 'Mktg Head', ENGINEERING_LEAD: 'Eng Lead'
  };

  private hasRole(r: string): boolean {
    const stored = this.authService.role() ?? '';
    return stored === r || stored === `ROLE_${r}`;
  }
  isFounder():   boolean { return this.hasRole('FOUNDER'); }
  isCoFounder(): boolean { return this.hasRole('COFOUNDER'); }

  constructor(
    public authService: AuthService,
    private teamService: TeamService,
    private startupService: StartupService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    if (this.isFounder())   this.loadFounderData();
    if (this.isCoFounder()) this.loadCoFounderData();
  }

  // ── Founder ───────────────────────────────────────────────────────────────
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
    this.closeDiscovery();
    this.loadTeam(id);
  }

  removeMember(memberId: number): void {
    if (!confirm('Remove this team member?')) return;
    this.removing.set(memberId);
    this.teamService.removeMember(memberId).subscribe({
      next: () => {
        this.removing.set(null);
        this.teamMembers.update(list => list.filter(m => m.id !== memberId));
        this.successMsg.set('Member removed.');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.removing.set(null);
        this.errorMsg.set(env.error ?? 'Failed to remove member.');
      }
    });
  }

  // ── User Discovery ────────────────────────────────────────────────────────
  openDiscovery(): void {
    this.showDiscovery.set(true);
    this.selectedUser.set(null);
    this.selectedRole.set('');
    this.searchQuery.set('');
    this.loadUsersForRole(this.roleFilter());
  }

  closeDiscovery(): void {
    this.showDiscovery.set(false);
    this.selectedUser.set(null);
    this.selectedRole.set('');
  }

  loadUsersForRole(role: string): void {
    this.roleFilter.set(role);
    this.usersLoading.set(true);
    this.selectedUser.set(null);
    const obs$ = role
      ? this.userService.getUsersByRole(role)
      : this.userService.getAllUsers();

    obs$.subscribe({
      next: env => { this.allUsers.set(env.data ?? []); this.usersLoading.set(false); },
      error: () => { this.allUsers.set([]); this.usersLoading.set(false); }
    });
  }

  /** Already in team for the selected startup */
  private get currentMemberIds(): Set<number> {
    return new Set(this.teamMembers().map(m => m.userId));
  }

  filteredUsers = computed(() => {
    const q = this.searchQuery().toLowerCase();
    const memberIds = new Set(this.teamMembers().map(m => m.userId));
    const myId = this.authService.userId();
    return this.allUsers().filter(u =>
      u.userId !== myId &&
      !memberIds.has(u.userId) &&
      (!q || (u.name ?? '').toLowerCase().includes(q) ||
              u.email.toLowerCase().includes(q) ||
              (u.skills ?? '').toLowerCase().includes(q))
    );
  });

  selectUserToInvite(user: UserResponse): void {
    this.selectedUser.set(user);
    this.selectedRole.set('');
  }

  sendInvite(): void {
    const user = this.selectedUser();
    const role = this.selectedRole();
    const startupId = this.selectedStartupId();

    if (!user || !role || !startupId) {
      this.errorMsg.set('Please select a user and a role.');
      return;
    }

    this.inviting.set(true);
    this.errorMsg.set('');

    const payload: InvitationRequest = {
      startupId,
      invitedUserId: user.userId,
      role
    };

    this.teamService.sendInvitation(payload).subscribe({
      next: () => {
        this.inviting.set(false);
        this.closeDiscovery();
        this.successMsg.set(`Invitation sent to ${user.name ?? user.email}!`);
        setTimeout(() => this.successMsg.set(''), 4000);
      },
      error: env => {
        this.inviting.set(false);
        this.errorMsg.set(env.error ?? 'Failed to send invitation.');
      }
    });
  }

  // ── CoFounder ─────────────────────────────────────────────────────────────
  loadCoFounderData(): void {
    this.teamService.getMyActiveRoles().subscribe({
      next: env => { this.myTeams.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load teams.'); this.loading.set(false); }
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────
  roleLabel(role: string): string {
    return this.roleLabels[role as TeamRole] ?? role.replace(/_/g, ' ');
  }

  roleShortLabel(role: string): string {
    return this.roleShort[role as TeamRole] ?? role;
  }

  roleClass(role: string): string {
    return role === 'CTO'              ? 'badge-purple'
         : role === 'CPO'              ? 'badge-info'
         : role === 'MARKETING_HEAD'   ? 'badge-warning'
         : 'badge-success';
  }

  userInitials(user: UserResponse): string {
    const name = user.name ?? user.email;
    return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
  }
}
