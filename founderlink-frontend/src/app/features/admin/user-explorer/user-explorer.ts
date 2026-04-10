import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserService } from '../../../core/services/user.service';
import { UserResponse } from '../../../models';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-user-explorer',
  standalone: true,
  imports: [CommonModule, RouterLink, PaginationComponent],
  templateUrl: './user-explorer.html',
  styleUrl: './user-explorer.css'
})
export class UserExplorerComponent implements OnInit {
  users = signal<UserResponse[]>([]);
  totalElements = signal(0);
  loading = signal(true);
  error = signal<string | null>(null);

  // Search Logic
  searchQuery = signal('');
  roleFilter = signal('ALL');

  // Pagination Logic
  currentPage = signal(1);
  pageSize = signal(10);

  constructor(private userService: UserService) {
    // Whenever parameters change, reload users from backend
    effect(() => {
      const role = this.roleFilter();
      const q = this.searchQuery().trim();
      const page = this.currentPage();
      const size = this.pageSize();
      
      // Prevent running load if we aren't fully initialized (optional, but effect runs automatically)
      // Call load internally
      // Note: updating component state within effect requires allowSignalWrites
      this.fetchUsersFromBackend(page, size, q, role);
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    // Intentionally left blank as the effect will trigger on mount due to default signal signals
  }

  loadUsers(): void {
    this.fetchUsersFromBackend(this.currentPage(), this.pageSize(), this.searchQuery().trim(), this.roleFilter());
  }

  private fetchUsersFromBackend(page: number, size: number, search: string, role: string): void {
    this.loading.set(true);
    // Backend pages are 0-indexed
    const pageIndex = page - 1 < 0 ? 0 : page - 1;

    const request$ = role === 'ALL' 
      ? this.userService.getAllUsers(pageIndex, size, search)
      : this.userService.getUsersByRole(role, pageIndex, size, search);

    request$.subscribe({
      next: (res) => {
        this.users.set(res.data || []);
        this.totalElements.set(res.totalElements || res.data?.length || 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load platform users.');
        this.loading.set(false);
      }
    });
  }

  getRoleClass(role: string): string {
    switch (role) {
      case 'FOUNDER': return 'role-founder';
      case 'INVESTOR': return 'role-investor';
      case 'COFOUNDER': return 'role-cofounder';
      case 'ADMIN': return 'role-admin';
      default: return '';
    }
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
