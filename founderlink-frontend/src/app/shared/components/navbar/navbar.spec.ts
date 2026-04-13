import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavbarComponent } from './navbar';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ThemeService } from '../../../core/services/theme.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let authServiceSpy: any;
  let notificationServiceSpy: any;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = {
      role: vi.fn(),
      userId: vi.fn(),
      email: vi.fn(),
      logout: vi.fn()
    };
    
    notificationServiceSpy = {
      getMyUnreadNotifications: vi.fn(),
      getMyNotifications: vi.fn(),
      markAsRead: vi.fn()
    };

    const themeServiceSpy = {
      isCrystal: vi.fn().mockReturnValue(false),
      themeMode: vi.fn().mockReturnValue('onyx'),
      toggleTheme: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
        { provide: ThemeService, useValue: themeServiceSpy }
      ]
    }).compileComponents();
    
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Lifecycle and notifications', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should initialize and load unread count', () => {
      authServiceSpy.userId.mockReturnValue(5);
      authServiceSpy.role.mockReturnValue('ROLE_FOUNDER');
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 3 }));
      
      fixture = TestBed.createComponent(NavbarComponent);
      component = fixture.componentInstance;
      
      // Need to set input explicitly since it's a signal input
      fixture.componentRef.setInput('pageTitle', 'Dashboard Test');
      fixture.detectChanges();

      expect(component.unreadCount()).toBe(3);
      expect(component.role()).toBe('FOUNDER');
      expect(component.getRoleName()).toBe('FOUNDER');
      
      // Fast forward to test interval
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 5 }));
      vi.advanceTimersByTime(35000);
      expect(component.unreadCount()).toBe(5);
    });

    it('should ignore load if user is disconnected', () => {
      authServiceSpy.userId.mockReturnValue(null);
      fixture = TestBed.createComponent(NavbarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(notificationServiceSpy.getMyUnreadNotifications).not.toHaveBeenCalled();
    });
  });

  describe('Notification Panel Interactions', () => {
    beforeEach(() => {
      authServiceSpy.userId.mockReturnValue(5);
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 0 }));
      fixture = TestBed.createComponent(NavbarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should toggle panel and load notifications if opening', () => {
      notificationServiceSpy.getMyNotifications.mockReturnValue(of({ data: [{ id: 1 }] }));
      
      component.toggleNotifPanel();
      
      expect(component.showNotifPanel()).toBe(true);
      expect(notificationServiceSpy.getMyNotifications).toHaveBeenCalled();
      expect(component.notifications().length).toBe(1);
    });

    it('should properly mark notifications as read', () => {
      component.notifications.set([{ id: 10, read: false, type: 'INFO', message: '' } as any]);
      component.unreadCount.set(1);
      notificationServiceSpy.markAsRead.mockReturnValue(of({ data: true }));

      component.markRead(10);
      
      expect(notificationServiceSpy.markAsRead).toHaveBeenCalledWith(10);
      expect(component.notifications()[0].read).toBe(true);
      expect(component.unreadCount()).toBe(0);
    });

    it('should handle notification clicks and routing when type is MESSAGE', () => {
      component.showNotifPanel.set(true);
      const n: any = { id: 10, read: false, type: 'MESSAGE', message: 'Hi from #42' };
      notificationServiceSpy.markAsRead.mockReturnValue(of({ data: true }));
      
      component.onNotificationClick(n);

      expect(component.showNotifPanel()).toBe(false);
      expect(notificationServiceSpy.markAsRead).toHaveBeenCalledWith(10);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard/messages'], expect.objectContaining({ queryParams: { user: '42' } }));
    });

    it('should handle notification clicks and routing for non-message types', () => {
      component.showNotifPanel.set(true);
      const n: any = { id: 11, read: true, type: 'INVESTMENT_UPDATE', message: 'Success' };
      
      component.onNotificationClick(n);

      expect(notificationServiceSpy.markAsRead).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard/notifications']);
    });

    it('should show empty state when no notifications exist', () => {
      component.showNotifPanel.set(true);
      component.notifications.set([]);
      fixture.detectChanges();
      
      const emptyMsg = fixture.nativeElement.querySelector('.notif-empty');
      expect(emptyMsg).toBeTruthy();
      expect(emptyMsg.textContent).toContain('No notifications yet');
    });

    it('should close panel when clicking overlay', () => {
      component.showNotifPanel.set(true);
      fixture.detectChanges();
      
      const overlay = fixture.nativeElement.querySelector('.notif-overlay');
      overlay.click();
      
      expect(component.showNotifPanel()).toBe(false);
    });
  });

  describe('Role-based UI Branches', () => {
    beforeEach(() => {
      authServiceSpy.userId.mockReturnValue(5);
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 0 }));
    });

    it('should render INVESTOR specific links', () => {
      authServiceSpy.role.mockReturnValue('INVESTOR');
      fixture = TestBed.createComponent(NavbarComponent);
      fixture.detectChanges();

      const html = fixture.nativeElement.innerHTML;
      expect(html).toContain('DISCOVERY');
      expect(html).toContain('PORTFOLIO');
      expect(html).not.toContain('STARTUP');
    });

    it('should render COFOUNDER specific links', () => {
      authServiceSpy.role.mockReturnValue('COFOUNDER');
      fixture = TestBed.createComponent(NavbarComponent);
      fixture.detectChanges();

      const html = fixture.nativeElement.innerHTML;
      expect(html).toContain('INVITATIONS');
      expect(html).toContain('TEAMS');
    });
  });

  describe('Theme Toggle UI', () => {
    it('should show moon icon in crystal mode (dark mode)', () => {
      const themeS = TestBed.inject(ThemeService);
      vi.spyOn(themeS, 'isCrystal').mockReturnValue(true);
      
      fixture = TestBed.createComponent(NavbarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      const themeToggle = fixture.nativeElement.querySelector('.theme-toggle');
      expect(themeToggle).toBeTruthy();
    });
  });

  describe('Utilities', () => {
    beforeEach(() => {
      authServiceSpy.userId.mockReturnValue(5);
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 0 }));
      fixture = TestBed.createComponent(NavbarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should map icons accurately for all switch cases', () => {
      expect(component.getIcon('INVESTMENT_UPDATE')).toBe('investment');
      expect(component.getIcon('TEAM_INVITE')).toBe('team');
      expect(component.getIcon('MESSAGE_RECEIVED')).toBe('message');
      expect(component.getIcon('PAYMENT_SUCCESS')).toBe('payment');
      expect(component.getIcon('STARTUP_APPROVED')).toBe('startup');
      expect(component.getIcon('REGISTERED_USER')).toBe('user');
      expect(component.getIcon('PASSWORD_RESET')).toBe('lock');
      expect(component.getIcon('RANDOM')).toBe('info');
    });

    it('should emit menu toggle event when menu button clicked', () => {
      const emitSpy = vi.spyOn(component.menuToggle, 'emit');
      const menuBtn = fixture.nativeElement.querySelector('.menu-btn');
      menuBtn.click();
      expect(emitSpy).toHaveBeenCalled();
    });
  });
});
