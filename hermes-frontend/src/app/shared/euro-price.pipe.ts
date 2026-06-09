import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'euroPrice', standalone: true })
export class EuroPricePipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return '—';
    return '€ ' + value.toLocaleString('nl-NL');
  }
}
