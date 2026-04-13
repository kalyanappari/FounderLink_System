import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StartupExplorerComponent } from './startup-explorer';
import { StartupService } from '../../../core/services/startup.service';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Component, Input, Output, EventEmitter } from '@angular/core';



describe('StartupExplorerComponent', () => {
  let component: StartupExplorerComponent;
  let fixture: ComponentFixture<StartupExplorerComponent>;
  let startupServiceSpy: any;

  beforeEach(async () => {
    startupServiceSpy = {
      getAll: vi.fn()
    };



    await TestBed.configureTestingModule({
      imports: [StartupExplorerComponent],
      providers: [
        provideRouter([]),
        { provide: StartupService, useValue: startupServiceSpy }
      ]
    }).compileComponents();
  });

  describe('Loading and initialization', () => {
    it('should load startups successfully based on effect', () => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [{ id: 1 }], totalElements: 1 }));
      
      fixture = TestBed.createComponent(StartupExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(startupServiceSpy.getAll).toHaveBeenCalledWith(0, 9);
      expect(component.startups().length).toBe(1);
      expect(component.totalElements()).toBe(1);
      expect(component.loading()).toBe(false);
      expect(component.error()).toBeNull();
    });

    it('should handle startup fetch error', () => {
      startupServiceSpy.getAll.mockReturnValue(throwError(() => new Error('API failure')));
      
      fixture = TestBed.createComponent(StartupExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.loading()).toBe(false);
      expect(component.error()).toBe('Failed to load global startups.');
      expect(component.startups().length).toBe(0);
    });
  });

  describe('Pagination and utilities', () => {
    beforeEach(() => {
      startupServiceSpy.getAll.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(StartupExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should handle manual loadStartups call', () => {
      startupServiceSpy.getAll.mockClear();
      component.loadStartups();
      expect(startupServiceSpy.getAll).toHaveBeenCalledWith(0, 9);
    });

    it('should change page from the pagination event', () => {
      window.scrollTo = vi.fn();
      startupServiceSpy.getAll.mockClear();
      
      component.onPageChange(2);
      fixture.detectChanges(); // force effect flush

      expect(component.currentPage()).toBe(2);
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
      expect(startupServiceSpy.getAll).toHaveBeenCalledWith(1, 9);
    });

    it('should format currency accurately', () => {
      expect(component.formatCurrency(120000)).toContain('1,20,000');
    });

    it('should return correct stage labels', () => {
      expect(component.getStageLabel('MVP')).toBe('Product Prototype');
      expect(component.getStageLabel('IDEA')).toBe('Ideation Phase');
      expect(component.getStageLabel('UNKNOWN')).toBe('UNKNOWN');
    });
  });
});
