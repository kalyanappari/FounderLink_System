import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { StartupService } from '../../core/services/startup.service';
import { InvestmentService } from '../../core/services/investment.service';
import { StartupResponse, StartupStage } from '../../models';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-startups',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PaginationComponent],
  templateUrl: './startups.html',
  styleUrl: './startups.css'
})
export class StartupsComponent implements OnInit {
  allStartups = signal<StartupResponse[]>([]);
  totalElements = signal(0);
  loading     = signal(true);
  errorMsg    = signal('');

  // Pagination Logic
  currentPage = signal(1);
  pageSize = signal(12);

  // Filters (Converted to signals for reactive effects)
  searchQuery      = signal('');
  selectedStage    = signal('');
  selectedIndustry = signal('');
  minFunding       = signal('');
  maxFunding       = signal('');
  availableIndustries = signal<string[]>([]);

  // Invest modal
  investModal   = signal<StartupResponse | null>(null);
  investAmount  = 0;
  investing     = signal(false);
  investError   = signal('');
  investSuccess = signal('');

  readonly stages: StartupStage[] = ['IDEA', 'MVP', 'EARLY_TRACTION', 'SCALING'];
  readonly stageLabels: Record<StartupStage, string> = {
    IDEA: 'Idea', MVP: 'MVP', EARLY_TRACTION: 'Early Traction', SCALING: 'Scaling'
  };

  constructor(
    public authService: AuthService,
    private startupService: StartupService,
    private investmentService: InvestmentService,
    private router: Router
  ) {
    // RECTIVE AUTO-FETCH: Whenever filters or page changes, fetch from backend
    effect(() => {
      this.fetchFromBackend(
        this.currentPage(),
        this.pageSize(),
        this.searchQuery(),
        this.selectedStage(),
        this.selectedIndustry(),
        this.minFunding(),
        this.maxFunding()
      );
    });
  }

  private fetchFromBackend(page: number, size: number, search: string, stage: string, industry: string, min: string, max: string): void {
    this.loading.set(true);
    this.errorMsg.set('');

    // Backend is 0-indexed
    const pageIndex = Math.max(0, page - 1);

    const filters: any = {};
    if (search)   filters.industry = search; 
    if (stage)    filters.stage    = stage;
    if (industry) filters.industry = industry;
    if (min)      filters.minFunding = Number(min);
    if (max)      filters.maxFunding = Number(max);

    this.startupService.search(filters, pageIndex, size).subscribe({
      next: env => { 
        this.allStartups.set(env.data ?? []);
        this.totalElements.set(env.totalElements || (env.data?.length || 0));
        this.loading.set(false); 
      },
      error: env => { 
        this.errorMsg.set(env.error ?? 'Failed to load startups.'); 
        this.loading.set(false); 
      }
    });
  }

  ngOnInit(): void {
    this.loadIndustries();
  }

  private loadIndustries(): void {
    // One-time load of industries for the filter dropdown
    if (this.availableIndustries().length === 0) {
      this.startupService.getAll(0, 100).subscribe(res => {
        if (res.data) {
          const industries = [...new Set(res.data.map(s => s.industry))].sort();
          this.availableIndustries.set(industries);
        }
      });
    }
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  applyFilters(): void {
    this.currentPage.set(1);
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.selectedStage.set('');
    this.selectedIndustry.set('');
    this.minFunding.set('');
    this.maxFunding.set('');
    this.currentPage.set(1);
  }

  get hasFilters(): boolean {
    return !!(this.selectedStage() || this.selectedIndustry() || this.minFunding() || this.maxFunding());
  }


  // ── Invest Modal ──────────────────────────────────────────────
  openInvestModal(startup: StartupResponse): void {
    this.investModal.set(startup);
    this.investAmount = 0;
    this.investError.set('');
    this.investSuccess.set('');
  }

  closeInvestModal(): void {
    this.investModal.set(null);
    this.investAmount = 0;
    this.investError.set('');
    this.investSuccess.set('');
  }

  submitInvestment(): void {
    const startup = this.investModal();
    if (!startup) return;
    if (!this.investAmount || this.investAmount < 1000) {
      this.investError.set('Minimum investment is ₹1,000.');
      return;
    }

    this.investing.set(true);
    this.investError.set('');

    this.investmentService.create({ startupId: startup.id, amount: this.investAmount }).subscribe({
      next: () => {
        this.investing.set(false);
        this.investSuccess.set('Investment submitted successfully! Awaiting founder approval.');
        setTimeout(() => this.closeInvestModal(), 2500);
      },
      error: env => {
        this.investing.set(false);
        this.investError.set(env.error ?? 'Failed to submit investment.');
      }
    });
  }

  messageFounder(founderId: number): void {
    this.router.navigate(['/dashboard/messages'], { queryParams: { user: founderId } });
  }

  // ── Helpers ───────────────────────────────────────────────────
  private roleIs(r: string): boolean {
    const s = this.authService.role() ?? '';
    return s === r || s === `ROLE_${r}`;
  }
  get isInvestor(): boolean { return this.roleIs('INVESTOR'); }
  get isFounder():  boolean { return this.roleIs('FOUNDER'); }

  stageLabel(stage: string): string {
    return this.stageLabels[stage as StartupStage] ?? stage;
  }

  stageClass(stage: string): string {
    return stage === 'IDEA'           ? 'badge-gray'
         : stage === 'MVP'            ? 'badge-info'
         : stage === 'EARLY_TRACTION' ? 'badge-warning'
         : 'badge-success';
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }
}
