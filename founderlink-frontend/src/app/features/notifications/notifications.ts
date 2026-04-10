import { Component, OnInit, OnDestroy, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { NotificationResponse } from '../../models';
import { Router } from '@angular/router';
import { ThemeService } from '../../core/services/theme.service';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, PaginationComponent],
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

  // Pagination State
  currentPage = signal(1);
  pageSize = signal(10);

  totalElements = signal(0);

  // Pagination State
  currentPage = signal(1);
  pageSize = signal(10);

  private refreshInterval: ReturnType<typeof setInterval> | null = null;

  constructor(
    public authService: AuthService, 
    public themeService: ThemeService,
    private notificationService: NotificationService,
    private router: Router
  ) {
    // Reload items on page or filter change
    effect(() => {
      const page = this.currentPage();
      const size = this.pageSize();
      const type = this.filterType(); // Reacts to filter change
      this.fetchNotifications(page, size, type);
    }, { allowSignalWrites: true });
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  ngOnInit(): void {
    this.pollUnreadCount();
    // Poll just the integer every 30 seconds rather than massive array
    this.refreshInterval = setInterval(() => this.pollUnreadCount(), 30000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }

  pollUnreadCount(): void {
    this.notificationService.getUnreadCount().subscribe({
      next: env => {
        if (env.data !== undefined) {
          this.unreadCount.set(env.data);
        }
      }
    });
  }

  fetchNotifications(page: number, size: number, type: string): void {
    this.loading.set(true);
    const pageIndex = page - 1 < 0 ? 0 : page - 1;
    
    const obs = type === 'unread' 
      ? this.notificationService.getMyUnreadNotifications(pageIndex, size)
      : this.notificationService.getMyNotifications(pageIndex, size);

    obs.subscribe({
      next: env => {
        const all = env.data ?? [];
        if (type === 'read') {
          // Fallback client-side filter for 'read' tab using 'all' data
          const readOnly = all.filter(n => n.read);
          this.notifications.set(readOnly);
          this.totalElements.set(readOnly.length);
        } else {
          this.notifications.set(all);
          this.totalElements.set(env.totalElements || all.length);
        }
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Failed to load notifications.');
        this.loading.set(false);
      }
    });
  }

  loadNotifications(): void {
    this.fetchNotifications(this.currentPage(), this.pageSize(), this.filterType());
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
    return this.notifications();
  }

  setFilter(f: 'all' | 'unread' | 'read'): void {
    this.filterType.set(f);
    this.currentPage.set(1);
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
