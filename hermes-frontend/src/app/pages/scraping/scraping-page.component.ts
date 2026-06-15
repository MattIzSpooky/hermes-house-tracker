import { Component, computed, inject, OnDestroy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrapingService } from '../../core/scraping.service';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { CreateScrapingSessionRequest, TERMINAL_STATUSES } from '../../core/api.types';
import { SpinnerComponent } from '../../shared/spinner.component';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';

@Component({
  selector: 'app-scraping-page',
  standalone: true,
  imports: [FormsModule, DatePipe, StatusBadgeComponent, SpinnerComponent, ErrorAlertComponent, SectionCardComponent],
  templateUrl: './scraping-page.component.html',
})
export class ScrapingPageComponent implements OnDestroy {
  protected readonly svc = inject(ScrapingService);

  city = '';
  minPrice: number | null = null;
  maxPrice: number | null = null;
  minArea: number | null = null;
  maxArea: number | null = null;
  pageLimit = 3;

  protected readonly isPolling = computed(() => {
    const s = this.svc.session();
    return s !== null && !TERMINAL_STATUSES.includes(s.status);
  });

  protected readonly isTerminal = computed(() => {
    const s = this.svc.session();
    return s !== null && TERMINAL_STATUSES.includes(s.status);
  });

  ngOnDestroy(): void {
    this.svc.stopPolling();
  }

  submit(): void {
    if (!this.city) return;
    const req: CreateScrapingSessionRequest = {
      city: this.city,
      pageLimit: this.pageLimit,
      ...(this.minPrice != null && { minPrice: this.minPrice }),
      ...(this.maxPrice != null && { maxPrice: this.maxPrice }),
      ...(this.minArea != null && { minArea: this.minArea }),
      ...(this.maxArea != null && { maxArea: this.maxArea }),
    };
    this.svc.createSession(req);
  }
}
