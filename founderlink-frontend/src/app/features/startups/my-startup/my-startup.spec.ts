import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MyStartupComponent } from './my-startup';
import { ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { StartupService } from '../../../core/services/startup.service';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Component, Input, Output, EventEmitter } from '@angular/core';



describe('MyStartupComponent', () => {
  let component: MyStartupComponent;
  let fixture: ComponentFixture<MyStartupComponent>;
  let startupServiceSpy: any;
  let authServiceSpy: any;

  const mockStartup = {
    id: 99, name: 'OpenAI', description: 'AI', industry: 'Tech',
    problemStatement: 'P', solution: 'S', fundingGoal: 100000, stage: 'MVP'
  };

  beforeEach(async () => {
    startupServiceSpy = {
      getMyStartups: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn()
    };

    authServiceSpy = {
      role: vi.fn().mockReturnValue('FOUNDER')
    };



    await TestBed.configureTestingModule({
      imports: [MyStartupComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: StartupService, useValue: startupServiceSpy }
      ]
    }).compileComponents();

    // Default mock to prevent crash on ngOnInit
    startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
  });

  afterEach(() => vi.restoreAllMocks());

  describe('Initialization and Listing', () => {
    it('should initialize and fetch the founders startups', () => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [mockStartup] }));

      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(startupServiceSpy.getMyStartups).toHaveBeenCalled();
      expect(component.startups()).toEqual([mockStartup]);
      expect(component.loading()).toBe(false);
    });

    it('should handle startup fetch error gracefully', () => {
      startupServiceSpy.getMyStartups.mockReturnValue(throwError(() => ({ error: 'Load fail' })));

      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.errorMsg()).toBe('Load fail');
      expect(component.loading()).toBe(false);
      expect(component.startups()).toEqual([]);
    });
  });

  describe('Form states', () => {
    beforeEach(() => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should open create form', () => {
      component.openCreate();
      expect(component.showForm()).toBe(true);
      expect(component.editingId()).toBeNull();
      expect(component.form.pristine).toBe(true);
      expect(component.f['name'].value).toBe(null);
    });

    it('should open edit form with populated fields', () => {
      component.openEdit(mockStartup as any);
      expect(component.showForm()).toBe(true);
      expect(component.editingId()).toBe(99);
      expect(component.f['name'].value).toBe('OpenAI');
    });

    it('should cancel form', () => {
      component.openEdit(mockStartup as any);
      component.cancelForm();
      expect(component.showForm()).toBe(false);
      expect(component.editingId()).toBeNull();
    });
  });

  describe('CRUD Operations', () => {
    beforeEach(() => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should validate form constraints on submit', () => {
      component.openCreate();
      component.form.patchValue({ name: 'Short' });
      // Missing required fields
      component.onSubmit();
      expect(component.form.touched).toBe(true);
      expect(startupServiceSpy.create).not.toHaveBeenCalled();
    });

    it('should create new startup', () => {
      component.openCreate();
      const payload = { ...mockStartup };
      delete (payload as any).id;
      component.form.patchValue(payload);
      startupServiceSpy.create.mockReturnValue(of({}));
      
      component.onSubmit();

      expect(startupServiceSpy.create).toHaveBeenCalledWith(payload);
      expect(component.successMsg()).toContain('created');
      expect(component.showForm()).toBe(false);
      
      vi.advanceTimersByTime(3000);
      expect(component.successMsg()).toBe('');
    });

    it('should update existing startup', () => {
      component.openEdit(mockStartup as any);
      component.form.patchValue({ fundingGoal: 200000 });
      startupServiceSpy.update.mockReturnValue(of({}));

      component.onSubmit();

      // Ensure we call update with the correct ID
      expect(startupServiceSpy.update).toHaveBeenCalledWith(99, expect.objectContaining({ fundingGoal: 200000 }));
      expect(component.successMsg()).toContain('updated');
      expect(component.showForm()).toBe(false);
    });

    it('should delete startup after confirmation', () => {
      window.confirm = vi.fn().mockReturnValue(true);
      startupServiceSpy.delete.mockReturnValue(of({}));
      component.startups.set([{ id: 88, name: 'X' }] as any[]);

      component.deleteStartup(88);

      expect(window.confirm).toHaveBeenCalled();
      expect(startupServiceSpy.delete).toHaveBeenCalledWith(88);
      expect(component.startups().length).toBe(0);
      
      vi.advanceTimersByTime(3000);
      expect(component.successMsg()).toBe('');
    });

    it('should NOT delete startup if confirmation is declined', () => {
      window.confirm = vi.fn().mockReturnValue(false);
      component.deleteStartup(88);
      expect(startupServiceSpy.delete).not.toHaveBeenCalled();
    });

    it('should handle delete failure', () => {
      window.confirm = vi.fn().mockReturnValue(true);
      startupServiceSpy.delete.mockReturnValue(throwError(() => ({ error: 'Error' })));
      component.deleteStartup(88);
      
      expect(component.errorMsg()).toBe('Error');
      expect(component.deleting()).toBeNull();
    });
  });

  describe('Pagination and Display', () => {
    beforeEach(() => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should format labels correctly', () => {
      expect(component.stageLabel('MVP')).toBe('MVP');
      expect(component.stageLabel('EARLY_TRACTION')).toBe('Early Traction');
    });

    it('should format stage badge colors correctly', () => {
      expect(component.stageClass('IDEA')).toBe('badge-gray');
      expect(component.stageClass('SCALING')).toBe('badge-success');
    });

    it('should update pagination accurately and jump cursor to top', () => {
      window.scrollTo = vi.fn();
      component.onPageChange(2);
      expect(component.currentPage()).toBe(2);
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
    });
  });

  describe('Template UI Verification', () => {
    it('should render the creation form when showForm is true and editingId is null', async () => {
      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      component.showForm.set(true);
      component.editingId.set(null);
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      const title = fixture.nativeElement.querySelector('.form-card-header h3');
      expect(title.textContent).toContain('Create');
      expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
    });

    it('should show success alert when successMsg is set', async () => {
      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      component.successMsg.set('Operation Success');
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      const alert = fixture.nativeElement.querySelector('.alert-success');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('Operation Success');
    });

    it('should show error alert when errorMsg is set', async () => {
      // Complete init first
      fixture = TestBed.createComponent(MyStartupComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      component.errorMsg.set('Api Crash');
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      
      const alert = fixture.nativeElement.querySelector('.alert-error');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('Api Crash');
    });
  });
});
