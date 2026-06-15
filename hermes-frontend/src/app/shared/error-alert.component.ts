import { Component, input } from '@angular/core';

@Component({
  selector: 'app-error-alert',
  standalone: true,
  templateUrl: './error-alert.component.html',
})
export class ErrorAlertComponent {
  readonly message = input.required<string>();
}
