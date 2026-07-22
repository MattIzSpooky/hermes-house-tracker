import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProfileService } from '../../core/profile.service';
import { ErrorAlertComponent } from '../../shared/error-alert.component';
import { SectionCardComponent } from '../../shared/section-card.component';
import { SpinnerComponent } from '../../shared/spinner.component';

interface AddressForm {
  street: string;
  houseNumber: string;
  houseNumberAddition: string;
  zipCode: string;
  city: string;
  province: string;
}

const EMPTY_FORM: AddressForm = {
  street: '',
  houseNumber: '',
  houseNumberAddition: '',
  zipCode: '',
  city: '',
  province: '',
};

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [FormsModule, ErrorAlertComponent, SectionCardComponent, SpinnerComponent],
  templateUrl: './profile-page.component.html',
})
export class ProfilePageComponent {
  protected readonly svc = inject(ProfileService);

  protected form: AddressForm = { ...EMPTY_FORM };

  constructor() {
    this.svc.loadProfile();
    effect(() => {
      const address = this.svc.address();
      if (address) {
        this.form = {
          street: address.street ?? '',
          houseNumber: address.houseNumber ?? '',
          houseNumberAddition: address.houseNumberAddition ?? '',
          zipCode: address.zipCode ?? '',
          city: address.city ?? '',
          province: address.province ?? '',
        };
      }
    });
  }

  submit(): void {
    const { street, houseNumber, city, houseNumberAddition, zipCode, province } = this.form;
    if (!street || !houseNumber || !city) return;
    this.svc.updateAddress({
      street,
      houseNumber,
      city,
      ...(houseNumberAddition && { houseNumberAddition }),
      ...(zipCode && { zipCode }),
      ...(province && { province }),
    });
  }
}
