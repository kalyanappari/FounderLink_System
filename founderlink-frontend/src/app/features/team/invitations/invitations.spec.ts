import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InvitationsComponent } from './invitations';
import { AuthService } from '../../../core/services/auth.service';
import { TeamService } from '../../../core/services/team.service';
import { StartupService } from '../../../core/services/startup.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';


describe('InvitationsComponent', () => {
  let component: InvitationsComponent;
  let fixture: ComponentFixture<InvitationsComponent>;
  let teamServiceSpy: any;
  let startupServiceSpy: any;
  let authServiceSpy: any;

  beforeEach(async () => {
    teamServiceSpy = {
      getMyInvitations: vi.fn(),
      joinTeam: vi.fn(),
      rejectInvitation: vi.fn()
    };
    startupServiceSpy = {
      getAll: vi.fn()
    };
    authServiceSpy = {
      role: vi.fn().mockReturnValue('COFOUNDER')
    };



    await TestBed.configureTestingModule({
      imports: [InvitationsComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: TeamService, useValue: teamServiceSpy },
        { provide: StartupService, useValue: startupServiceSpy }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Init Fetching', () => {
    it('should prefetch startups properly mapping names and load invitations', () => {
      teamServiceSpy.getMyInvitations.mockReturnValue(of({ data: [{ id: 1, status: 'PENDING', role: 'CTO' }] }));
      startupServiceSpy.getAll.mockReturnValue(of({ data: [{ id: 9, name: 'Taco' }] }));

      fixture = TestBed.createComponent(InvitationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.startupNames().get(9)).toBe('Taco');
      expect(component.invitations().length).toBe(1);
      expect(component.loading()).toBe(false);
    });

    it('should handle error cases when parsing invitations', () => {
      teamServiceSpy.getMyInvitations.mockReturnValue(throwError(() => ({ error: 'Load Fallback' })));
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));

      fixture = TestBed.createComponent(InvitationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.errorMsg()).toBe('Load Fallback');
      expect(component.invitations().length).toBe(0);
    });
  });

  describe('Mechanics for Acting on Invites', () => {
    beforeEach(() => {
      teamServiceSpy.getMyInvitations.mockReturnValue(of({ data: [] }));
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(InvitationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should cleanly execute team join requests mutating local states', () => {
      component.invitations.set([{ id: 7, status: 'PENDING', role: 'CPO' } as any]);
      teamServiceSpy.joinTeam.mockReturnValue(of({}));
      
      component.accept({ id: 7 } as any);
      
      expect(teamServiceSpy.joinTeam).toHaveBeenCalledWith({ invitationId: 7 });
      expect(component.invitations()[0].status).toBe('ACCEPTED');
      expect(component.successMsg()).not.toBe('');
      
      vi.advanceTimersByTime(3500);
      expect(component.successMsg()).toBe('');
    });

    it('should execute rejection requests', () => {
      window.confirm = vi.fn().mockReturnValue(true);
      component.invitations.set([{ id: 8, status: 'PENDING', role: 'CTO' } as any]);
      teamServiceSpy.rejectInvitation.mockReturnValue(of({}));
      
      component.reject({ id: 8 } as any);
      
      expect(teamServiceSpy.rejectInvitation).toHaveBeenCalledWith(8);
      expect(component.invitations()[0].status).toBe('REJECTED');
    });

    it('should prevent rejection if unconfirmed by blocking calls', () => {
      window.confirm = vi.fn().mockReturnValue(false);
      component.reject({ id: 8 } as any);
      expect(teamServiceSpy.rejectInvitation).not.toHaveBeenCalled();
    });

    it('should handle api error failure on actions cleanly', () => {
      component.invitations.set([{ id: 9, status: 'PENDING', role: 'CTO' } as any]);
      teamServiceSpy.joinTeam.mockReturnValue(throwError(() => ({ error: 'ErrorJoin' })));
      
      component.accept({ id: 9 } as any);
      
      expect(component.acting()).toBeNull();
      expect(component.errorMsg()).toBe('ErrorJoin');
      expect(component.invitations()[0].status).toBe('PENDING'); // No change array
    });
  });

  describe('UI Formatting', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(InvitationsComponent);
      component = fixture.componentInstance;
    });

    it('should split arrays correctly based on response', () => {
      component.invitations.set([
        { id: 1, status: 'PENDING', role: 'CTO' } as any,
        { id: 2, status: 'ACCEPTED', role: 'CTO' } as any
      ]);
      expect(component.pending().length).toBe(1);
      expect(component.responded().length).toBe(1);
    });

    it('should format role names neatly', () => {
      expect(component.roleLabel('CTO')).toBe('CTO');
      expect(component.roleLabel('SUPER_NINJA')).toBe('SUPER NINJA');
      expect(component.statusLabel('PENDING')).toBe('Awaiting Response');
      expect(component.statusClass('REJECTED')).toBe('badge-danger');
    });

    it('should safely update current page references', () => {
      component.onPageChangePending(4);
      expect(component.currentPagePending()).toBe(4);
      
      component.onPageChangeResponded(2);
      expect(component.currentPageResponded()).toBe(2);
    });
  });
});
