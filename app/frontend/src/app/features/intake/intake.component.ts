import { Component, ElementRef, OnDestroy, ViewChild, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import {
  ApiError,
  CaseType,
  EQUIPMENT_CATEGORY_LABELS,
  EquipmentCategory,
} from '../../core/models';
import { ApiService } from '../../core/api.service';
import { futureDateForbidden, fileTypeAllowed, fileSizeMax } from '../../shared/validators';

@Component({
  selector: 'app-intake',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './intake.component.html',
  styleUrl: './intake.component.scss',
})
export class IntakeComponent implements OnDestroy {
  @ViewChild('errorBanner') private errorBanner?: ElementRef<HTMLDivElement>;

  protected today = new Date();

  protected categoryEntries = Object.entries(EQUIPMENT_CATEGORY_LABELS).map(
    ([value, label]) => ({ value: value as EquipmentCategory, label })
  );

  protected form = new FormGroup({
    type: new FormControl<CaseType>('ZWROT', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    category: new FormControl<EquipmentCategory | null>(null, [Validators.required]),
    model: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    purchaseDate: new FormControl<Date | null>(null, [
      Validators.required,
      futureDateForbidden,
    ]),
    reason: new FormControl('', { nonNullable: true }),
    image: new FormControl<File | null>(null, [
      Validators.required,
      fileTypeAllowed(['image/jpeg', 'image/png', 'image/webp']),
      fileSizeMax(10 * 1024 * 1024),
    ]),
  });

  protected submitting = signal(false);
  protected submitError = signal<ApiError | null>(null);
  protected previewUrl = signal<string | null>(null);
  protected selectedFileName = signal<string | null>(null);
  private currentObjectUrl: string | null = null;
  private typeSubscription: Subscription;

  constructor(private api: ApiService, private router: Router) {
    // Initialize reason validators based on initial type value
    this.updateReasonValidators(this.form.controls.type.value);
    // Subscribe to type changes to update reason validators
    this.typeSubscription = this.form.controls.type.valueChanges.subscribe(type => {
      this.updateReasonValidators(type);
    });
  }

  private updateReasonValidators(type: CaseType): void {
    const reasonCtrl = this.form.controls.reason;
    reasonCtrl.clearValidators();
    if (type === 'REKLAMACJA') {
      reasonCtrl.addValidators([Validators.required, Validators.maxLength(2000)]);
    } else {
      reasonCtrl.addValidators([Validators.maxLength(2000)]);
    }
    reasonCtrl.updateValueAndValidity();
  }

  protected onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.form.controls.image.setValue(file);
    this.form.controls.image.markAsTouched();
    if (this.currentObjectUrl) {
      URL.revokeObjectURL(this.currentObjectUrl);
      this.currentObjectUrl = null;
    }
    if (file) {
      this.currentObjectUrl = URL.createObjectURL(file);
      this.previewUrl.set(this.currentObjectUrl);
      this.selectedFileName.set(file.name);
    } else {
      this.previewUrl.set(null);
      this.selectedFileName.set(null);
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);

    const v = this.form.getRawValue();
    const fd = new FormData();
    fd.append('type', v.type);
    fd.append('category', v.category ?? '');
    fd.append('model', v.model);
    if (v.purchaseDate) {
      const d = new Date(v.purchaseDate);
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      fd.append('purchaseDate', `${yyyy}-${mm}-${dd}`);
    }
    if (v.reason) fd.append('reason', v.reason);
    if (v.image) fd.append('image', v.image);

    this.api.submitCase(fd).subscribe({
      next: (res) => {
        this.router.navigate(['/chat', res.sessionId], { state: { response: res } });
      },
      error: (err: ApiError) => {
        this.submitError.set(err);
        this.submitting.set(false);
        // Surface the banner even if the user submitted from the bottom of a long form.
        setTimeout(() => {
          const el = this.errorBanner?.nativeElement;
          el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
          el?.focus();
        }, 0);
      },
    });
  }

  ngOnDestroy(): void {
    this.typeSubscription.unsubscribe();
    if (this.currentObjectUrl) {
      URL.revokeObjectURL(this.currentObjectUrl);
    }
  }
}
