import { Component, input, output, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { NotificationResponse } from '../../../models';

@Component({
  selector: 'app-navbar',
  imports: [CommonModule, RouterLink],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css'
})
export class NavbarComponent implements OnInit, OnDestroy {
  menuToggle = output<void>();
  pageTitle  = input('Dashboard');

  unreadCount    = signal(0);
  showNotifPanel = signal(false);
  notifications  = signal<NotificationResponse[]>([]);

  private pollInterval: ReturnType<typeof setInterval> | null = null;

  constructor(public authService: AuthService, private notificationService: NotificationService) {}

  ngOnInit(): void {
    this.loadUnread();
    this.pollInterval = setInterval(() => this.loadUnread(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  loadUnread(): void {
    if (!this.authService.userId()) return;
    this.notificationService.getMyUnreadNotifications().subscribe({
      next: env => this.unreadCount.set((env.data ?? []).length),
      error: () => {}
    });
  }

  toggleNotifPanel(): void {
    if (!this.showNotifPanel()) this.loadNotifications();
    this.showNotifPanel.update(v => !v);
  }

  loadNotifications(): void {
    this.notificationService.getMyNotifications().subscribe({
      next: env => this.notifications.set((env.data ?? []).slice(0, 10)),
      error: () => {}
    });
  }

  markRead(id: number): void {
    this.notificationService.markAsRead(id).subscribe({
      next: env => {
        if (env.data) {
          this.notifications.update(list => list.map(n => n.id === id ? { ...n, read: true } : n));
          this.unreadCount.update(c => Math.max(0, c - 1));
        }
      }
    });
  }

  getRoleName(): string {
    return this.authService.role()?.replace('ROLE_', '') ?? '';
  }
}
