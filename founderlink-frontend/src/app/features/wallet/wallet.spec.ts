import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WalletComponent } from './wallet';
import { AuthService } from '../../core/services/auth.service';
import { StartupService } from '../../core/services/startup.service';
import { WalletService } from '../../core/services/wallet.service';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

describe('WalletComponent', () => {
  let component: WalletComponent;
  let fixture: ComponentFixture<WalletComponent>;
  let startupServiceSpy: any;
  let walletServiceSpy: any;

  beforeEach(async () => {
    startupServiceSpy = {
      getMyStartups: vi.fn()
    };

    walletServiceSpy = {
      getWallet: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [WalletComponent],
      providers: [
        { provide: AuthService, useValue: {} },
        { provide: StartupService, useValue: startupServiceSpy },
        { provide: WalletService, useValue: walletServiceSpy }
      ]
    }).compileComponents();
  });

  describe('Initialization and Startup Loading', () => {
    it('should handle startup fetch error gracefully', () => {
      startupServiceSpy.getMyStartups.mockReturnValue(throwError(() => ({ error: 'Error' })));
      
      fixture = TestBed.createComponent(WalletComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.errorMsg()).toBe('Failed to load startups.');
      expect(component.loading()).toBe(false);
      expect(component.startups()).toEqual([]);
    });

    it('should load founder startups successfully and defer wallet fetch', () => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [{ id: 7, name: 'SaaS' }] }));
      walletServiceSpy.getWallet.mockReturnValue(of({ data: { balance: 1000 } }));

      fixture = TestBed.createComponent(WalletComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(startupServiceSpy.getMyStartups).toHaveBeenCalled();
      expect(component.startups().length).toBe(1);
      expect(component.selectedStartupId()).toBe(7);
      expect(walletServiceSpy.getWallet).toHaveBeenCalledWith(7);
      
      expect(component.wallet()?.balance).toBe(1000);
      expect(component.walletLoading()).toBe(false);
      expect(component.selectedStartup()?.name).toBe('SaaS');
    });

    it('should not fetch wallet if no startups', () => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [] }));
      
      fixture = TestBed.createComponent(WalletComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(walletServiceSpy.getWallet).not.toHaveBeenCalled();
      expect(component.loading()).toBe(false);
    });
  });

  describe('Wallet load states and utils', () => {
    beforeEach(() => {
      startupServiceSpy.getMyStartups.mockReturnValue(of({ data: [{ id: 8, name: 'AI' }] }));
      fixture = TestBed.createComponent(WalletComponent);
      component = fixture.componentInstance;
    });

    it('should handle missing wallet (404/Error) properly', () => {
      walletServiceSpy.getWallet.mockReturnValue(throwError(() => ({ error: 'Not found' })));
      fixture.detectChanges();

      expect(component.wallet()).toBeNull();
      expect(component.walletLoading()).toBe(false);
    });

    it('should trigger wallet update when onStartupChange is called', () => {
      walletServiceSpy.getWallet.mockReturnValue(of({ data: { balance: 5000 } }));
      fixture.detectChanges(); // initializes with id 8
      
      walletServiceSpy.getWallet.mockClear();
      component.onStartupChange(9);

      expect(component.selectedStartupId()).toBe(9);
      expect(walletServiceSpy.getWallet).toHaveBeenCalledWith(9);
    });

    it('should format currency correctly', () => {
      expect(component.formatCurrency(48000)).toContain('48,000');
    });
  });
});
