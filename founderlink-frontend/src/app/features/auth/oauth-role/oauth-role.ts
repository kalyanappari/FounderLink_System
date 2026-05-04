import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

interface RoleOption {
  value: string;
  label: string;
  icon: string;
  desc: string;
  perks: string[];
  gradient: string;
}

@Component({
  selector: 'app-oauth-role',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './oauth-role.html',
  styleUrl: './oauth-role.css'
})
export class OAuthRoleComponent implements OnInit {
  oauthToken  = signal('');
  userName    = signal('');
  userEmail   = signal('');
  selectedRole = signal('');
  loading     = signal(false);
  errorMsg    = signal('');

  readonly roles: RoleOption[] = [
    {
      value: 'FOUNDER',
      label: 'Founder',
      icon: '🚀',
      desc: 'Build & scale your startup',
      gradient: 'linear-gradient(135deg,rgba(99,102,241,.15),rgba(168,85,247,.1))',
      perks: ['Create a startup profile', 'Attract investors', 'Build your dream team']
    },
    {
      value: 'INVESTOR',
      label: 'Investor',
      icon: '💎',
      desc: 'Discover & fund startups',
      gradient: 'linear-gradient(135deg,rgba(245,158,11,.12),rgba(234,88,12,.08))',
      perks: ['Browse vetted startups', 'Track your portfolio', 'Connect with founders']
    },
    {
      value: 'COFOUNDER',
      label: 'Co-Founder',
      icon: '🤝',
      desc: 'Join a team & co-build',
      gradient: 'linear-gradient(135deg,rgba(16,185,129,.12),rgba(6,182,212,.08))',
      perks: ['Browse open roles', 'Join startup teams', 'Grow your career']
    }
  ];

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    const token = sessionStorage.getItem('oauthToken');
    const email = sessionStorage.getItem('oauthEmail');
    const name  = sessionStorage.getItem('oauthName');

    if (!token || !email) {
      // No pending OAuth session — send back to login
      this.router.navigate(['/auth/login']);
      return;
    }
    this.oauthToken.set(token);
    this.userEmail.set(email);
    this.userName.set(name || email.split('@')[0]);
  }

  selectRole(role: string): void {
    this.selectedRole.set(role);
  }

  complete(): void {
    if (!this.selectedRole()) return;
    this.loading.set(true);
    this.errorMsg.set('');

    this.authService.completeOAuthRegistration(this.oauthToken(), this.selectedRole())
      .subscribe({
        next: () => {
          this.loading.set(false);
          sessionStorage.removeItem('oauthToken');
          sessionStorage.removeItem('oauthEmail');
          sessionStorage.removeItem('oauthName');
          this.router.navigate(['/dashboard']);
        },
        error: (err: any) => {
          this.loading.set(false);
          this.errorMsg.set(err.error?.message || 'Something went wrong. Please try signing in again.');
        }
      });
  }
}
