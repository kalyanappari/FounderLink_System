import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard';
import { provideRouter, Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { SidebarComponent } from '../../shared/components/sidebar/sidebar';
import { NavbarComponent } from '../../shared/components/navbar/navbar';
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Subject } from 'rxjs';


describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let routerEventsSubject: Subject<any>;
  let originalInnerWidth: number;

  beforeEach(async () => {
    routerEventsSubject = new Subject<any>();
    
    const mockRouter = {
      events: routerEventsSubject.asObservable(),
      navigate: vi.fn(),
      url: '/dashboard'
    };

    const mockAuthService = { role: () => 'FOUNDER', userId: () => 1, email: () => 'f@test.com' };
    const mockThemeService = { themeMode: () => 'onyx', isCrystal: () => false };


    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: {} },
        { provide: AuthService, useValue: mockAuthService },
        { provide: ThemeService, useValue: mockThemeService }
      ]
    }).compileComponents();

    // Mock window innerWidth for mobile tests
    originalInnerWidth = window.innerWidth;
  });

  afterEach(() => {
    Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: originalInnerWidth });
    vi.restoreAllMocks();
  });

  describe('Desktop view', () => {
    beforeEach(() => {
      Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: 1024 });
      fixture = TestBed.createComponent(DashboardComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should initialize with sidebar open on desktop', () => {
      expect(component.sidebarOpen()).toBe(true);
    });

    it('should toggle sidebar', () => {
      component.toggleSidebar();
      expect(component.sidebarOpen()).toBe(false);
      component.toggleSidebar();
      expect(component.sidebarOpen()).toBe(true);
    });

    it('should update pageTitle on NavigationEnd without closing sidebar', () => {
      routerEventsSubject.next(new NavigationEnd(1, '/dashboard/startups', '/dashboard/startups'));
      
      expect(component.pageTitle()).toBe('Startups');
      expect(component.sidebarOpen()).toBe(true); // Should not auto-close on desktop
    });

    it('should strip query params when resolving title', () => {
      routerEventsSubject.next(new NavigationEnd(1, '/dashboard/messages?user=42', '/dashboard/messages?user=42'));
      expect(component.pageTitle()).toBe('Messages');
    });

    it('should fallback to default title if route not mapped', () => {
      routerEventsSubject.next(new NavigationEnd(1, '/dashboard/unknown', '/dashboard/unknown'));
      expect(component.pageTitle()).toBe('Dashboard');
    });
  });

  describe('Mobile view', () => {
    beforeEach(() => {
      Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: 375 });
      fixture = TestBed.createComponent(DashboardComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should initialize with sidebar closed on mobile', () => {
      expect(component.sidebarOpen()).toBe(false);
    });

    it('should auto-close sidebar on NavigationEnd on mobile', () => {
      component.sidebarOpen.set(true); // Manually open it
      expect(component.sidebarOpen()).toBe(true);

      routerEventsSubject.next(new NavigationEnd(1, '/dashboard/profile', '/dashboard/profile'));
      
      expect(component.pageTitle()).toBe('Profile');
      expect(component.sidebarOpen()).toBe(false); // Should close on nav
    });
  });
});
