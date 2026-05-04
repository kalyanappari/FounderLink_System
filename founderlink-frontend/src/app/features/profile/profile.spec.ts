import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileComponent } from './profile';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let authServiceSpy: any;
  let userServiceSpy: any;

  beforeEach(async () => {
    authServiceSpy = {
      userId: vi.fn(),
      email: vi.fn().mockReturnValue('test@test.com'),
      role: vi.fn().mockReturnValue('ROLE_FOUNDER'),
      logout: vi.fn()
    };

    userServiceSpy = {
      getUser: vi.fn(),
      updateMyProfile: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [ProfileComponent, FormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: UserService, useValue: userServiceSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } }
      ]
    }).compileComponents();
  });

  describe('Loading Profile', () => {
    it('should show error if no session found', () => {
      authServiceSpy.userId.mockReturnValue(null);
      fixture = TestBed.createComponent(ProfileComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.errorMsg()).toBe('No session found. Please re-login.');
      expect(component.loading()).toBe(false);
    });

    it('should successfully load the profile', () => {
      authServiceSpy.userId.mockReturnValue(5);
      userServiceSpy.getUser.mockReturnValue(of({ data: { name: 'Alice', skills: 'Angular, React' } }));

      fixture = TestBed.createComponent(ProfileComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(userServiceSpy.getUser).toHaveBeenCalledWith(5);
      expect(component.user()?.name).toBe('Alice');
      expect(component.name).toBe('Alice');
      expect(component.skills).toBe('Angular, React');
      expect(component.loading()).toBe(false);
    });

    it('should handle fetch errors gracefully', () => {
      authServiceSpy.userId.mockReturnValue(5);
      userServiceSpy.getUser.mockReturnValue(throwError(() => ({ error: 'Load Fail' })));

      fixture = TestBed.createComponent(ProfileComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.errorMsg()).toBe('Failed to load profile.');
      expect(component.user()).toBeNull();
    });
  });

  describe('Form states', () => {
    beforeEach(() => {
      authServiceSpy.userId.mockReturnValue(5);
      userServiceSpy.getUser.mockReturnValue(of({ data: { name: 'Bob', skills: 'Vue' } }));
      fixture = TestBed.createComponent(ProfileComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should open edit form and cancel edit form', () => {
      expect(component.editingCard()).toBeNull();
      component.startEditing('basic');
      expect(component.editingCard()).toBe('basic');
      
      component.name = 'Changes';
      component.cancelEditing(); // cancel
      
      expect(component.editingCard()).toBeNull();
    });

    it('should handle profile update success', () => {
      component.name = 'Bob Updated';
      userServiceSpy.updateMyProfile.mockReturnValue(of({ data: { name: 'Bob Updated' } }));

      vi.useFakeTimers();
      component.saveCard('basic');
      
      expect(userServiceSpy.updateMyProfile).toHaveBeenCalledWith(expect.objectContaining({ name: 'Bob Updated' }));
      expect(component.user()?.name).toBe('Bob Updated');
      expect(component.editingCard()).toBeNull();
      expect(component.saving()).toBe(false);
      expect(component.successMsg()).toBe('Saved!');
      
      vi.advanceTimersByTime(3000);
      expect(component.successMsg()).toBe('');
      vi.useRealTimers();
    });

    it('should handle profile update failure', () => {
      component.name = 'Bob Updated';
      userServiceSpy.updateMyProfile.mockReturnValue(throwError(() => ({ error: 'Failed' })));
      component.saveCard('basic');
      
      expect(component.errorMsg()).toBe('Failed');
      expect(component.saving()).toBe(false);
    });
  });

  describe('Utilities', () => {
    beforeEach(() => {
      authServiceSpy.userId.mockReturnValue(1);
      userServiceSpy.getUser.mockReturnValue(of({ data: {} }));
      fixture = TestBed.createComponent(ProfileComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should call authService.logout on logout', () => {
      component.logout();
      expect(authServiceSpy.logout).toHaveBeenCalled();
    });

    it('should convert stages correctly', () => {
      expect(component.stageLabel('EARLY_TRACTION')).toBe('Early Traction');
      expect(component.stageLabel('UNKNOWN')).toBe('UNKNOWN');
    });

    it('should format currency correctly', () => {
      expect(component.formatCurrency(5_000_000)).toBe('₹50.0L');
      expect(component.formatCurrency(15_000_000)).toBe('₹1.5Cr');
      expect(component.formatCurrency(150_000)).toBe('₹1.5L');
    });
  });
});
