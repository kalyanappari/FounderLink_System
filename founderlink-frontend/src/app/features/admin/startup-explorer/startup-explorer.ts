import { Component, OnInit, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StartupService } from '../../../core/services/startup.service';
import { StartupResponse } from '../../../models';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-startup-explorer',
  standalone: true,
  imports: [CommonModule, RouterLink, PaginationComponent],
  templateUrl: './startup-explorer.html',
  styleUrl: './startup-explorer.css'
})
export class StartupExplorerComponent implements OnInit {
  startups = signal<StartupResponse[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  // Pagination Logic
  currentPage = signal(1);
  pageSize = signal(9); // 3x3 grid

  paginatedStartups = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.startups().slice(start, start + this.pageSize());
  });

  constructor(private startupService: StartupService) {
    // Reset to page 1 whenever results change
    effect(() => {
      this.startups();
      this.currentPage.set(1);
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    this.loadStartups();
  }

  loadStartups(): void {
    this.loading.set(true);
    this.startupService.getAll().subscribe({
      next: (res) => {
        this.startups.set(res.data || []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load global startups.');
        this.loading.set(false);
      }
    });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value);
  }

  getStageLabel(stage: string): string {
    const stages: Record<string, string> = {
      'IDEA': 'Ideation Phase',
      'MVP': 'Product Prototype',
      'EARLY_TRACTION': 'Active Market',
      'SCALING': 'Scaling Operations'
    };
    return stages[stage] ?? stage;
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
