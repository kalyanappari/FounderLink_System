import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { InvestmentService } from '../../core/services/investment.service';
import { StartupService } from '../../core/services/startup.service';
import { UserService } from '../../core/services/user.service';
import { InvestmentResponse, InvestmentStatus, StartupResponse } from '../../models';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-investments',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginationComponent],
  templateUrl: './investments.html',
  styleUrl: './investments.css'
})
export class InvestmentsComponent implements OnInit {
  investments       = signal<InvestmentResponse[]>([]);
  myStartups        = signal<StartupResponse[]>([]);
  selectedStartupId = signal<number | null>(null);
  loading           = signal(true);
  updating          = signal<number | null>(null);
  errorMsg          = signal('');
  successMsg        = signal('');
  filterStatus      = '';
  userNames         = signal<Map<number, string>>(new Map());

  // Pagination State
  currentPage = signal(1);
  pageSize = signal(8);

  paginatedInvestments = computed(() => {
    const list = this.filteredInvestments;
    const start = (this.currentPage() - 1) * this.pageSize();
    return list.slice(start, start + this.pageSize());
  });

  constructor(
    public authService: AuthService,
    private investmentService: InvestmentService,
    private startupService: StartupService,
    private userService: UserService,
    private router: Router
  ) {}

  onPageChange(page: number): void {
    this.currentPage.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // Handle filter changes manually to reset pagination
  onFilterChange(): void {
    this.currentPage.set(1);
  }

  ngOnInit(): void {
    this.startupService.getMyStartups().subscribe({
      next: env => {
        const list = env.data ?? [];
        this.myStartups.set(list);
        if (list.length > 0) {
          this.selectedStartupId.set(list[0].id);
          this.loadInvestments(list[0].id);
        } else {
          this.loading.set(false);
        }
      },
      error: () => this.loading.set(false)
    });

    this.userService.getAllUsers().subscribe({
      next: env => {
        const map = new Map<number, string>();
        env.data?.forEach(u => map.set(u.userId, u.name || `Investor ${u.userId}`));
        this.userNames.set(map);
      }
    });
  }

  onStartupChange(startupId: number): void {
    this.selectedStartupId.set(startupId);
    this.currentPage.set(1);
    this.loadInvestments(startupId);
  }

  loadInvestments(startupId: number): void {
    this.loading.set(true);
    this.investmentService.getStartupInvestments(startupId).subscribe({
      next: env => { this.investments.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load investments.'); this.loading.set(false); }
    });
  }

  updateStatus(investmentId: number, status: 'APPROVED' | 'REJECTED'): void {
    this.updating.set(investmentId);
    this.errorMsg.set('');
    this.investmentService.updateStatus(investmentId, { status }).subscribe({
      next: env => {
        this.updating.set(null);
        const updated = env.data;
        if (updated) {
          this.investments.update(list => list.map(i => i.id === investmentId ? updated : i));
        }
        this.successMsg.set(`Investment ${status.toLowerCase()} successfully.`);
        setTimeout(() => this.successMsg.set(''), 3000);
      },
      error: env => {
        this.updating.set(null);
        this.errorMsg.set(env.error ?? 'Failed to update status.');
      }
    });
  }

  get filteredInvestments(): InvestmentResponse[] {
    return this.investments().filter(i => !this.filterStatus || i.status === this.filterStatus);
  }

  get totalAmount(): number  { return this.investments().reduce((s, i) => s + i.amount, 0); }
  get pendingCount(): number  { return this.investments().filter(i => i.status === 'PENDING').length; }
  get approvedCount(): number { return this.investments().filter(i => i.status === 'APPROVED').length; }
  get completedCount(): number { return this.investments().filter(i => i.status === 'COMPLETED').length; }

  statusClass(status: string): string {
    return status === 'APPROVED'        ? 'badge-success'
         : status === 'PENDING'         ? 'badge-warning'
         : status === 'COMPLETED'       ? 'badge-info'
         : status === 'STARTUP_CLOSED'  ? 'badge-gray'
         : 'badge-danger';
  }

  statusLabel(status: InvestmentStatus): string {
    const labels: Record<InvestmentStatus, string> = {
      PENDING: 'Pending Review', APPROVED: 'Approved', REJECTED: 'Rejected',
      COMPLETED: 'Completed', PAYMENT_FAILED: 'Payment Failed', STARTUP_CLOSED: 'Startup Closed'
    };
    return labels[status] ?? status;
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }

  messageInvestor(investorId: number): void {
    this.router.navigate(['/dashboard/messages'], { queryParams: { user: investorId } });
  }
}
