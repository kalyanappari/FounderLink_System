import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { UserResponse, UserUpdateRequest } from '../../models';

@Component({
  selector: 'app-profile',
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class ProfileComponent implements OnInit {
  user      = signal<UserResponse | null>(null);
  loading   = signal(true);
  editing   = signal(false);
  saving    = signal(false);
  errorMsg  = signal('');
  successMsg = signal('');

  name           = '';
  skills         = '';
  experience     = '';
  bio            = '';
  portfolioLinks = '';

  constructor(
    public authService: AuthService,
    private userService: UserService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading.set(true);
    const userId = this.authService.userId()!;
    this.userService.getUser(userId).subscribe({
      next: env => {
        const u = env.data;
        this.user.set(u);
        if (u) {
          this.name           = u.name ?? '';
          this.skills         = u.skills ?? '';
          this.experience     = u.experience ?? '';
          this.bio            = u.bio ?? '';
          this.portfolioLinks = u.portfolioLinks ?? '';
        }
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Failed to load profile.');
        this.loading.set(false);
      }
    });
  }

  toggleEdit(): void {
    this.editing.update(v => !v);
    if (!this.editing()) {
      // Reset on cancel
      const u = this.user();
      if (u) {
        this.name = u.name ?? ''; this.skills = u.skills ?? '';
        this.experience = u.experience ?? ''; this.bio = u.bio ?? '';
        this.portfolioLinks = u.portfolioLinks ?? '';
      }
    }
  }

  saveProfile(): void {
    if (!this.name.trim()) { this.errorMsg.set('Name is required.'); return; }
    this.saving.set(true);
    this.errorMsg.set('');

    const req: UserUpdateRequest = {
      name: this.name || null,
      skills: this.skills || null,
      experience: this.experience || null,
      bio: this.bio || null,
      portfolioLinks: this.portfolioLinks || null
    };

    // Always uses session userId — never a route param
    this.userService.updateMyProfile(req).subscribe({
      next: env => {
        this.user.set(env.data);
        this.editing.set(false);
        this.saving.set(false);
        this.successMsg.set('Profile updated successfully!');
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.saving.set(false);
        this.errorMsg.set(env.error ?? 'Failed to update profile.');
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }

  getRoleDisplay(): { label: string; icon: string; color: string } {
    const role = this.authService.role() ?? '';
    const clean = role.replace('ROLE_', '');
    const map: Record<string, { label: string; icon: string; color: string }> = {
      FOUNDER:   { label: 'Founder',    icon: '🚀', color: '#6366f1' },
      INVESTOR:  { label: 'Investor',   icon: '💰', color: '#22c55e' },
      COFOUNDER: { label: 'Co-Founder', icon: '👥', color: '#06b6d4' },
      ADMIN:     { label: 'Admin',      icon: '⚙️', color: '#ef4444' }
    };
    return map[clean] ?? { label: clean, icon: '👤', color: '#64748b' };
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('en-IN', {
      year: 'numeric', month: 'long', day: 'numeric'
    });
  }
}
