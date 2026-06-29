import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function futureDateForbidden(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const picked = new Date(control.value);
  picked.setHours(0, 0, 0, 0);
  return picked > today ? { futureDate: true } : null;
}

export function fileSizeMax(maxBytes: number): ValidatorFn {
  return (control) => {
    const file: File | null = control.value;
    if (!file) return null;
    return file.size > maxBytes ? { fileSizeMax: { max: maxBytes, actual: file.size } } : null;
  };
}

export function fileTypeAllowed(types: string[]): ValidatorFn {
  return (control) => {
    const file: File | null = control.value;
    if (!file) return null;
    return types.includes(file.type) ? null : { fileType: { allowed: types, actual: file.type } };
  };
}
