import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { StartupService } from '../../../core/services/startup.service';
import { InvestmentResponse, InvestmentStatus } from '../../../models';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PaginationComponent],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.css'
})
export class PortfolioComponent implements OnInit {
  investments = signal<InvestmentResponse[]>([]);
  loading     = signal(true);
  errorMsg    = signal('');
  filterStatus = '';
  startupNames = signal<Map<number, string>>(new Map());
  founderIds   = signal<Map<number, number>>(new Map());

  // Pagination State
  currentPage = signal(1);
  pageSize = signal(8);

  paginatedInvestments = computed(() => {
    const list = this.filtered;
    const start = (this.currentPage() - 1) * this.pageSize();
    return list.slice(start, start + this.pageSize());
  });

  constructor(
    public authService: AuthService,
    private investmentService: InvestmentService,
    private startupService: StartupService,
    private router: Router
  ) {}

  onPageChange(page: number): void {
    this.currentPage.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  onFilterChange(): void {
    this.currentPage.set(1);
  }

  ngOnInit(): void {
    this.investmentService.getMyPortfolio().subscribe({
      next: env => { this.investments.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load portfolio.'); this.loading.set(false); }
    });

    this.startupService.getAll().subscribe({
      next: env => {
        const nameMap = new Map<number, string>();
        const idMap   = new Map<number, number>();
        env.data?.forEach(s => {
          nameMap.set(s.id, s.name);
          idMap.set(s.id, s.founderId);
        });
        this.startupNames.set(nameMap);
        this.founderIds.set(idMap);
      }
    });
  }

  messageFounder(startupId: number): void {
    const founderId = this.founderIds().get(startupId);
    if (founderId) {
      this.router.navigate(['/dashboard/messages'], { queryParams: { user: founderId } });
    }
  }

  get filtered(): InvestmentResponse[] {
    return this.investments().filter(i => !this.filterStatus || i.status === this.filterStatus);
  }

  get totalInvested(): number   { return this.investments().reduce((s, i) => s + i.amount, 0); }
  get completedAmount(): number { return this.investments().filter(i => i.status === 'COMPLETED').reduce((s, i) => s + i.amount, 0); }
  get pendingAmount(): number   { return this.investments().filter(i => i.status === 'PENDING').reduce((s, i) => s + i.amount, 0); }
  get approvedAmount(): number  { return this.investments().filter(i => i.status === 'APPROVED').reduce((s, i) => s + i.amount, 0); }

  statusLabel(status: InvestmentStatus): string {
    const labels: Record<InvestmentStatus, string> = {
      PENDING: 'Pending Review', APPROVED: 'Approved', REJECTED: 'Rejected',
      COMPLETED: 'Completed', PAYMENT_FAILED: 'Payment Failed', STARTUP_CLOSED: 'Startup Closed'
    };
    return labels[status] ?? status;
  }

  statusClass(status: string): string {
    return status === 'APPROVED'       ? 'badge-success'
         : status === 'PENDING'        ? 'badge-warning'
         : status === 'COMPLETED'      ? 'badge-info'
         : status === 'STARTUP_CLOSED' ? 'badge-gray'
         : 'badge-danger';
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }
}
