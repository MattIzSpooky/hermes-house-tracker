import { Component, input } from '@angular/core';

@Component({
  selector: 'app-spinner',
  standalone: true,
  templateUrl: './spinner.component.html',
})
export class SpinnerComponent {
  readonly color = input<'cyan' | 'white'>('cyan');
  readonly label = input<string>('');
}
