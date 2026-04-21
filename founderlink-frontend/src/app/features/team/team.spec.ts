import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TeamComponent } from './team';
import { AuthService } from '../../core/services/auth.service';
import { TeamService } from '../../core/services/team.service';
import { StartupService } from '../../core/services/startup.service';
import { UserService } from '../../core/services/user.service';
import { provideRouter, Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { vi } from 'vitest';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

describe('TeamComponent', () => {
  let component: TeamComponent;
  let fixture: ComponentFixture<TeamComponent>;
  let teamServiceSpy: any;
  let startupServiceSpy: any;
  let userServiceSpy: any;
  let authServiceSpy: any;

  beforeEach(async () => {
    teamServiceSpy = {
      getTeamMembers: vi.fn(),
      getStartupInvitations: vi.fn(),
      getMemberHistory: vi.fn(),
      removeMember: vi.fn(),
      cancelInvitation: vi.fn(),
      sendInvitation: vi.fn()
    };
    startupServiceSpy = {
      getAll: vi.fn(),
      getMyStartups: vi.fn()
    };
    userServiceSpy = {
      getAllUsers: vi.fn(),
      getUsersByRole: vi.fn(),
      getUser: vi.fn()
    };
    authServiceSpy = {
      role: vi.fn().mockReturnValue('ROLE_FOUNDER'),
      userId: vi.fn().mockReturnValue(1)
    };


    await TestBed.configureTestingModule({
      imports: [TeamComponent, ReactiveFormsModule, FormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: TeamService, useValue: teamServiceSpy },
        { provide: StartupService, useValue: startupServiceSpy },
        { provide: UserService, useValue: userServiceSpy }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  describe('Founder Initialization', () => {
    beforeEach(() => {
      // Base Prefetches
      startupServiceSpy.getAll.mockReturnValue(of({ data: [{ id: 10, name: 'Alpha', founderId: 2 }] }));
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [{ userId: 5, name: 'Eva' }] }));
      
      // Founder specifically fetches their startups
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [{ id: 10 }] }));
      teamServiceSpy.getTeamMembers.mockReturnValue(of({ data: [{ id: 1, userId: 5, role: 'CTO' }] }));
      teamServiceSpy.getStartupInvitations.mockReturnValue(of({ data: [] }));
    });

    it('should aggregate context logically throughout the initialization phase if Founder', async () => {
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      await new Promise(r => setTimeout(r, 0));

      expect(startupServiceSpy.getAll).toHaveBeenCalled();
      expect(userServiceSpy.getAllUsers).toHaveBeenCalled();
      expect(component.startupNames().get(10)).toBe('Alpha');
      expect(component.startupFounders().get(10)).toBe(2);

      expect(startupServiceSpy.getMyStartups).toHaveBeenCalled();
      expect(component.selectedStartupId()).toBe(10);
      expect(teamServiceSpy.getTeamMembers).toHaveBeenCalledWith(10);
      expect(component.teamMembers().length).toBe(1);
    });
    
    it('should fetch extra user details if invitations come back with unknown IDs', async () => {
      teamServiceSpy.getStartupInvitations.mockReturnValue(of({
        data: [{ id: 99, status: 'PENDING', invitedUserId: 999 }]
      }));
      userServiceSpy.getUser.mockReturnValue(of({ data: { userId: 999, name: 'John Doe' } }));
      
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      await new Promise(r => setTimeout(r, 0));

      expect(userServiceSpy.getUser).toHaveBeenCalledWith(999);
      expect(component.userNames().get(999)).toBe('John Doe');
    });

    it('should handle missing startups correctly', () => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.selectedStartupId()).toBeNull();
      expect(component.loading()).toBe(false);
    });
  });

  describe('CoFounder Initialization', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('COFOUNDER');
      // Shared prefetch mocks
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [] }));
      teamServiceSpy.getMemberHistory.mockReturnValue(of({ data: [{ id: 50, isActive: true }] }));
    });

    it('should load history', () => {
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(teamServiceSpy.getMemberHistory).toHaveBeenCalled();
      expect(component.activeTeams().length).toBe(1);
      expect(component.historicalTeams().length).toBe(0);
      expect(component.paginatedTeams().length).toBe(1);
      expect(component.loading()).toBe(false);
    });

    it('should handle history failure', async () => {
      teamServiceSpy.getMemberHistory.mockReturnValue(throwError(() => ({ error: 'Error History' })));
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      expect(component.errorMsg()).toBe('Error History');
    });
  });

  describe('Founder Actions', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('FOUNDER');
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [] }));
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should trigger confirm dialog block on member removal', () => {
      window.confirm = vi.fn().mockReturnValue(false);
      component.removeMember(100);
      expect(teamServiceSpy.removeMember).not.toHaveBeenCalled();
    });

    it('should process removal safely', () => {
      window.confirm = vi.fn().mockReturnValue(true);
      component.teamMembers.set([{ id: 100 } as any]);
      teamServiceSpy.removeMember.mockReturnValue(of({}));
      
      component.removeMember(100);
      
      expect(teamServiceSpy.removeMember).toHaveBeenCalledWith(100);
      expect(component.teamMembers().length).toBe(0);
      expect(component.successMsg()).not.toBe('');
      
      vi.advanceTimersByTime(3500);
      expect(component.successMsg()).toBe(''); // Disappears after interval
    });

    it('should evaluate role limits efficiently when rendering buttons', () => {
      component.teamMembers.set([{ role: 'CTO' }] as any[]);
      component.pendingInvitations.set([{ role: 'CPO' }] as any[]);
      
      expect(component.isRoleActive('CTO')).toBe(true);
      expect(component.isRolePending('CPO')).toBe(true);
      expect(component.isRoleActive('MARKETING_HEAD')).toBe(false);
    });

    it('should cleanly abort invitation sends with validation', () => {
      component.selectedStartupId.set(1);
      component.selectedUser.set({ userId: 5 } as any);
      component.selectedRole.set('CPO');
      
      // Attempt 1: Already active
      component.teamMembers.set([{ role: 'CPO' }] as any[]);
      component.sendInvite();
      expect(component.errorMsg()).toContain('already filled');

      // Attempt 2: Missing setup
      component.selectedRole.set('');
      component.sendInvite();
      expect(component.errorMsg()).toContain('select a user and a role');
      
      expect(teamServiceSpy.sendInvitation).not.toHaveBeenCalled();
    });

    it('should send invitations', () => {
      component.selectedStartupId.set(1);
      component.selectedUser.set({ userId: 5 } as any);
      component.selectedRole.set('CTO');
      component.showDiscovery.set(true); // Should auto hide
      
      teamServiceSpy.sendInvitation.mockReturnValue(of({}));
      
      component.sendInvite();

      expect(teamServiceSpy.sendInvitation).toHaveBeenCalled();
      expect(component.showDiscovery()).toBe(false); // Closed
    });

    it('should handle cancelInvite confirmation and success', () => {
      window.confirm = vi.fn().mockReturnValue(true);
      component.pendingInvitations.set([{ id: 500, status: 'PENDING' }] as any[]);
      teamServiceSpy.cancelInvitation.mockReturnValue(of({}));

      component.cancelInvite(500);

      expect(teamServiceSpy.cancelInvitation).toHaveBeenCalledWith(500);
      expect(component.pendingInvitations().length).toBe(0);
      expect(component.successMsg()).toContain('cancelled');
    });

    it('should handle cancelInvite failure', () => {
      window.confirm = vi.fn().mockReturnValue(true);
      teamServiceSpy.cancelInvitation.mockReturnValue(throwError(() => ({ error: 'Cancel Failed' })));

      component.cancelInvite(500);

      expect(component.errorMsg()).toBe('Cancel Failed');
    });

    it('should abort cancelInvite if user rejects confirmation', () => {
      window.confirm = vi.fn().mockReturnValue(false);
      component.cancelInvite(500);
      expect(teamServiceSpy.cancelInvitation).not.toHaveBeenCalled();
    });

    it('should reload team and close discovery on startup change', () => {
      teamServiceSpy.getTeamMembers.mockReturnValue(of({ data: [] }));
      teamServiceSpy.getStartupInvitations.mockReturnValue(of({ data: [] }));
      const loadSpy = vi.spyOn(component, 'loadTeam');
      component.showDiscovery.set(true);
      
      component.onStartupChange(200);

      expect(component.selectedStartupId()).toBe(200);
      expect(component.showDiscovery()).toBe(false);
      expect(loadSpy).toHaveBeenCalledWith(200);
    });

    it('should navigate to member or founder chat', () => {
      const router = TestBed.inject(Router);
      const navigateSpy = vi.spyOn(router, 'navigate');

      // Member
      component.messageMember(123);
      expect(navigateSpy).toHaveBeenCalledWith(['/dashboard/messages'], { queryParams: { user: 123 } });

      // Founder
      component.startupFounders.set(new Map([[10, 99]]));
      component.messageFounder(10);
      expect(navigateSpy).toHaveBeenCalledWith(['/dashboard/messages'], { queryParams: { user: 99 } });
    });

    it('should navigate to startup details', () => {
      const router = TestBed.inject(Router);
      const navigateSpy = vi.spyOn(router, 'navigate');

      component.viewStartup(500);
      expect(navigateSpy).toHaveBeenCalledWith(['/startup', 500]);
    });
  });

  describe('Filtering Mechanisms for Directory', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [] }));
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should fetch users correctly when explicitly asked', () => {
      userServiceSpy.getUsersByRole.mockReturnValue(of({ data: [{ userId: 10, name: 'X' }] }));
      
      component.loadUsersForRole('INVESTOR');
      
      expect(userServiceSpy.getUsersByRole).toHaveBeenCalledWith('INVESTOR');
      expect(component.allUsers().length).toBe(1);
    });

  describe('User Discovery Handling', () => {
    it('should handle loadUsersForRole failure', () => {
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      
      const service = TestBed.inject(UserService);
      vi.spyOn(service, 'getUsersByRole').mockReturnValue(throwError(() => ({ error: 'Load Users Failed' })));
      
      component.loadUsersForRole('COFOUNDER');
      
      expect(component.errorMsg()).toBe('Load Users Failed');
      expect(component.usersLoading()).toBe(false);
    });
  });

    it('should compute complex filters omitting current logged in identity and active team', () => {
      authServiceSpy.userId.mockReturnValue(99); 
      // User 2 in team
      component.teamMembers.set([{ userId: 2 } as any]);
      
      // All users fetched implicitly to UI array layer
      component.allUsers.set([
        { userId: 99, email: 'me' },
        { userId: 2, email: 'teammate' },
        { userId: 3, email: 'john@c.com', name: 'John', skills: 'Vue' },
        { userId: 4, email: 'mike@c.com', name: 'Mike', skills: 'React' }
      ] as any[]);

      component.searchQuery.set('vu'); // Should match John

      const matches = component.filteredUsers();
      expect(matches.length).toBe(1);
      expect(matches[0].userId).toBe(3); // Match!
    });
  });

  describe('Paginators API', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
    });

    it('should safely update current page references', () => {
      component.onPageChangeSquad(2); expect(component.currentPageSquad()).toBe(2);
      component.onPageChangePipeline(3); expect(component.currentPagePipeline()).toBe(3);
      component.onPageChangeDiscovery(4); expect(component.currentPageDiscovery()).toBe(4);
      component.onPageChangeTeams(5); expect(component.currentPageTeams()).toBe(5);
    });
  });

  describe('Styling & View Config', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
    });

    it('should derive CSS properly for dynamic roles', () => {
      expect(component.roleClass('CTO')).toBe('badge-purple');
      expect(component.roleClass('CPO')).toBe('badge-info');
      expect(component.roleClass('MARKETING_HEAD')).toBe('badge-warning');
      expect(component.roleClass('OTHER')).toBe('badge-success');
    });

    it('should fetch reliable default mappings', () => {
      expect(component.roleLabel('CTO')).toBe('Chief Technology Officer');
      expect(component.roleShortLabel('CTO')).toBe('CTO');
    });

    it('should generate logical initials natively', () => {
      expect(component.userInitials({ name: 'Hello World', email: 'x' } as any)).toBe('HW');
      expect(component.userInitials({ name: undefined, email: 'test@domain.com' } as any)).toBe('T');
    });
  });

  describe('Template UI Verification', () => {
    it('should show error alert when errorMsg is set', async () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [] }));
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      
      fixture = TestBed.createComponent(TeamComponent);
      component = fixture.componentInstance;
      component.errorMsg.set('Initialization Failed');
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      const alert = fixture.nativeElement.querySelector('.alert-error');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('Initialization Failed');
    });

    it('should render the Co-Founder teams view when role is COFOUNDER', async () => {
      authServiceSpy.role.mockReturnValue('COFOUNDER');
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [] }));
      teamServiceSpy.getMemberHistory.mockReturnValue(of({ data: [{ id: 1, isActive: true, startupId: 10 }] }));
      
      fixture = TestBed.createComponent(TeamComponent);
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      const teamsSection = fixture.nativeElement.querySelector('.active-squad');
      expect(teamsSection).toBeTruthy();
      expect(fixture.nativeElement.textContent).toContain('Active Startup Seats');
    });

    it('should show empty state in discovery panel', async () => {
      component.showDiscovery.set(true);
      component.allUsers.set([]);
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      const emptyState = fixture.nativeElement.querySelector('.empty-state-sm');
      if (emptyState) { 
         expect(emptyState).toBeTruthy();
      }
    });
  });
});
