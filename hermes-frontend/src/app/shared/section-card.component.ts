import { Component, input } from '@angular/core';

@Component({
  selector: 'app-section-card',
  standalone: true,
  templateUrl: './section-card.component.html',
})
export class SectionCardComponent {
  readonly padding = input<string>('p-5');
  readonly extraClass = input<string>('');
}
