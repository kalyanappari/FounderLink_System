import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StartupsComponent } from './startups';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { StartupService } from '../../core/services/startup.service';
import { InvestmentService } from '../../core/services/investment.service';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Component, Input, Output, EventEmitter } from '@angular/core';


describe('StartupsComponent', () => {
  let component: StartupsComponent;
  let fixture: ComponentFixture<StartupsComponent>;
  let startupServiceSpy: any;
  let investmentServiceSpy: any;
  let authServiceSpy: any;
  let router: Router;

  const mockStartup = {
    id: 1, name: 'Test Startup', industry: 'SaaS', stage: 'MVP'
  };

  beforeEach(async () => {
    startupServiceSpy = {
      getAll: vi.fn().mockReturnValue(of({ data: [] })),
      search: vi.fn()
    };
    investmentServiceSpy = {
      create: vi.fn()
    };
    authServiceSpy = {
      role: vi.fn().mockReturnValue('INVESTOR')
    };



    await TestBed.configureTestingModule({
      imports: [StartupsComponent, FormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: StartupService, useValue: startupServiceSpy },
        { provide: InvestmentService, useValue: investmentServiceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  describe('Initialization and Loading', () => {
    it('should load focus data via effect and industries via ngOnInit', () => {
      // Setup specific mocks for this test
      startupServiceSpy.search.mockReturnValue(of({ data: [mockStartup], totalElements: 1 }));
      startupServiceSpy.getAll.mockReturnValue(of({ data: [mockStartup] }));
      
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      
      // fixture.detectChanges() runs ngOnInit (getAll) AND the constructor effect (search)
      fixture.detectChanges(); 
      
      expect(startupServiceSpy.search).toHaveBeenCalledWith({}, 0, 12);
      expect(startupServiceSpy.getAll).toHaveBeenCalledWith(0, 100);
      expect(component.allStartups()).toEqual([mockStartup]);
      expect(component.availableIndustries()).toEqual(['SaaS']);
      expect(component.loading()).toBe(false);
    });

    it('should handle API errors gracefully', () => {
      startupServiceSpy.search.mockReturnValue(throwError(() => ({ error: 'Fail' })));
      
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.errorMsg()).toBe('Fail');
      expect(component.loading()).toBe(false);
    });
  });

  describe('Pagination Logic', () => {
    beforeEach(() => {
      startupServiceSpy.search.mockReturnValue(of({ data: [], totalElements: 50 }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should trigger a new search when page becomes 2', () => {
      window.scrollTo = vi.fn();
      startupServiceSpy.search.mockClear();
      startupServiceSpy.search.mockReturnValue(of({ data: [], totalElements: 50 }));

      component.onPageChange(2);
      fixture.detectChanges(); // Effect picks up the page change

      expect(component.currentPage()).toBe(2);
      expect(startupServiceSpy.search).toHaveBeenCalledWith({}, 1, 12);
    });
  });

  describe('Reactive Filtering', () => {
    beforeEach(() => {
      startupServiceSpy.search.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should automatically search when industry signal changes', () => {
      startupServiceSpy.search.mockClear();
      startupServiceSpy.search.mockReturnValue(of({ data: [mockStartup], totalElements: 1 }));

      component.selectedIndustry.set('Tech');
      fixture.detectChanges(); // Effect picks up filter change

      expect(startupServiceSpy.search).toHaveBeenCalledWith({ industry: 'Tech' }, 0, 12);
    });

    it('should reset to page 1 when applyFilters is called', () => {
      component.currentPage.set(5);
      component.applyFilters();
      expect(component.currentPage()).toBe(1);
    });

    it('should clear all signals when clearFilters is called', () => {
      component.selectedStage.set('IDEA');
      component.clearFilters();
      
      expect(component.selectedStage()).toBe('');
      expect(component.currentPage()).toBe(1);
    });
  });

  describe('Roles and Badges', () => {
    beforeEach(() => {
      startupServiceSpy.search.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should identify investor roles', () => {
      authServiceSpy.role.mockReturnValue('INVESTOR');
      expect(component.isInvestor).toBe(true);
      expect(component.isFounder).toBe(false);
      
      authServiceSpy.role.mockReturnValue('ROLE_INVESTOR');
      expect(component.isInvestor).toBe(true);
    });

    it('should correctly format currency', () => {
      expect(component.formatCurrency(50000)).toContain('50,000');
    });

    it('should map stage classes', () => {
      expect(component.stageClass('MVP')).toBe('badge-info');
      expect(component.stageClass('SCALING')).toBe('badge-success');
    });
  });

  describe('Investment Modal', () => {
    beforeEach(() => {
      startupServiceSpy.search.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      vi.useFakeTimers();
    });
    
    afterEach(() => {
      vi.useRealTimers();
    });

    it('should open and close invest modal', () => {
      component.openInvestModal(mockStartup as any);
      expect(component.investModal()).toEqual(mockStartup);
      expect(component.investAmount).toBe(0);

      component.closeInvestModal();
      expect(component.investModal()).toBeNull();
    });

    it('should block submit if amount < 1000', () => {
      component.openInvestModal(mockStartup as any);
      component.investAmount = 500;
      component.submitInvestment();

      expect(component.investError()).toBe('Minimum investment is ₹1,000.');
      expect(investmentServiceSpy.create).not.toHaveBeenCalled();
    });

    it('should submit investment successfully', () => {
      component.openInvestModal(mockStartup as any);
      component.investAmount = 5000;
      investmentServiceSpy.create.mockReturnValue(of({ data: {} }));

      component.submitInvestment();

      expect(investmentServiceSpy.create).toHaveBeenCalledWith({ startupId: 1, amount: 5000 });
      expect(component.investing()).toBe(false);
      expect(component.investSuccess()).toContain('successfully');
      
      vi.advanceTimersByTime(2500);
      expect(component.investModal()).toBeNull(); // should be closed after timeout
    });

    it('should handle investment error', () => {
      component.openInvestModal(mockStartup as any);
      component.investAmount = 5000;
      investmentServiceSpy.create.mockReturnValue(throwError(() => ({ error: 'Validation failed' })));

      component.submitInvestment();

      expect(component.investing()).toBe(false);
      expect(component.investError()).toBe('Validation failed');
    });
  });

  describe('Message routing', () => {
    it('should route to messages with founder ID', () => {
      startupServiceSpy.search.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      
      component.messageFounder(42);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard/messages'], { queryParams: { user: 42 } });
    });
  });
});
