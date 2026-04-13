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
      getAll: vi.fn(),
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
  });

  describe('Initialization and Loading', () => {
    it('should initialize and load startups', () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [mockStartup] }));
      
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges(); // calls ngOnInit -> loadStartups
      
      expect(startupServiceSpy.getAll).toHaveBeenCalled();
      expect(component.allStartups()).toEqual([mockStartup]);
      expect(component.availableIndustries()).toEqual(['SaaS']);
      expect(component.loading()).toBe(false);
      expect(component.errorMsg()).toBe('');
    });

    it('should handle load error gracefully', () => {
      startupServiceSpy.getAll.mockReturnValue(throwError(() => ({ error: 'Error loading' })));
      
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.allStartups()).toEqual([]);
      expect(component.errorMsg()).toBe('Error loading');
      expect(component.loading()).toBe(false);
    });
  });

  describe('Pagination and Display', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: Array(20).fill(mockStartup) }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should slice data based on page size', () => {
      expect(component.allStartups().length).toBe(20);
      expect(component.paginatedStartups().length).toBe(12); // page 1
    });

    it('should change page and scroll to top', () => {
      window.scrollTo = vi.fn();
      component.onPageChange(2);
      expect(component.currentPage()).toBe(2);
      expect(component.paginatedStartups().length).toBe(8); // items 13-20
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
    });
  });

  describe('Filtering', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should return false if no filters are applied', () => {
      expect(component.hasFilters).toBe(false);
    });

    it('should return true if any filter is applied', () => {
      component.selectedStage = 'MVP';
      expect(component.hasFilters).toBe(true);
    });

    it('should apply filters and call search API', () => {
      component.selectedIndustry = 'Fintech';
      component.minFunding = '1000';
      startupServiceSpy.search.mockReturnValue(of({ data: [mockStartup] }));

      component.applyFilters();

      expect(component.loading()).toBe(false);
      expect(startupServiceSpy.search).toHaveBeenCalledWith({
        industry: 'Fintech',
        minFunding: 1000
      });
      expect(component.allStartups()).toEqual([mockStartup]);
    });

    it('should handle search error', () => {
      component.selectedIndustry = 'Fintech';
      startupServiceSpy.search.mockReturnValue(throwError(() => ({ error: 'Bad filter' })));

      component.applyFilters();

      expect(component.errorMsg()).toBe('Bad filter');
      expect(component.loading()).toBe(false);
    });

    it('should clear filters and reload', () => {
      startupServiceSpy.getAll.mockClear();
      component.selectedStage = 'IDEA';
      component.clearFilters();

      expect(component.selectedStage).toBe('');
      expect(component.hasFilters).toBe(false);
      expect(startupServiceSpy.getAll).toHaveBeenCalled();
    });
  });

  describe('Roles and Badges', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
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
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
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
      startupServiceSpy.getAll.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(StartupsComponent);
      component = fixture.componentInstance;
      
      component.messageFounder(42);
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard/messages'], { queryParams: { user: 42 } });
    });
  });
});
