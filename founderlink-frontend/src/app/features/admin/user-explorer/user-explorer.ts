import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { UserService } from '../../../core/services/user.service';
import { UserResponse } from '../../../models';

@Component({
  selector: 'app-user-explorer',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './user-explorer.html',
  styleUrl: './user-explorer.css'
})
export class UserExplorerComponent implements OnInit {
  users = signal<UserResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  // Search Logic
  searchQuery = signal('');
  roleFilter = signal('ALL');

  filteredUsers = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const role = this.roleFilter();
    let list = this.users();

    if (role !== 'ALL') {
      list = list.filter(u => u.role === role);
    }

    if (q) {
      list = list.filter(u => 
        u.name?.toLowerCase().includes(q) || 
        u.email.toLowerCase().includes(q) || 
        u.role.toLowerCase().includes(q)
      );
    }

    return list;
  });

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.userService.getAllUsers().subscribe({
      next: (res) => {
        this.users.set(res.data || []);
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
}
