import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ForgotPasswordComponent } from './forgot-password';
import { ReactiveFormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceSpy: any;

  beforeEach(async () => {
    authServiceSpy = {
      forgotPassword: vi.fn(),
      resetPassword: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Step 1: Request Pin', () => {
    it('should initialize on request step', () => {
      expect(component.step()).toBe('request');
      expect(component.requestForm.valid).toBe(false);
    });

    it('should mark requestForm invalid if email is missing', () => {
      component.sendPin();
      expect(component.requestForm.touched).toBe(true);
      expect(authServiceSpy.forgotPassword).not.toHaveBeenCalled();
    });

    it('should send pin and move to reset step on success', () => {
      component.requestForm.patchValue({ email: 'alice@example.com' });
      authServiceSpy.forgotPassword.mockReturnValue(of({ message: 'Sent' }));

      component.sendPin();

      expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith({ email: 'alice@example.com' });
      expect(component.step()).toBe('reset');
      expect(component.submittedEmail()).toBe('alice@example.com');
      // Should prefill the email in reset form
      expect(component.resetForm.get('email')?.value).toBe('alice@example.com');
    });

    it('should show error message when email is not found', () => {
      component.requestForm.patchValue({ email: 'foo@bar.com' });
      authServiceSpy.forgotPassword.mockReturnValue(throwError(() => ({
        error: { message: 'User not found' }
      })));

      component.sendPin();

      expect(component.step()).toBe('request');
      expect(component.errorMsg()).toBe('User not found');
    });
  });

  describe('Step 2: Reset Password', () => {
    beforeEach(() => {
      // Manually push to step 2 for these tests
      component.step.set('reset');
      component.resetForm.patchValue({
        email: 'alice@example.com',
        pin: '123456',
        newPassword: 'new-secure-pass'
      });
    });

    it('should mark resetForm invalid if passwords or pin are missing/short', () => {
      component.resetForm.patchValue({ pin: '12' }); // invalid pin
      component.resetPassword();
      expect(component.resetForm.touched).toBe(true);
      expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
    });

    it('should submit new password and show success message', () => {
      authServiceSpy.resetPassword.mockReturnValue(of({ message: 'Success' }));

      component.resetPassword();

      expect(authServiceSpy.resetPassword).toHaveBeenCalledWith({
        email: 'alice@example.com',
        pin: '123456',
        newPassword: 'new-secure-pass'
      });
      expect(component.successMsg()).toContain('Password reset successfully');
    });

    it('should show error when pin is invalid', () => {
      authServiceSpy.resetPassword.mockReturnValue(throwError(() => ({
        error: { message: 'Invalid PIN' }
      })));

      component.resetPassword();

      expect(component.errorMsg()).toBe('Invalid PIN');
      fixture.detectChanges();
      const alert = fixture.nativeElement.querySelector('.alert-error');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('Invalid PIN');
    });

    it('should show success message in UI after reset', async () => {
      authServiceSpy.resetPassword.mockReturnValue(of({ message: 'Success' }));
      component.resetPassword();
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      fixture.detectChanges();
      
      const state = fixture.nativeElement.querySelector('.success-state');
      expect(state).toBeTruthy();
      expect(state.textContent).toContain('Access Restored');
    });
  });

  describe('Template transitions', () => {
    it('should show reset form only after moving to reset step', async () => {
      // Use standard detective work on the text content
      expect(fixture.nativeElement.textContent).not.toContain('New Password');
      
      component.step.set('reset');
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      fixture.detectChanges();
      
      expect(fixture.nativeElement.textContent).toContain('Reset Password');
      expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeTruthy();
    });

    it('should show success state after reset', async () => {
      component.successMsg.set('All Done');
      fixture.detectChanges();
      await new Promise(r => setTimeout(r, 0));
      fixture.detectChanges();
      
      const state = fixture.nativeElement.querySelector('.success-state');
      expect(state).toBeTruthy();
    });
  });
});
