import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-section-card',
  standalone: true,
  templateUrl: './section-card.component.html',
  host: { class: 'block' },
})
export class SectionCardComponent {
  readonly padding = input<string>('p-5');
  readonly extraClass = input<string>('');

  protected readonly classes = computed(() =>
    ['bg-white rounded-xl border border-slate-200 shadow-sm', this.padding(), this.extraClass()]
      .filter(Boolean)
      .join(' ')
  );
}
