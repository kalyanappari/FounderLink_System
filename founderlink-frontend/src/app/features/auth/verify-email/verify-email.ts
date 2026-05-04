import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.css'
})
export class VerifyEmailComponent implements OnInit {
  otp        = '';
  email      = signal('');
  loading    = signal(false);
  resending  = signal(false);
  errorMsg   = signal('');
  successMsg = signal('');
  resendCooldown = signal(0);

  private resendTimer: any;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    const pending = localStorage.getItem('pendingVerificationEmail');
    if (!pending) {
      // No pending registration — redirect to login
      this.router.navigate(['/auth/login']);
      return;
    }
    this.email.set(pending);
  }

  onSubmit(): void {
    if (!this.otp || this.otp.length !== 6) {
      this.errorMsg.set('Please enter the 6-digit code from your email.');
      return;
    }
    this.loading.set(true);
    this.errorMsg.set('');

    this.authService.verifyEmail(this.email(), this.otp).subscribe({
      next: () => {
        this.loading.set(false);
        localStorage.removeItem('pendingVerificationEmail');
        this.successMsg.set('Email verified! Redirecting to login...');
        setTimeout(() => this.router.navigate(['/auth/login']), 1500);
      },
      error: (err: any) => {
        this.loading.set(false);
        this.errorMsg.set(err.error?.message || 'Invalid or expired code. Please try again.');
      }
    });
  }

  resendOtp(): void {
    if (this.resendCooldown() > 0 || this.resending()) return;
    this.resending.set(true);
    this.errorMsg.set('');

    this.authService.resendVerification(this.email()).subscribe({
      next: () => {
        this.resending.set(false);
        this.successMsg.set('A new code has been sent to your email.');
        this.startCooldown(60);
      },
      error: (err: any) => {
        this.resending.set(false);
        this.errorMsg.set(err.error?.message || 'Could not resend code. Please try again.');
      }
    });
  }

  private startCooldown(seconds: number): void {
    this.resendCooldown.set(seconds);
    this.resendTimer = setInterval(() => {
      const current = this.resendCooldown() - 1;
      this.resendCooldown.set(current);
      if (current <= 0) clearInterval(this.resendTimer);
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.resendTimer) clearInterval(this.resendTimer);
  }
}
