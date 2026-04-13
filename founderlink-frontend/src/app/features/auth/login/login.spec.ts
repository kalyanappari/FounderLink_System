import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginComponent } from './login';
import { ReactiveFormsModule } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: any;
  let userServiceSpy: any;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = {
      login: vi.fn()
    };
    userServiceSpy = {
      getPublicStats: vi.fn().mockReturnValue(of({ founders: 1, investors: 2, cofounders: 3 }))
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UserService, useValue: userServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    
    component = fixture.componentInstance;
    fixture.detectChanges(); // triggers ngOnInit
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
      expect(component.stats()).toEqual({ founders: 1, investors: 2, cofounders: 3 });
    });

    it('should handle error when loading public stats gracefully', () => {
      vi.spyOn(console, 'warn').mockImplementation(() => {});
      userServiceSpy.getPublicStats.mockReturnValueOnce(throwError(() => new Error('API down')));
      
      // Re-create component to trigger fresh ngOnInit
      fixture = TestBed.createComponent(LoginComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(console.warn).toHaveBeenCalledWith('Failed to load public stats');
      // Should retain default stats set in the signal
      expect(component.stats()).toEqual({ founders: 350, investors: 200, cofounders: 120 });
    });
  });

  describe('Form validation', () => {
    it('should initialize form with empty fields', () => {
      expect(component.form.get('email')?.value).toBe('');
      expect(component.form.get('password')?.value).toBe('');
      expect(component.form.valid).toBe(false);
    });

    it('should validate email format and required', () => {
      const email = component.form.get('email')!;
      
      email.setValue('');
      expect(email.hasError('required')).toBe(true);

      email.setValue('invalid-email');
      expect(email.hasError('email')).toBe(true);

      email.setValue('alice@example.com');
      expect(email.errors).toBeNull();
    });

    it('should validate password required', () => {
      const password = component.form.get('password')!;
      expect(password.hasError('required')).toBe(true);

      password.setValue('Secret1!');
      expect(password.errors).toBeNull();
    });
  });

  describe('onSubmit()', () => {
    it('should mark form as touched and not call API if form is invalid', () => {
      component.form.get('email')?.setValue('');
      component.form.get('password')?.setValue('');
      
      component.onSubmit();
      
      expect(component.form.touched).toBe(true);
      expect(authServiceSpy.login).not.toHaveBeenCalled();
    });

    it('should set loading, call auth service, and navigate on success', () => {
      component.form.setValue({ email: 'alice@example.com', password: 'open' });
      authServiceSpy.login.mockReturnValue(of({ token: 'abc' }));

      component.onSubmit();

      expect(component.loading()).toBe(false); // set to false inside next
      expect(authServiceSpy.login).toHaveBeenCalledWith({ email: 'alice@example.com', password: 'open' });
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('should stop loading and set errorMsg on auth failure', () => {
      component.form.setValue({ email: 'bob@example.com', password: 'wrong' });
      const mockError = { error: { message: 'Bad credentials' } };
      authServiceSpy.login.mockReturnValue(throwError(() => mockError));

      component.onSubmit();

      expect(component.loading()).toBe(false);
      expect(component.errorMsg()).toBe('Bad credentials');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should fall back to generic error message if error.message is missing', () => {
      component.form.setValue({ email: 'bob@example.com', password: 'wrong' });
      authServiceSpy.login.mockReturnValue(throwError(() => ({ status: 500 })));

      component.onSubmit();

      expect(component.errorMsg()).toBe('Invalid email or password. Please try again.');
    });
  });
});
