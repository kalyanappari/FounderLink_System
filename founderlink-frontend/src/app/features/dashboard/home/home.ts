import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { StartupService } from '../../../core/services/startup.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { TeamService } from '../../../core/services/team.service';
import { UserService } from '../../../core/services/user.service';
import { StartupResponse, InvestmentResponse, InvitationResponse, TeamMemberResponse, UserResponse } from '../../../models';

@Component({
  selector: 'app-home',
  imports: [CommonModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.css'
})
export class HomeComponent implements OnInit {
  // Founder
  myStartups = signal<StartupResponse[]>([]);
  startupInvestments = signal<InvestmentResponse[]>([]);
  teamCounts = signal<Map<number, number>>(new Map());

  // Investor
  myInvestments = signal<InvestmentResponse[]>([]);
  allStartups = signal<StartupResponse[]>([]);

  // CoFounder
  myInvitations = signal<InvitationResponse[]>([]);

  // Name Maps to prevent showing IDs
  startupNames = signal<Map<number, string>>(new Map());
  userNames = signal<Map<number, string>>(new Map());

  // Consolidated Tactical Data
  allTeamMembers = signal<any[]>([]);
  allProjectInvestments = signal<InvestmentResponse[]>([]);

  loading = signal(true);

  constructor(
    public authService: AuthService,
    private startupService: StartupService,
    private investmentService: InvestmentService,
    private teamService: TeamService,
    private userService: UserService
  ) { }

  ngOnInit(): void {
    const raw = this.authService.role() ?? '';
    const role = raw.replace('ROLE_', '');
    if (role === 'FOUNDER') this.loadFounderData();
    if (role === 'INVESTOR') this.loadInvestorData();
    if (role === 'COFOUNDER') this.loadCofounderData();
    if (role === 'ADMIN') this.loadAdminData();

    // Global Tactical Data Fetching
    this.loadTacticalContext();

    // Pre-fetch all startups for discovery and mapping
    this.startupService.getAll().subscribe({
      next: env => {
        const startups = env.data ?? [];
        this.allStartups.set(startups);
        const map = new Map<number, string>();
        startups.forEach(s => map.set(s.id, s.name));
        this.startupNames.set(map);
      }
    });

    this.userService.getAllUsers().subscribe({
      next: env => {
        const map = new Map<number, string>();
        env.data?.forEach(u => map.set(u.userId, u.name || `User ${u.userId}`));
        this.userNames.set(map);
      }
    });
  }

  private loadTacticalContext(): void {
    const role = this.role;

    // Fetch Team Context
    if (role === 'FOUNDER') {
      this.startupService.getMyStartups().subscribe(env => {
        env.data?.forEach(s => {
          this.teamService.getTeamMembers(s.id).subscribe(teamEnv => {
            const current = this.allTeamMembers();
            this.allTeamMembers.set([...current, ...(teamEnv.data ?? [])]);
          });
        });
      });
    } else if (role === 'COFOUNDER') {
      this.teamService.getMyActiveRoles().subscribe(env => {
        this.allTeamMembers.set(env.data ?? []);
      });
    }

    // Fetch Investment Context
    if (role === 'FOUNDER') {
      this.startupService.getMyStartups().subscribe(env => {
        env.data?.forEach(s => {
          this.investmentService.getStartupInvestments(s.id).subscribe(invEnv => {
            const current = this.allProjectInvestments();
            this.allProjectInvestments.set([...current, ...(invEnv.data ?? [])]);
          });
        });
      });
    } else if (role === 'INVESTOR') {
      this.investmentService.getMyPortfolio().subscribe(env => {
        this.allProjectInvestments.set(env.data ?? []);
      });
    }
  }

  private loadFounderData(): void {
    this.startupService.getMyStartups().subscribe({
      next: env => {
        const startups = env.data ?? [];
        this.myStartups.set(startups);
        this.loading.set(false);
        if (startups.length) {
          this.loadStartupInvestments(startups[0].id);
          // Fetch actual team counts for precision
          startups.forEach(s => this.loadTeamCount(s.id));
        }
      },
      error: () => { this.myStartups.set([]); this.loading.set(false); }
    });
  }

  private loadTeamCount(startupId: number): void {
    this.teamService.getTeamMembers(startupId).subscribe({
      next: env => {
        const current = new Map(this.teamCounts());
        current.set(startupId, env.data?.length ?? 0);
        this.teamCounts.set(current);
      }
    });
  }

  private loadStartupInvestments(startupId: number): void {
    this.investmentService.getStartupInvestments(startupId).subscribe({
      next: env => this.startupInvestments.set(env.data ?? []),
      error: () => this.startupInvestments.set([])
    });
  }

  private loadInvestorData(): void {
    this.investmentService.getMyPortfolio().subscribe({
      next: env => { this.myInvestments.set(env.data ?? []); this.loading.set(false); },
      error: () => { this.myInvestments.set([]); this.loading.set(false); }
    });
  }

  private loadCofounderData(): void {
    this.teamService.getMyInvitations().subscribe({
      next: env => { this.myInvitations.set(env.data ?? []); this.loading.set(false); },
      error: () => { this.myInvitations.set([]); this.loading.set(false); }
    });
  }

  private loadAdminData(): void {
    const statusMap: Record<string, boolean> = {
      auth: true,
      user: true,
      startup: true,
      investment: true,
      team: true,
      payment: true,
      wallet: true,
      notification: true
    };

    // 1. User & Skill Density
    this.userService.getAllUsers().subscribe({
      next: env => {
        const users = env.data ?? [];
        this.totalPlatformUsers.set(users.length);
        this.foundersCount.set(users.filter(u => u.role === 'FOUNDER').length);
        this.investorsCount.set(users.filter(u => u.role === 'INVESTOR').length);
        this.cofoundersCount.set(users.filter(u => u.role === 'COFOUNDER').length);

        // Skill Density Map
        const skillCounters: Record<string, number> = {};
        users.forEach(u => {
          if (u.skills) {
            u.skills.split(',').forEach(s => {
              const skill = s.trim();
              if (skill) skillCounters[skill] = (skillCounters[skill] || 0) + 1;
            });
          }
        });
        const top5Skills = Object.entries(skillCounters)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 5)
          .map(([name, count]) => ({ name, count }));
        this.topSkills.set(top5Skills);

        // Engagement Velocity (by updatedAt)
        const activeUsers = [...users]
          .filter(u => u.updatedAt)
          .sort((a, b) => new Date(b.updatedAt!).getTime() - new Date(a.updatedAt!).getTime())
          .slice(0, 5);
        this.recentUserUpdates.set(activeUsers);
      },
      error: () => (statusMap['user'] = false)
    });

    // 2. Startup & Market Segments
    this.startupService.getAll().subscribe({
      next: env => {
        const startups = env.data ?? [];
        this.allStartupsCount.set(startups.length);
        this.totalPlatformCapitalTarget.set(startups.reduce((acc, s) => acc + (s.fundingGoal || 0), 0));

        // Market Segments
        const segments = { micro: 0, growth: 0, unicorn: 0 };
        startups.forEach(s => {
          if (s.fundingGoal < 100000) segments.micro++;
          else if (s.fundingGoal < 1000000) segments.growth++;
          else segments.unicorn++;
        });
        this.marketSegments.set(segments);

        this.ideaStageCount.set(startups.filter(s => s.stage === 'IDEA').length);
        this.mvpStageCount.set(startups.filter(s => s.stage === 'MVP').length);
        this.tractionStageCount.set(startups.filter(s => s.stage === 'EARLY_TRACTION').length);
        this.scalingStageCount.set(startups.filter(s => s.stage === 'SCALING').length);

        const indMap = new Map<string, number>();
        startups.forEach(s => {
          const ind = s.industry || 'Other';
          indMap.set(ind, (indMap.get(ind) || 0) + 1);
        });
        const sortedInds = Array.from(indMap.entries())
          .sort((a, b) => b[1] - a[1])
          .slice(0, 3)
          .map(([name, count]) => ({ name, count }));
        this.topIndustries.set(sortedInds);

        this.recentStartups.set(startups.slice(-5).reverse());
        this.loading.set(false);
      },
      error: () => {
        statusMap['startup'] = false;
        this.loading.set(false);
      }
    });

    // Update Service Pulse
    this.servicePulse.set(statusMap);
  }

  // ── Intelligence Signals ───────────────────────────────────────
  totalPlatformUsers = signal<number>(0);
  allStartupsCount = signal<number>(0);
  foundersCount = signal<number>(0);
  investorsCount = signal<number>(0);
  cofoundersCount = signal<number>(0);
  totalPlatformCapitalTarget = signal<number>(0);
  ideaStageCount = signal<number>(0);
  mvpStageCount = signal<number>(0);
  tractionStageCount = signal<number>(0);
  scalingStageCount = signal<number>(0);
  topIndustries = signal<{ name: string, count: number }[]>([]);
  recentStartups = signal<StartupResponse[]>([]);
  servicePulse = signal<Record<string, boolean>>({});
  topSkills = signal<{ name: string, count: number }[]>([]);
  marketSegments = signal<{ micro: number, growth: number, unicorn: number }>({ micro: 0, growth: 0, unicorn: 0 });
  recentUserUpdates = signal<UserResponse[]>([]);

  // ── Computed Helpers ───────────────────────────────────────────
  get role(): string {
    return (this.authService.role() ?? '').replace('ROLE_', '');
  }

  get name(): string {
    return (this.authService.email() ?? '').split('@')[0] || 'there';
  }

  get insights(): { title: string, content: string }[] {
    const roleMap: Record<string, { title: string, content: string }[]> = {
      FOUNDER: [
        { title: 'Investor Relations', content: 'Being transparent with your finances makes your startup much more attractive to long-term investors.' },
        { title: 'Ownership Structure', content: 'Raising capital usually means giving up 10-20% of your company in each funding round.' },
        { title: 'Scale Strategy', content: 'Investors currently value startups that can grow efficiently while keeping their spending under control.' }
      ],
      INVESTOR: [
        { title: 'Portfolio Management', content: 'To lower your risk, try to spread your investments across 5-10 different startups in various industries.' },
        { title: 'Due Diligence Checklist', content: 'Always double-check a startup’s legal ownership and insurance before making a major investment.' },
        { title: 'Return on Investment', content: 'In today’s tech market, investors typically look for a 3-5x return over a 5 to 7 year period.' }
      ],
      COFOUNDER: [
        { title: 'Team Agreements', content: 'Having a clear "vesting" schedule is the best way to ensure every co-founder stays committed.' },
        { title: 'Defining Your Role', content: 'Clearly dividing tasks between the CEO and CTO helps avoid confusion and speeds up decision-making.' }
      ]
    };
    return roleMap[this.role] ?? [];
  }

  get milestones(): string[] {
    const roleMap: Record<string, string[]> = {
      FOUNDER: ['Set up Equity & Ownership', 'Find a Lead Investor', 'Legalize Product Ownership'],
      INVESTOR: ['Invest in 3+ Areas', 'Review Startup Profits', 'Check Legal Certificates'],
      COFOUNDER: ['Define Daily Tasks', 'Sign Ownership Agreement', 'Quarterly Sync Meeting']
    };
    return roleMap[this.role] ?? [];
  }

  get guidance(): string {
    const roleMap: Record<string, string> = {
      FOUNDER: 'Your next strategic move should focus on managing your spending to ensure you have enough runway before the next funding round.',
      INVESTOR: 'Current market indicators suggest a switch toward profitable growth. Review your startups for sustainable business models.',
      COFOUNDER: 'Clear communication with the core team is the best way to reduce confusion while your startup is growing quickly.'
    };
    return roleMap[this.role] ?? '';
  }

  // ── Discovery Logic ────────────────────────────────────────────
  discoveryStartups = computed(() => {
    const myInvIds = new Set(this.myInvestments().map(inv => inv.startupId));
    // Filter out startups already in portfolio
    return this.allStartups()
      .filter(s => !myInvIds.has(s.id))
      .slice(0, 5); // Multi-metric list for dashboard
  });

  // ── Partnership Logic ──────────────────────────────────────────
  acceptedInvitations = computed(() => this.myInvitations().filter(i => i.status === 'ACCEPTED'));
  pendingInvitations = computed(() => this.myInvitations().filter(i => i.status === 'PENDING'));

  // ── Computed stats ─────────────────────────────────────────────
  get totalInvested(): number {
    return this.myInvestments().reduce((s, i) => s + i.amount, 0);
  }
  get pendingInvestments(): number {
    return this.myInvestments().filter(i => i.status === 'PENDING').length;
  }
  get approvedInvestments(): number {
    return this.myInvestments().filter(i => i.status === 'APPROVED').length;
  }
  get completedInvestments(): number {
    return this.myInvestments().filter(i => i.status === 'COMPLETED').length;
  }
  get totalFundingReceived(): number {
    return this.startupInvestments()
      .filter(i => i.status === 'COMPLETED' || i.status === 'APPROVED')
      .reduce((s, i) => s + i.amount, 0);
  }

  get totalStartupsCount(): number {
    return this.myStartups().length;
  }

  get totalTeamCount(): number {
    let count = 0;
    this.teamCounts().forEach(c => count += c);
    return count;
  }

  get totalPortfolioSize(): number {
    return this.myInvestments().length;
  }

  get totalPartnershipsCount(): number {
    return this.acceptedInvitations().length;
  }

  get totalInvitationsCount(): number {
    return this.pendingInvitations().length;
  }

  get averageCheckSize(): number {
    const total = this.totalInvested;
    const count = this.totalPortfolioSize;
    return count > 0 ? Math.floor(total / count) : 0;
  }

  get pendingStartupInvestments(): number {
    return this.startupInvestments().filter(i => i.status === 'PENDING').length;
  }

  getStatusClass(status: string): string {
    return status === 'APPROVED' || status === 'ACCEPTED' ? 'badge-success'
      : status === 'COMPLETED' ? 'badge-info'
        : status === 'PENDING' ? 'badge-warning'
          : status === 'REJECTED' ? 'badge-danger'
            : 'badge-gray';
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected',
      COMPLETED: 'Completed', PAYMENT_FAILED: 'Failed', STARTUP_CLOSED: 'Closed',
      ACCEPTED: 'Active'
    };
    return labels[status] ?? status;
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }
}
