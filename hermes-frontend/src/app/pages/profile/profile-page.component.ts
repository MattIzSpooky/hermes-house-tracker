import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProfileService } from '../../core/profile.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';
import { SpinnerComponent } from '../../shared/spinner.component';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [FormsModule, ErrorAlertComponent, SectionCardComponent, SpinnerComponent],
  templateUrl: './profile-page.component.html',
})
export class ProfilePageComponent {
  protected readonly svc = inject(ProfileService);

  street = '';
  houseNumber = '';
  houseNumberAddition = '';
  zipCode = '';
  city = '';
  province = '';

  constructor() {
    this.svc.loadProfile();
    effect(() => {
      const address = this.svc.address();
      if (address) {
        this.street = address.street ?? '';
        this.houseNumber = address.houseNumber ?? '';
        this.houseNumberAddition = address.houseNumberAddition ?? '';
        this.zipCode = address.zipCode ?? '';
        this.city = address.city ?? '';
        this.province = address.province ?? '';
      }
    });
  }

  submit(): void {
    if (!this.street || !this.houseNumber || !this.city) return;
    this.svc.updateAddress({
      street: this.street,
      houseNumber: this.houseNumber,
      city: this.city,
      ...(this.houseNumberAddition && { houseNumberAddition: this.houseNumberAddition }),
      ...(this.zipCode && { zipCode: this.zipCode }),
      ...(this.province && { province: this.province }),
    });
  }
}
