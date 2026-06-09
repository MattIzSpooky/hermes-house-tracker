import { Component, computed, input } from '@angular/core';

const STATUS_CLASSES: Record<string, string> = {
  PENDING: 'bg-gray-100 text-gray-700',
  IN_PROGRESS: 'bg-blue-100 text-blue-700',
  COMPLETED: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
  TIMED_OUT: 'bg-red-100 text-red-700',
  FOR_SALE: 'bg-green-100 text-green-700',
  UNDER_OFFER: 'bg-orange-100 text-orange-700',
  SOLD: 'bg-gray-100 text-gray-700',
  WITHDRAWN: 'bg-red-100 text-red-700',
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span [class]="'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ' + badgeClass()">
      {{ status() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly badgeClass = computed(
    () => STATUS_CLASSES[this.status()] ?? 'bg-gray-100 text-gray-700'
  );
}
