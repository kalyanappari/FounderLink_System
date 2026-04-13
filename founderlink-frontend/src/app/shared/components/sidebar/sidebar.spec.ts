import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarComponent } from './sidebar';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { vi } from 'vitest';

describe('SidebarComponent', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let authServiceSpy: any;

  beforeEach(async () => {
    authServiceSpy = {
      role: vi.fn(),
      email: vi.fn(),
      logout: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Role-based visible items logic', () => {
    it('should map items correctly cleanly for a FOUNDER', () => {
      authServiceSpy.role.mockReturnValue('ROLE_FOUNDER');
      
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.visibleItems();
      // Test explicit exclusions / inclusions
      expect(items.some(i => i.label === 'Dashboard')).toBe(true);
      expect(items.some(i => i.label === 'My Startup')).toBe(true);
      expect(items.some(i => i.label === 'User Network')).toBe(false); // Admin only
      expect(items.some(i => i.label === 'Payments')).toBe(false); // Investor only
      expect(items.some(i => i.label === 'Invitations')).toBe(false); // Cofounder only
    });

    it('should map items correctly cleanly for an INVESTOR', () => {
      authServiceSpy.role.mockReturnValue('ROLE_INVESTOR');
      
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.visibleItems();
      expect(items.some(i => i.label === 'Payments')).toBe(true);
      expect(items.some(i => i.label === 'Portfolio')).toBe(true);
      expect(items.some(i => i.label === 'Team')).toBe(false);
      expect(items.some(i => i.label === 'Wallet')).toBe(false);
    });

    it('should map items correctly cleanly for a COFOUNDER', () => {
      authServiceSpy.role.mockReturnValue('ROLE_COFOUNDER');
      
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.visibleItems();
      expect(items.some(i => i.label === 'Invitations')).toBe(true);
      expect(items.some(i => i.label === 'Team')).toBe(true);
      expect(items.some(i => i.label === 'Investments')).toBe(false);
    });

    it('should map items correctly cleanly for an ADMIN', () => {
      authServiceSpy.role.mockReturnValue('ROLE_ADMIN');
      
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.visibleItems();
      expect(items.some(i => i.label === 'User Network')).toBe(true);
      expect(items.some(i => i.label === 'Portfolio')).toBe(false);
    });

    it('should fail silently and return all paths if role is undefined', () => {
      authServiceSpy.role.mockReturnValue(undefined);
      
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const items = component.visibleItems();
      // Should show maximum entries when no role exists (fallback logic)
      expect(items.length).toBe(12);
    });
  });

  describe('Events', () => {
    beforeEach(() => {
      authServiceSpy.role.mockReturnValue('ROLE_FOUNDER');
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should emit close event on nav click', () => {
      const emitSpy = vi.spyOn(component.closeMenu, 'emit');
      component.onNavClick();
      expect(emitSpy).toHaveBeenCalled();
    });
  });
});
