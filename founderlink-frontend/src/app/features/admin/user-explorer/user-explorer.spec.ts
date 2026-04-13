import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserExplorerComponent } from './user-explorer';
import { UserService } from '../../../core/services/user.service';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Component, Input, Output, EventEmitter } from '@angular/core';



describe('UserExplorerComponent', () => {
  let component: UserExplorerComponent;
  let fixture: ComponentFixture<UserExplorerComponent>;
  let userServiceSpy: any;

  beforeEach(async () => {
    userServiceSpy = {
      getAllUsers: vi.fn(),
      getUsersByRole: vi.fn()
    };



    await TestBed.configureTestingModule({
      imports: [UserExplorerComponent],
      providers: [
        provideRouter([]),
        { provide: UserService, useValue: userServiceSpy }
      ]
    }).compileComponents();
  });

  describe('Loading and initialization', () => {
    it('should load all users successfully on init (ALL role)', () => {
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [{ userId: 1 }], totalElements: 1 }));
      
      fixture = TestBed.createComponent(UserExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges(); // Will flush effect dependencies

      expect(userServiceSpy.getAllUsers).toHaveBeenCalledWith(0, 10, '');
      expect(userServiceSpy.getUsersByRole).not.toHaveBeenCalled();
      expect(component.users().length).toBe(1);
      expect(component.totalElements()).toBe(1);
      expect(component.error()).toBeNull();
    });

    it('should handle fetch errors', () => {
      userServiceSpy.getAllUsers.mockReturnValue(throwError(() => new Error('API failure')));
      
      fixture = TestBed.createComponent(UserExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.loading()).toBe(false);
      expect(component.error()).toBe('Failed to load platform users.');
      expect(component.users().length).toBe(0);
    });
  });

  describe('Search and Filtering logic', () => {
    beforeEach(() => {
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [], totalElements: 0 }));
      userServiceSpy.getUsersByRole.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(UserExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should call internal update logic explicitly via loadUsers', () => {
      userServiceSpy.getAllUsers.mockClear();
      component.searchQuery.set('Alice');
      component.loadUsers();
      expect(userServiceSpy.getAllUsers).toHaveBeenCalledWith(0, 10, 'Alice');
    });

    it('should trigger getUsersByRole if role filter is set via effect recalculation', () => {
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [] }));
      userServiceSpy.getAllUsers.mockClear();
      
      component.roleFilter.set('FOUNDER');
      fixture.detectChanges();

      expect(userServiceSpy.getUsersByRole).toHaveBeenCalledWith('FOUNDER', 0, 10, '');
      expect(userServiceSpy.getAllUsers).not.toHaveBeenCalled();
    });
  });

  describe('Pagination and utilities', () => {
    beforeEach(() => {
      userServiceSpy.getAllUsers.mockReturnValue(of({ data: [], totalElements: 0 }));
      fixture = TestBed.createComponent(UserExplorerComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should change page from the pagination event', () => {
      window.scrollTo = vi.fn();
      userServiceSpy.getAllUsers.mockClear();
      
      component.onPageChange(3);
      fixture.detectChanges(); // flush effect

      expect(component.currentPage()).toBe(3);
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
      expect(userServiceSpy.getAllUsers).toHaveBeenCalledWith(2, 10, '');
    });

    it('should output the correct role classes', () => {
      expect(component.getRoleClass('FOUNDER')).toBe('role-founder');
      expect(component.getRoleClass('INVESTOR')).toBe('role-investor');
      expect(component.getRoleClass('COFOUNDER')).toBe('role-cofounder');
      expect(component.getRoleClass('ADMIN')).toBe('role-admin');
      expect(component.getRoleClass('UNKNOWN')).toBe('');
    });
  });
});
