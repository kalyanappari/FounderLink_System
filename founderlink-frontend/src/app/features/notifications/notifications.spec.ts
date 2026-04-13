import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationsComponent } from './notifications';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { NotificationService } from '../../core/services/notification.service';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Component, Input, Output, EventEmitter } from '@angular/core';

describe('NotificationsComponent', () => {
  let component: NotificationsComponent;
  let fixture: ComponentFixture<NotificationsComponent>;
  let notificationServiceSpy: any;
  let routerSpy: any;

  beforeEach(async () => {
    notificationServiceSpy = {
      getMyNotifications: vi.fn(),
      getMyUnreadNotifications: vi.fn(),
      markAsRead: vi.fn()
    };
    routerSpy = { navigate: vi.fn() };


    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: {} },
        { provide: ThemeService, useValue: { isCrystal: () => false } },
        { provide: NotificationService, useValue: notificationServiceSpy }
      ]
    }).compileComponents();
  });

  describe('Lifecycle and Data Fetching', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should initialize and poll unread count', () => {
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 5, data: [] }));
      notificationServiceSpy.getMyNotifications.mockReturnValue(of({ totalElements: 0, data: [] })); // For effect

      fixture = TestBed.createComponent(NotificationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges(); // ngOnInit

      expect(component.unreadCount()).toBe(5);
      
      // Fast forward interval manually
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 9, data: [] }));
      vi.advanceTimersByTime(35000); // 30 sec interval
      
      expect(component.unreadCount()).toBe(9);
    });

    it('should handle read vs unread vs all fetch type through effect', () => {
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({ totalElements: 0, data: [] }));
      notificationServiceSpy.getMyNotifications.mockReturnValue(of({ totalElements: 0, data: [] }));

      fixture = TestBed.createComponent(NotificationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges(); // Will trigger effect for initial 'all' load
      
      expect(notificationServiceSpy.getMyNotifications).toHaveBeenCalledWith(0, 10);
      
      // Update filter to unread
      notificationServiceSpy.getMyNotifications.mockClear();
      component.setFilter('unread');
      fixture.detectChanges();
      
      expect(notificationServiceSpy.getMyUnreadNotifications).toHaveBeenCalledWith(0, 10);
      expect(notificationServiceSpy.getMyNotifications).not.toHaveBeenCalled();

      // Update to read
      notificationServiceSpy.getMyUnreadNotifications.mockClear();
      notificationServiceSpy.getMyNotifications.mockReturnValue(of({ data: [{ id: 1, read: true, type: 'INFO' }, { id: 2, read: false, type: 'INFO' }] }));
      component.setFilter('read');
      fixture.detectChanges();

      expect(notificationServiceSpy.getMyNotifications).toHaveBeenCalledWith(0, 10);
      expect(component.notifications().length).toBe(1); // Mapped client-side
      expect(component.notifications()[0].id).toBe(1);
    });

    it('should handle errors gracefully during fetch', () => {
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({}));
      notificationServiceSpy.getMyNotifications.mockReturnValue(throwError(() => ({ error: 'G' })));
      
      fixture = TestBed.createComponent(NotificationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges(); 

      expect(component.errorMsg()).toBe('Failed to load notifications.');
      expect(component.loading()).toBe(false);
    });
  });

  describe('Interactions', () => {
    beforeEach(() => {
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({}));
      notificationServiceSpy.getMyNotifications.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(NotificationsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should mark completely individual notification as read', () => {
      component.notifications.set([{ id: 50, read: false, type: 'INFO', message: 'Test' } as any]);
      component.unreadCount.set(1);
      notificationServiceSpy.markAsRead.mockReturnValue(of({ data: true }));

      component.markAsRead(50);
      
      expect(notificationServiceSpy.markAsRead).toHaveBeenCalledWith(50);
      expect(component.notifications()[0].read).toBe(true);
      expect(component.unreadCount()).toBe(0);
    });

    it('should safely ignore already read marks', () => {
      component.notifications.set([{ id: 51, read: true, type: 'INFO', message: 'Test' } as any]);
      component.markAsRead(51);
      expect(notificationServiceSpy.markAsRead).not.toHaveBeenCalled();
    });

    it('should navigate to messages directly on MESSAGE type click', () => {
      component.notifications.set([{ id: 10, read: true, type: 'MESSAGE', message: 'New text #15' } as any]);
      
      component.onNotificationClick(component.notifications()[0]);

      expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard/messages'], { queryParams: { user: '15' } });
      expect(component.selectedNotification()).toBeNull(); // Doesn't open modal
    });

    it('should fall back to modal if MESSAGE type has no userId hash', () => {
      component.notifications.set([{ id: 11, read: true, type: 'MESSAGE', message: 'Hi without hash' } as any]);
      component.onNotificationClick(component.notifications()[0]);
      expect(component.selectedNotification()?.id).toBe(11);
    });

    it('should select component to modal if not MESSAGE type', () => {
      component.notifications.set([{ id: 10, read: true, type: 'SYSTEM', message: 'Hi' } as any]);
      
      component.onNotificationClick(component.notifications()[0]);

      expect(routerSpy.navigate).not.toHaveBeenCalled();
      expect(component.selectedNotification()?.id).toBe(10); // Opens modal
      
      component.closeModal();
      expect(component.selectedNotification()).toBeNull();
    });

    it('should mark all unread array elements as read explicitly', () => {
      component.notifications.set([
        { id: 1, read: false, type: 'A', message: 'X' } as any,
        { id: 2, read: false, type: 'B', message: 'Y' } as any
      ]);
      notificationServiceSpy.markAsRead.mockReturnValue(of({ data: true }));
      component.unreadCount.set(2);
      
      component.markAllAsRead();
      
      expect(notificationServiceSpy.markAsRead).toHaveBeenCalledTimes(2);
      expect(component.unreadCount()).toBe(0);
    });
  });

  describe('Utilities', () => {
    beforeEach(() => {
      notificationServiceSpy.getMyUnreadNotifications.mockReturnValue(of({}));
      notificationServiceSpy.getMyNotifications.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(NotificationsComponent);
      component = fixture.componentInstance;
    });

    it('should return correct CSS classes for icon maps based on substring match', () => {
      expect(component.getIcon('SOME_PAYMENT_THING')).toBe('payment');
      expect(component.getIcon('TEAM_INVITATION')).toBe('team');
      expect(component.getIcon('RANDOM')).toBe('info');
      expect(component.getIcon('STARTUP_STATUS')).toBe('startup');
      expect(component.getIcon('INVESTMENT_ALERT')).toBe('investment');
      expect(component.getIcon('MESSAGE_NEW')).toBe('message');
      expect(component.getIcon('REGISTERED_EVENT')).toBe('user');
      expect(component.getIcon('PASSWORD_CHANGED')).toBe('lock');
    });

    it('should format date strings logically based on proximity', () => {
      const now = new Date();
      expect(component.formatDate(now.toISOString())).toBe('Just now');
      expect(component.formatDate(new Date(now.getTime() - 10 * 60000).toISOString())).toBe('10m ago');
      expect(component.formatDate(new Date(now.getTime() - 3 * 3600000).toISOString())).toBe('3h ago');
      expect(component.formatDate(new Date(now.getTime() - 2 * 86400000).toISOString())).toBe('2d ago');
      
      const oldDate = new Date(2023, 0, 1);
      expect(component.formatDate(oldDate.toISOString())).toContain('2023');
    });

    it('should handle pagination changes', () => {
      window.scrollTo = vi.fn();
      component.onPageChange(2);
      expect(component.currentPage()).toBe(2);
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
    });
  });
});
