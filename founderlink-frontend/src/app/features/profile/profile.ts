import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import {
  UserResponse, UserUpdateRequest,
  StartupResponse, InvestmentResponse, TeamMemberResponse
} from '../../models';
import { StartupService }    from '../../core/services/startup.service';
import { InvestmentService } from '../../core/services/investment.service';
import { TeamService }        from '../../core/services/team.service';

@Component({
  selector: 'app-profile',
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class ProfileComponent implements OnInit {
  private route = inject(ActivatedRoute);

  // ── View Mode ──
  /** true  = viewing someone else's profile (read-only)
   *  false = own profile (editable) */
  isViewingOther = signal(false);
  viewedUserId   = signal<number | null>(null);

  // ── View State ──
  loading    = signal(true);
  saving     = signal(false);
  errorMsg   = signal('');
  successMsg = signal('');
  editingCard = signal<string | null>(null);
  user        = signal<UserResponse | null>(null);

  // ── Ecosystem Activity ──
  activityCount = signal(0);
  activityLabel = signal('Activity');

  // ── Role-specific Data ──
  myStartups    = signal<StartupResponse[]>([]);
  myInvestments = signal<InvestmentResponse[]>([]);
  myActiveRoles = signal<TeamMemberResponse[]>([]);
  myRoleHistory = signal<TeamMemberResponse[]>([]);
  platformStats = signal<{ founders: number; investors: number; cofounders: number } | null>(null);

  // ── Form Model (own profile only) ──
  name           = '';
  email          = '';
  bio            = '';
  skills         = '';
  experience     = '';
  portfolioLinks = '';

  constructor(
    public  authService:       AuthService,
    public  userService:       UserService,
    private startupService:    StartupService,
    private investmentService: InvestmentService,
    private teamService:       TeamService,
    private router:            Router
  ) {}

  ngOnInit(): void {
    // Check if a specific userId param was provided in the URL
    const paramId = this.route.snapshot.paramMap.get('userId');
    const myId    = this.authService.userId();

    if (paramId && Number(paramId) !== myId) {
      // Viewing someone else's profile
      this.isViewingOther.set(true);
      this.viewedUserId.set(Number(paramId));
      this.loadUserById(Number(paramId));
    } else {
      // Own profile
      this.isViewingOther.set(false);
      this.loadProfile();
    }
  }

  // ── Load own profile ──
  loadProfile(): void {
    this.loading.set(true);
    const userId = this.authService.userId();
    if (!userId) {
      this.errorMsg.set('No session found. Please re-login.');
      this.loading.set(false);
      return;
    }
    this.userService.getUser(userId).subscribe({
      next: env => {
        const u = env.data;
        this.user.set(u);
        if (u) {
          this.name           = u.name           ?? '';
          this.email          = u.email          ?? '';
          this.skills         = u.skills         ?? '';
          this.experience     = u.experience     ?? '';
          this.bio            = u.bio            ?? '';
          this.portfolioLinks = u.portfolioLinks ?? '';
          this.loadRoleData(u.role);
        }
        this.loading.set(false);
      },
      error: () => { this.errorMsg.set('Failed to load profile.'); this.loading.set(false); }
    });
  }

  // ── Load another user's profile (read-only) ──
  loadUserById(userId: number): void {
    this.loading.set(true);
    this.userService.getUser(userId).subscribe({
      next: env => {
        const u = env.data;
        this.user.set(u);
        if (u) {
          // Populate read-only skill display
          this.skills         = u.skills         ?? '';
          this.experience     = u.experience     ?? '';
          this.bio            = u.bio            ?? '';
          this.portfolioLinks = u.portfolioLinks ?? '';
          this.loadRoleData(u.role, true);
        }
        this.loading.set(false);
      },
      error: () => { this.errorMsg.set('Failed to load user profile.'); this.loading.set(false); }
    });
  }

  private loadRoleData(role: string, readOnly = false): void {
    const r = role?.replace('ROLE_', '');

    if (r === 'FOUNDER') {
      this.activityLabel.set('Startups Launched');
      if (readOnly) {
        // For read-only: fetch all startup and filter by this founder's id
        const founderId = this.user()?.userId;
        this.startupService.getAll(0, 50).subscribe({
          next: env => {
            const mine = (env.data ?? []).filter(s => s.founderId === founderId);
            this.activityCount.set(mine.length);
            this.myStartups.set(mine);
          }
        });
      } else {
        this.startupService.getMyStartups(0, 10).subscribe({
          next: env => {
            this.activityCount.set(env.totalElements ?? 0);
            this.myStartups.set(env.data ?? []);
          }
        });
      }
    }

    if (r === 'INVESTOR') {
      if (readOnly) {
        const viewedId = this.viewedUserId()!;
        this.activityLabel.set('Investments Made');
        // Strategy 1: direct endpoint GET /investments/investor/{id}
        // Strategy 2 (fallback): cascade all startups → getStartupInvestments → filter
        this.investmentService.getByInvestorId(viewedId).subscribe({
          next: env => {
            const items = env.data ?? [];
            this.myInvestments.set(items);
            this.activityCount.set(items.length);
          },
          error: () => {
            // Fallback: cascade through all startups, catching individual 403s
            this.startupService.getAll(0, 200).subscribe({
              next: env => {
                const startups = env.data ?? [];
                if (startups.length === 0) return;
                forkJoin(
                  startups.map(s =>
                    this.investmentService.getStartupInvestments(s.id).pipe(
                      catchError(() => of({ success: false, data: [] as any[], error: null }))
                    )
                  )
                ).subscribe({
                  next: results => {
                    const all = results.flatMap(r => r.data ?? []);
                    const theirs = all.filter(i => i.investorId === viewedId);
                    this.myInvestments.set(theirs);
                    this.activityCount.set(theirs.length);
                  }
                });
              }
            });
          }
        });
      } else {
        this.activityLabel.set('Investments Active');
        this.investmentService.getMyPortfolio().subscribe({
          next: env => {
            const items = env.data ?? [];
            this.activityCount.set(items.length);
            this.myInvestments.set(items);
          }
        });
      }
    }

    if (r === 'COFOUNDER') {
      if (readOnly) {
        const viewedId = this.viewedUserId()!;
        this.activityLabel.set('Active Roles');

        // Active roles: direct endpoint first, fallback to cascade
        this.teamService.getUserActiveRoles(viewedId).subscribe({
          next: env => {
            const roles = env.data ?? [];
            this.myActiveRoles.set(roles);
            this.activityCount.set(roles.length);
          },
          error: () => {
            // Fallback: cascade all startups → getTeamMembers → filter active
            this.startupService.getAll(0, 200).subscribe({
              next: env => {
                const startups = env.data ?? [];
                if (startups.length === 0) return;
                forkJoin(
                  startups.map(s =>
                    this.teamService.getTeamMembers(s.id).pipe(
                      catchError(() => of({ success: false, data: [] as any[], error: null }))
                    )
                  )
                ).subscribe({
                  next: results => {
                    const all = results.flatMap(r => r.data ?? []);
                    const theirs = all.filter(m => m.userId === viewedId);
                    const active = theirs.filter(m => m.isActive);
                    this.myActiveRoles.set(active);
                    this.activityCount.set(active.length);
                  }
                });
              }
            });
          }
        });

        // Full history (active + past): direct endpoint
        // getTeamMembers only returns active, so we MUST use history endpoint for full record
        this.teamService.getUserTeamHistory(viewedId).subscribe({
          next: env => this.myRoleHistory.set(env.data ?? []),
          error: () => {
            // History endpoint unavailable — history stays empty, show note in UI
            this.myRoleHistory.set([]);
          }
        });
      } else {
        this.activityLabel.set('Active Roles');
        this.teamService.getMyActiveRoles().subscribe({
          next: env => {
            const roles = env.data ?? [];
            this.activityCount.set(roles.length);
            this.myActiveRoles.set(roles);
          }
        });
        this.teamService.getMemberHistory().subscribe({
          next: env => this.myRoleHistory.set(env.data ?? [])
        });
      }
    }

    if (r === 'ADMIN') {
      this.activityLabel.set('Platform Users');
      if (!readOnly) {
        this.userService.getPublicStats().subscribe({
          next: stats => {
            this.platformStats.set(stats);
            this.activityCount.set(stats.founders + stats.investors + stats.cofounders);
          }
        });
      }
    }
  }

  // ── Editing (own profile only) ──
  startEditing(card: string): void {
    if (this.isViewingOther()) return;
    this.editingCard.set(card);
  }

  cancelEditing(): void {
    this.editingCard.set(null);
    this.loadProfile();
  }

  saveCard(card: string): void {
    this.saving.set(true);
    const req: UserUpdateRequest = {
      name:           this.name           || null,
      bio:            this.bio            || null,
      skills:         this.skills         || null,
      experience:     this.experience     || null,
      portfolioLinks: this.portfolioLinks || null
    };
    this.userService.updateMyProfile(req).subscribe({
      next: env => {
        this.user.set(env.data);
        this.editingCard.set(null);
        this.saving.set(false);
        this.successMsg.set('Saved!');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.saving.set(false);
        this.errorMsg.set(env.error ?? 'Failed to update.');
      }
    });
  }

  // ── Navigation helper (used from other components) ──
  goBack(): void { history.back(); }

  // ── Profile Completion (computed from whichever user is loaded) ──
  completionPercentage = computed(() => {
    const items = this.completionItems();
    const done  = items.filter(i => i.completed).length;
    return Math.round((done / items.length) * 100);
  });

  completionItems = computed(() => {
    const u = this.user();
    return [
      { label: 'Basic Identity',     completed: !!u?.name && !!u?.email },
      { label: 'Professional Bio',   completed: !!u?.bio },
      { label: 'Skills & Expertise', completed: !!u?.skills },
      { label: 'Experience Record',  completed: !!u?.experience },
      { label: 'Portfolio Links',    completed: !!u?.portfolioLinks },
      { label: 'Ecosystem Activity', completed: this.activityCount() > 0 }
    ];
  });

  // ── Helpers ──
  /** Number of past (inactive) roles in the history list — avoids inline template logic */
  get pastRoleCount(): number {
    return this.myRoleHistory().filter(r => !r.isActive).length;
  }

  stageLabel(stage: string): string {
    const m: Record<string,string> = { IDEA:'Idea', MVP:'MVP', EARLY_TRACTION:'Early Traction', SCALING:'Scaling' };
    return m[stage] ?? stage;
  }

  stageColor(stage: string): string {
    const m: Record<string,string> = { IDEA:'#8b5cf6', MVP:'#3b82f6', EARLY_TRACTION:'#f59e0b', SCALING:'#10b981' };
    return m[stage] ?? '#6366f1';
  }

  investmentStatusColor(status: string): string {
    const m: Record<string,string> = { PENDING:'#f59e0b', APPROVED:'#3b82f6', COMPLETED:'#10b981', REJECTED:'#ef4444', PAYMENT_FAILED:'#ef4444', STARTUP_CLOSED:'#6b7280' };
    return m[status] ?? '#6366f1';
  }

  teamRoleLabel(role: string): string {
    const m: Record<string,string> = { CTO:'CTO', CPO:'CPO', MARKETING_HEAD:'Marketing Head', ENGINEERING_LEAD:'Engineering Lead' };
    return m[role] ?? role;
  }

  formatCurrency(amount: number): string {
    if (amount >= 10_000_000) return `₹${(amount/10_000_000).toFixed(1)}Cr`;
    if (amount >= 100_000)    return `₹${(amount/100_000).toFixed(1)}L`;
    return `₹${amount.toLocaleString('en-IN')}`;
  }

  logout(): void { this.authService.logout(); }
}
