import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { NotificationResponse } from '../../models';
import { Router } from '@angular/router';

@Component({
  selector: 'app-notifications',
  imports: [CommonModule],
  templateUrl: './notifications.html',
  styleUrl: './notifications.css'
})
export class NotificationsComponent implements OnInit, OnDestroy {
  notifications = signal<NotificationResponse[]>([]);
  loading       = signal(true);
  errorMsg      = signal('');
  unreadCount   = signal(0);
  filterType    = signal<'all' | 'unread' | 'read'>('all');
  selectedNotification = signal<NotificationResponse | null>(null);

  private refreshInterval: ReturnType<typeof setInterval> | null = null;

  constructor(
    public authService: AuthService, 
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
    this.refreshInterval = setInterval(() => this.loadNotifications(), 30000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }

  loadNotifications(): void {
    this.loading.set(true);
    this.notificationService.getMyNotifications().subscribe({
      next: env => {
        const all = env.data ?? [];
        this.notifications.set(all);
        this.unreadCount.set(all.filter(n => !n.read).length);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Failed to load notifications.');
        this.loading.set(false);
      }
    });
  }

  markAsRead(id: number): void {
    const n = this.notifications().find(x => x.id === id);
    if (!n || n.read) return;
    this.notificationService.markAsRead(id).subscribe({
      next: env => {
        if (env.data) {
          this.notifications.update(list => list.map(x => x.id === id ? { ...x, read: true } : x));
          this.unreadCount.update(c => Math.max(0, c - 1));
        }
      }
    });
  }

  onNotificationClick(n: NotificationResponse): void {
    if (!n.read) {
      this.markAsRead(n.id);
    }
    
    // Check if it's a message type
    if (n.type.includes('MESSAGE')) {
      const match = n.message.match(/#(\d+)/);
      if (match && match[1]) {
        this.router.navigate(['/dashboard/messages'], { queryParams: { user: match[1] } });
        return;
      }
    }

    // Otherwise open modal for full message
    this.selectedNotification.set(n);
  }

  closeModal(): void {
    this.selectedNotification.set(null);
  }

  markAllAsRead(): void {
    const unread = this.notifications().filter(n => !n.read);
    unread.forEach(n => this.markAsRead(n.id));
  }

  getFiltered(): NotificationResponse[] {
    const f = this.filterType();
    const all = this.notifications();
    if (f === 'read')   return all.filter(n => n.read);
    if (f === 'unread') return all.filter(n => !n.read);
    return all;
  }

  setFilter(f: 'all' | 'unread' | 'read'): void {
    this.filterType.set(f);
  }

  getIcon(type: string): string {
    if (type.includes('INVESTMENT')) return 'investment';
    if (type.includes('TEAM')) return 'team';
    if (type.includes('PAYMENT')) return 'payment';
    if (type.includes('MESSAGE')) return 'message';
    if (type.includes('STARTUP')) return 'startup';
    if (type.includes('REGISTERED')) return 'user';
    if (type.includes('PASSWORD')) return 'lock';
    return 'info';
  }

  formatDate(date: string): string {
    const d = new Date(date);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    if (diffMins < 1)  return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7)  return `${diffDays}d ago`;
    return d.toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' });
  }
}
