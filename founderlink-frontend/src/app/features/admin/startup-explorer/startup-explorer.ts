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
  totalElements = signal(0);
  loading = signal(true);
  error = signal<string | null>(null);

  // Pagination Logic
  currentPage = signal(1);
  pageSize = signal(9); // 3x3 grid

  constructor(private startupService: StartupService) {
    // Reload items on page change
    effect(() => {
      const page = this.currentPage();
      const size = this.pageSize();
      this.fetchStartups(page, size);
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    // Intentionally left blank as effect handles initial load
  }

  loadStartups(): void {
    this.fetchStartups(this.currentPage(), this.pageSize());
  }

  private fetchStartups(page: number, size: number): void {
    this.loading.set(true);
    // Backend pages are 0-indexed
    const pageIndex = page - 1 < 0 ? 0 : page - 1;

    this.startupService.getAll(pageIndex, size).subscribe({
      next: (res) => {
        this.startups.set(res.data || []);
        this.totalElements.set(res.totalElements || res.data?.length || 0);
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
