import { Component, computed, input } from '@angular/core';

const STATUS_CLASSES: Record<string, string> = {
  PENDING:      'bg-slate-100 text-slate-600',
  IN_PROGRESS:  'bg-cyan-100 text-cyan-700',
  COMPLETED:    'bg-emerald-100 text-emerald-700',
  FAILED:       'bg-red-100 text-red-700',
  TIMED_OUT:    'bg-red-100 text-red-700',
  FOR_SALE:     'bg-emerald-100 text-emerald-700',
  UNDER_OFFER:  'bg-amber-100 text-amber-700',
  SOLD:         'bg-slate-100 text-slate-500',
  WITHDRAWN:    'bg-red-100 text-red-700',
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  templateUrl: './status-badge.component.html',
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly badgeClass = computed(
    () => STATUS_CLASSES[this.status()] ?? 'bg-slate-100 text-slate-600'
  );
  protected readonly label = computed(() =>
    this.status().replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase())
  );
}
