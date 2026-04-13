import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeComponent } from './home';
import { AuthService } from '../../../core/services/auth.service';
import { StartupService } from '../../../core/services/startup.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { TeamService } from '../../../core/services/team.service';
import { UserService } from '../../../core/services/user.service';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let authServiceSpy: any;
  let startupServiceSpy: any;
  let investmentServiceSpy: any;
  let teamServiceSpy: any;
  let userServiceSpy: any;

  beforeEach(async () => {
    // Setup generic mock responses
    startupServiceSpy = {
      getAll: vi.fn().mockReturnValue(of({ data: [{ id: 1, name: 'Global Startup' }] })),
      getMyStartups: vi.fn().mockReturnValue(of({ data: [] })),
    };
    investmentServiceSpy = {
      getStartupInvestments: vi.fn().mockReturnValue(of({ data: [] })),
      getMyPortfolio: vi.fn().mockReturnValue(of({ data: [] })),
    };
    teamServiceSpy = {
      getTeamMembers: vi.fn().mockReturnValue(of({ data: [] })),
      getMemberHistory: vi.fn().mockReturnValue(of({ data: [] })),
      getMyInvitations: vi.fn().mockReturnValue(of({ data: [] })),
    };
    userServiceSpy = {
      getAllUsers: vi.fn().mockReturnValue(of({ data: [] })),
      getPublicStats: vi.fn().mockReturnValue(of({ founders: 1, investors: 2, cofounders: 3 }))
    };
    authServiceSpy = {
      role: vi.fn().mockReturnValue('FOUNDER'),
      email: vi.fn().mockReturnValue('test@example.com')
    };

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: StartupService, useValue: startupServiceSpy },
        { provide: InvestmentService, useValue: investmentServiceSpy },
        { provide: TeamService, useValue: teamServiceSpy },
        { provide: UserService, useValue: userServiceSpy }
      ]
    }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  // ─── Shared Base Data Tests ────────────────────────────────────────────────

  describe('Shared Component Data', () => {
    it('should pre-fetch global startups and users on initialization', () => {
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [{ userId: 7, name: 'Alice' }] }));
      startupServiceSpy.getAll.mockReturnValue(of({ data: [{ id: 5, name: 'XCorp' }] }));
      
      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.startupNames().get(5)).toBe('XCorp');
      expect(component.userNames().get(7)).toBe('Alice');
    });

    it('should correctly format computed user name and get insights', () => {
      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.name).toBe('test');
      expect(component.insights.length).toBe(3);
      expect(component.guidance).toBeTruthy();
    });
  });

  // ─── Role: FOUNDER ────────────────────────────────────────────────────────

  describe('Role: FOUNDER logic execution', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('ROLE_FOUNDER');
      const startups = [{ id: 9, name: 'Local Startup' }];
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: startups }));
      teamServiceSpy.getTeamMembers.mockReturnValue(of({ data: [{ userId: 1 }, { userId: 2 }] }));
      
      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should execute loadFounderData and update component state', () => {
      expect(component.myStartups().length).toBe(1);
      // It should also cascade load team members and investments
      expect(teamServiceSpy.getTeamMembers).toHaveBeenCalledWith(9);
      expect(investmentServiceSpy.getStartupInvestments).toHaveBeenCalledWith(9);
    });

    it('should correctly tally the team counts from nested subscriptions', () => {
      expect(component.teamCounts().get(9)).toBe(2);
      expect(component.totalStartupsCount).toBe(1);
      expect(component.totalTeamCount).toBe(2);
    });
  });

  // ─── Role: INVESTOR ───────────────────────────────────────────────────────

  describe('Role: INVESTOR logic execution', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('ROLE_INVESTOR');
      investmentServiceSpy.getMyPortfolio.mockReturnValue(of({ 
        data: [
          { startupId: 1, amount: 20000, status: 'APPROVED' },
          { startupId: 2, amount: 30000, status: 'PENDING' }
        ] 
      }));
      
      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should execute loadInvestorData and load portfolio info', () => {
      expect(component.totalInvested).toBe(50000);
      expect(component.pendingInvestments).toBe(1);
      expect(component.approvedInvestments).toBe(1);
      
      // Should also compute discovery startups (excluding ones already invested in)
      const mockGlobalStartups = [
        { id: 1, name: 'In Portfolio' },
        { id: 3, name: 'Not In Portfolio' }
      ];
      component.allStartups.set(mockGlobalStartups as any[]);
      
      const discovery = component.discoveryStartups();
      expect(discovery.length).toBe(1);
      expect(discovery[0].id).toBe(3);
    });
  });

  // ─── Role: COFOUNDER ──────────────────────────────────────────────────────

  describe('Role: COFOUNDER logic execution', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('COFOUNDER'); // without prefix
      teamServiceSpy.getMyInvitations.mockReturnValue(of({
        data: [{ id: 1, status: 'PENDING', createdAt: new Date() }]
      }));
      
      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should collect active invitations', () => {
      expect(component.pendingInvitations().length).toBe(1);
      expect(component.totalInvitationsCount).toBe(1);
    });
  });

  // ─── Role: ADMIN ──────────────────────────────────────────────────────────

  describe('Role: ADMIN logic execution', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('ADMIN');
      
      // Advanced dashboard loading for Admin
      userServiceSpy.getAllUsers.mockReturnValue(of({
        data: [
          { userId: 1, skills: 'Angular, Node', updatedAt: '2025-01-01' },
          { userId: 2, skills: 'Node, React', updatedAt: '2025-01-02' }
        ]
      }));

      startupServiceSpy.getAll.mockReturnValue(of({
        data: [
          { id: 1, industry: 'SaaS', fundingGoal: 500000, stage: 'SCALING' },
          { id: 2, industry: 'SaaS', fundingGoal: 60000, stage: 'IDEA' },
          { id: 3, industry: 'Hardware', fundingGoal: 1500000, stage: 'EARLY_TRACTION' }
        ]
      }));

      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should aggregate platform stats successfully', () => {
      expect(component.foundersCount()).toBe(1); // from getPublicStats override
      expect(component.totalPlatformUsers()).toBe(6);
      
      // Starts calculation logic
      expect(component.allStartupsCount()).toBe(3);
      expect(component.totalPlatformCapitalTarget()).toBe(2060000);
      
      // Sub-breakdowns
      expect(component.topIndustries().find(i => i.name === 'SaaS')?.count).toBe(2);
      expect(component.topSkills().find(s => s.name === 'Node')?.count).toBe(2);
      
      // Segment logic checks
      expect(component.marketSegments().micro).toBe(1); // < 100k
      expect(component.marketSegments().growth).toBe(1); // < 1M
      expect(component.marketSegments().unicorn).toBe(1); // >= 1M
    });
  });

  // ─── Utility ──────────────────────────────────────────────────────────────
  
  describe('Utility Methods', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(HomeComponent);
      component = fixture.componentInstance;
    });

    it('should format currency accurately', () => {
      expect(component.formatCurrency(75000)).toContain('75,000');
    });

    it('should identify active partnerships', () => {
      component.allTeamMembers.set([{ startupId: 5, isActive: true }]);
      expect(component.isPartnershipActive(5)).toBe(true);
      expect(component.isPartnershipActive(7)).toBe(false);
    });

    it('should get badge colors properly', () => {
      expect(component.getStatusClass('APPROVED')).toBe('badge-success');
      expect(component.getStatusClass('REJECTED')).toBe('badge-danger');
      expect(component.getStatusClass('xyz')).toBe('badge-gray');
    });

    it('should get correct status label', () => {
      expect(component.statusLabel('PAYMENT_FAILED')).toBe('Failed');
      expect(component.statusLabel('COMPLETED')).toBe('Completed');
    });
  });
});
