import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RegisterComponent } from './register';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let authServiceSpy: any;
  let userServiceSpy: any;
  let router: Router;
  let queryParamsSubject: BehaviorSubject<any>;

  beforeEach(async () => {
    authServiceSpy = {
      register: vi.fn()
    };
    userServiceSpy = {
      getPublicStats: vi.fn().mockReturnValue(of({ founders: 10, investors: 20, cofounders: 30 }))
    };
    queryParamsSubject = new BehaviorSubject({});

    await TestBed.configureTestingModule({
      imports: [RegisterComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UserService, useValue: userServiceSpy },
        { provide: ActivatedRoute, useValue: { queryParams: queryParamsSubject.asObservable() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Initialization', () => {
    it('should create the component', () => {
      expect(component).toBeTruthy();
    });

    it('should load public stats on init', () => {
      expect(userServiceSpy.getPublicStats).toHaveBeenCalled();
      expect(component.stats()).toEqual({ founders: 10, investors: 20, cofounders: 30 });
    });

    it('should lock role if query params provide a valid role', () => {
      queryParamsSubject.next({ role: 'INVESTOR' });
      fixture.detectChanges();

      expect(component.isRoleLocked).toBe(true);
      expect(component.form.get('role')?.value).toBe('INVESTOR');
      expect(component.selectedRole).toBe('Investor');
    });

    it('should NOT lock role if query param role is invalid', () => {
      queryParamsSubject.next({ role: 'HACKER' });
      fixture.detectChanges();

      expect(component.isRoleLocked).toBe(false);
      expect(component.form.get('role')?.value).toBe('');
      expect(component.selectedRole).toBe('');
    });
  });

  describe('Form validation', () => {
    it('should validate name required and minLength', () => {
      const name = component.form.get('name')!;
      name.setValue('');
      expect(name.hasError('required')).toBe(true);

      name.setValue('A');
      expect(name.hasError('minlength')).toBe(true);

      name.setValue('Abc');
      expect(name.errors).toBeNull();
    });

    it('should correctly format selectedRole from form value', () => {
      component.form.patchValue({ role: 'FOUNDER' });
      expect(component.selectedRole).toBe('Founder');

      component.form.patchValue({ role: 'COFOUNDER' });
      expect(component.selectedRole).toBe('Co-Founder');
    });
  });

  describe('onSubmit()', () => {
    it('should mark form as touched and stop if invalid', () => {
      component.form.patchValue({ name: '' });
      component.onSubmit();
      expect(component.form.touched).toBe(true);
      expect(authServiceSpy.register).not.toHaveBeenCalled();
    });

    it('should register successfully, set success msg, and redirect after timeout', () => {
      vi.useFakeTimers();
      component.form.setValue({
        name: 'Alice',
        email: 'alice@example.com',
        password: 'password123',
        role: 'FOUNDER'
      });
      authServiceSpy.register.mockReturnValue(of({ data: { message: 'Ok' } }));

      component.onSubmit();
      
      expect(component.loading()).toBe(false);
      expect(component.successMsg()).not.toBe('');
      
      vi.advanceTimersByTime(1800);
      
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
      vi.useRealTimers();
    });

    it('should handle registration failure gracefully', () => {
      component.form.setValue({
        name: 'Bob',
        email: 'bob@example.com',
        password: 'password123',
        role: 'INVESTOR'
      });
      authServiceSpy.register.mockReturnValue(throwError(() => ({
        error: { message: 'Email already exists' }
      })));

      component.onSubmit();

      expect(component.loading()).toBe(false);
      expect(component.errorMsg()).toBe('Email already exists');
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });
});
