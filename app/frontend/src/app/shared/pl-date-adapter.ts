import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';
import { MatDateFormats } from '@angular/material/core';

/**
 * Date adapter that parses and displays Polish-format dates (DD.MM.RRRR), matching the form
 * placeholder. The stock NativeDateAdapter uses the browser locale (en-US) and cannot parse a
 * typed "15.01.2026", which silently fails validation — see the datepicker bug fixed on 2026-06-25.
 */
@Injectable()
export class PlDateAdapter extends NativeDateAdapter {
  override parse(value: unknown): Date | null {
    if (typeof value === 'string' && value.trim()) {
      const m = value.trim().match(/^(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{4})$/);
      if (m) {
        const day = +m[1];
        const month = +m[2];
        const year = +m[3];
        if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
          const d = new Date(year, month - 1, day);
          // Reject overflow (e.g. 31.02) — JS rolls them over.
          if (d.getDate() === day && d.getMonth() === month - 1) {
            return d;
          }
        }
        return null;
      }
      const ts = Date.parse(value);
      return isNaN(ts) ? null : new Date(ts);
    }
    return super.parse(value);
  }

  override format(date: Date, displayFormat: object): string {
    // For the text input we render the Polish DD.MM.RRRR form; calendar labels fall through
    // to the native (pl-PL) formatter for localized month/day names.
    if ((displayFormat as unknown) === 'input') {
      const d = String(date.getDate()).padStart(2, '0');
      const mo = String(date.getMonth() + 1).padStart(2, '0');
      return `${d}.${mo}.${date.getFullYear()}`;
    }
    return super.format(date, displayFormat);
  }
}

export const PL_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: 'input' },
  display: {
    dateInput: 'input',
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
  },
};
