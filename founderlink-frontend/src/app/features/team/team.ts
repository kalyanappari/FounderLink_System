import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TeamService } from '../../core/services/team.service';
import { StartupService } from '../../core/services/startup.service';
import { UserService } from '../../core/services/user.service';
import {
  TeamMemberResponse, StartupResponse, UserResponse, TeamRole, InvitationRequest, InvitationResponse
} from '../../models';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-team',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, PaginationComponent],
  templateUrl: './team.html',
  styleUrl: './team.css'
})
export class TeamComponent implements OnInit {
  loading = signal(true);
  errorMsg = signal('');
  successMsg = signal('');

  // ── Pagination State ───────────────────────────────────────────────────────
  currentPageSquad = signal(1);
  pageSizeSquad = signal(5);

  currentPagePipeline = signal(1);
  pageSizePipeline = signal(5);

  currentPageDiscovery = signal(1);
  pageSizeDiscovery = signal(8);

  currentPageTeams = signal(1);
  pageSizeTeams = signal(5);

  // ── Founder state ─────────────────────────────────────────────────────────
  myStartups = signal<StartupResponse[]>([]);
  selectedStartupId = signal<number | null>(null);
  teamMembers = signal<TeamMemberResponse[]>([]);
  pendingInvitations = signal<InvitationResponse[]>([]);
  removing = signal<number | null>(null);
  cancellingInvId = signal<number | null>(null);

  // User discovery panel
  showDiscovery = signal(false);
  allUsers = signal<UserResponse[]>([]);
  usersLoading = signal(false);
  roleFilter = signal<string>('COFOUNDER');
  searchQuery = signal('');
  selectedUser = signal<UserResponse | null>(null);
  selectedRole = signal<TeamRole | ''>('');
  inviting = signal(false);

  // ── Computed Paginated Lists ──────────────────────────────────────────────
  paginatedSquad = computed(() => {
    const start = (this.currentPageSquad() - 1) * this.pageSizeSquad();
    return this.teamMembers().slice(start, start + this.pageSizeSquad());
  });

  paginatedPipeline = computed(() => {
    const start = (this.currentPagePipeline() - 1) * this.pageSizePipeline();
    return this.pendingInvitations().slice(start, start + this.pageSizePipeline());
  });

  paginatedDiscovery = computed(() => {
    const start = (this.currentPageDiscovery() - 1) * this.pageSizeDiscovery();
    return this.filteredUsers().slice(start, start + this.pageSizeDiscovery());
  });

  paginatedTeams = computed(() => {
    const start = (this.currentPageTeams() - 1) * this.pageSizeTeams();
    return this.myTeams().slice(start, start + this.pageSizeTeams());
  });

  // ── CoFounder state ───────────────────────────────────────────────────────
  myTeams = signal<TeamMemberResponse[]>([]);
  viewedUser = signal<UserResponse | null>(null);

  // Startup Name Map & User Name Map
  startupNames = signal<Map<number, string>>(new Map());
  userNames = signal<Map<number, string>>(new Map());
  startupFounders = signal<Map<number, number>>(new Map());

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

  isRoleActive(role: string): boolean {
    return this.teamMembers().some(m => m.role === role);
  }

  isRolePending(role: string): boolean {
    return this.pendingInvitations().some(i => i.role === role);
  }

  private hasRole(r: string): boolean {
    const stored = this.authService.role() ?? '';
    return stored === r || stored === `ROLE_${r}`;
  }
  isFounder(): boolean { return this.hasRole('FOUNDER'); }
  isCoFounder(): boolean { return this.hasRole('COFOUNDER'); }

  constructor(
    public authService: AuthService,
    private teamService: TeamService,
    private startupService: StartupService,
    private userService: UserService,
    private router: Router
  ) { 
    // Reset pages when context changes
    effect(() => {
      this.selectedStartupId();
      this.currentPageSquad.set(1);
      this.currentPagePipeline.set(1);
    }, { allowSignalWrites: true });

    effect(() => {
      this.searchQuery();
      this.roleFilter();
      this.currentPageDiscovery.set(1);
    }, { allowSignalWrites: true });
  }

  onPageChangeSquad(page: number): void {
    this.currentPageSquad.set(page);
  }

  onPageChangePipeline(page: number): void {
    this.currentPagePipeline.set(page);
  }

  onPageChangeDiscovery(page: number): void {
    this.currentPageDiscovery.set(page);
  }

  onPageChangeTeams(page: number): void {
    this.currentPageTeams.set(page);
  }

  ngOnInit(): void {
    if (this.isFounder()) this.loadFounderData();
    if (this.isCoFounder()) this.loadCoFounderData();

    // Prefetch startup names
    this.startupService.getAll().subscribe({
      next: env => {
        const nameMap = new Map<number, string>();
        const founderMap = new Map<number, number>();
        env.data?.forEach(s => {
          nameMap.set(s.id, s.name);
          founderMap.set(s.id, s.founderId);
        });
        this.startupNames.set(nameMap);
        this.startupFounders.set(founderMap);
      }
    });

    // Prefetch all users to map names in the team members list
    this.userService.getAllUsers().subscribe({
      next: env => {
        const map = new Map<number, string>();
        env.data?.forEach(u => map.set(u.userId, u.name || u.email));
        this.userNames.set(map);
      }
    });
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
    // Fetch active members
    this.teamService.getTeamMembers(startupId).subscribe({
      next: env => { this.teamMembers.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load team.'); this.loading.set(false); }
    });

    // Fetch pending invitations
    this.teamService.getStartupInvitations(startupId).subscribe({
      next: env => {
        const invs = (env.data ?? []).filter(i => i.status === 'PENDING');
        this.pendingInvitations.set(invs);

        // Ensure we have names for these invited users
        invs.forEach(inv => {
          if (!this.userNames().has(inv.invitedUserId)) {
            this.userService.getUser(inv.invitedUserId).subscribe({
              next: uenv => {
                if (uenv.data) {
                  this.userNames.update(map => {
                    const newMap = new Map(map);
                    newMap.set(uenv.data!.userId, uenv.data!.name || uenv.data!.email);
                    return newMap;
                  });
                }
              }
            });
          }
        });
      }
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

  cancelInvite(invitationId: number): void {
    if (!confirm('Cancel this pending invitation?')) return;
    this.cancellingInvId.set(invitationId);
    this.teamService.cancelInvitation(invitationId).subscribe({
      next: () => {
        this.cancellingInvId.set(null);
        this.pendingInvitations.update(list => list.filter(i => i.id !== invitationId));
        this.successMsg.set('Invitation cancelled.');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.cancellingInvId.set(null);
        this.errorMsg.set(env.error ?? 'Failed to cancel invitation.');
      }
    });
  }

  messageMember(userId: number): void {
    // Navigate safely to the messaging portal for this specific user
    this.router.navigate(['/dashboard/messages'], { queryParams: { user: userId } });
  }

  // ── User Discovery ────────────────────────────────────────────────────────
  openDiscovery(): void {
    this.showDiscovery.set(true);
    this.selectedUser.set(null);
    this.selectedRole.set('');
    this.searchQuery.set('');
    this.loadUsersForRole('COFOUNDER');
  }

  closeDiscovery(): void {
    this.showDiscovery.set(false);
    this.selectedUser.set(null);
    this.selectedRole.set('');
    this.viewedUser.set(null);
  }

  viewProfile(u: UserResponse): void {
    this.viewedUser.set(u);
  }

  closeProfile(): void {
    this.viewedUser.set(null);
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

    if (this.isRoleActive(role)) {
      this.errorMsg.set(`The ${this.roleLabel(role)} role is already filled by an active member.`);
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

  viewStartup(startupId: number): void {
    this.router.navigate(['/startup', startupId]);
  }

  messageFounder(startupId: number): void {
    const founderId = this.startupFounders().get(startupId);
    if (founderId) {
      this.router.navigate(['/dashboard/messages'], { queryParams: { user: founderId } });
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────
  roleLabel(role: string): string {
    return this.roleLabels[role as TeamRole] ?? role.replace(/_/g, ' ');
  }

  roleShortLabel(role: string): string {
    return this.roleShort[role as TeamRole] ?? role;
  }

  roleClass(role: string): string {
    return role === 'CTO' ? 'badge-purple'
      : role === 'CPO' ? 'badge-info'
        : role === 'MARKETING_HEAD' ? 'badge-warning'
          : 'badge-success';
  }

  userInitials(user: UserResponse): string {
    const name = user.name ?? user.email;
    return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
  }
}
