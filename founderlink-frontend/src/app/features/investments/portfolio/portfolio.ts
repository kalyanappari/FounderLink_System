import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { InvestmentResponse, InvestmentStatus } from '../../../models';

@Component({
  selector: 'app-portfolio',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.css'
})
export class PortfolioComponent implements OnInit {
  investments = signal<InvestmentResponse[]>([]);
  loading     = signal(true);
  errorMsg    = signal('');
  filterStatus = '';

  constructor(public authService: AuthService, private investmentService: InvestmentService) {}

  ngOnInit(): void {
    this.investmentService.getMyPortfolio().subscribe({
      next: env => { this.investments.set(env.data ?? []); this.loading.set(false); },
      error: env => { this.errorMsg.set(env.error ?? 'Failed to load portfolio.'); this.loading.set(false); }
    });
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
