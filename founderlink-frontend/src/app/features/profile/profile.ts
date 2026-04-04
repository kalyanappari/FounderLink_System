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
    const userId = this.authService.userId();
    if (!userId) {
      this.errorMsg.set('No session found. Please re-login.');
      this.loading.set(false);
      return;
    }
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

  getRoleDisplay(): { label: string; icon: string; color: string; aura: string } {
    const role = (this.authService.role() ?? '').replace('ROLE_', '');
    const map: Record<string, { label: string; icon: string; color: string; aura: string }> = {
      FOUNDER:   { label: 'Founder',    icon: '🚀', color: '#6366f1', aura: 'aura-blue' },
      INVESTOR:  { label: 'Investor',   icon: '💰', color: '#10b981', aura: 'aura-green' },
      COFOUNDER: { label: 'Co-Founder', icon: '👥', color: '#06b6d4', aura: 'aura-cyan' },
      ADMIN:     { label: 'Admin',      icon: '⚙️', color: '#f43f5e', aura: 'aura-rose' }
    };
    return map[role] ?? { label: role, icon: '👤', color: '#94a3b8', aura: 'aura-gray' };
  }

  get skillArray(): string[] {
    return this.skills ? this.skills.split(',').map(s => s.trim()).filter(s => !!s) : [];
  }
}
