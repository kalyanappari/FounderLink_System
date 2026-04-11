import { Component, Input, Output, EventEmitter, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.css'
})
export class PaginationComponent {
  private _totalItems = signal(0);
  private _pageSize = signal(10);
  private _currentPage = signal(1);

  @Input() set totalItems(val: number) { this._totalItems.set(val); }
  @Input() set pageSize(val: number) { this._pageSize.set(val); }
  @Input() set currentPage(val: number) { this._currentPage.set(val); }

  @Output() pageChange = new EventEmitter<number>();

  public get totalItems(): number { return this._totalItems(); }
  public get pageSize(): number { return this._pageSize(); }
  public get currentPage(): number { return this._currentPage(); }

  totalPages = computed(() => Math.ceil(this._totalItems() / this._pageSize()));

  pages = computed(() => {
    const total = this.totalPages();
    const current = this._currentPage();
    const pages: number[] = [];
    
    // Simple logic to show current, one before, and one after if possible
    let start = Math.max(1, current - 1);
    let end = Math.min(total, current + 1);

    // Always show at least 3 pages if they exist
    if (end - start < 2) {
      if (start === 1) end = Math.min(total, 3);
      else if (end === total) start = Math.max(1, total - 2);
    }

    for (let i = start; i <= end; i++) {
        pages.push(i);
    }
    return pages;
  });

  get startItem(): number {
    if (this._totalItems() === 0) return 0;
    return (this._currentPage() - 1) * this._pageSize() + 1;
  }

  get endItem(): number {
    return Math.min(this._currentPage() * this._pageSize(), this._totalItems());
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages() && page !== this._currentPage()) {
      this.pageChange.emit(page);
    }
  }

  nextPage(): void {
    if (this._currentPage() < this.totalPages()) {
      this.goToPage(this._currentPage() + 1);
    }
  }

  prevPage(): void {
    if (this._currentPage() > 1) {
      this.goToPage(this._currentPage() - 1);
    }
  }
}
