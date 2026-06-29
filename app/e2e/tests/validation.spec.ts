import { test, expect } from '@playwright/test';
import { IMAGES } from './helpers';

/** Client-side validation paths — these never call the LLM, so they are fast and deterministic. */

test.describe('Validation paths', () => {
  test('empty form shows inline errors and stays on the intake screen', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /Wyślij zgłoszenie/ }).click();

    await expect(page).toHaveURL('/');
    await expect(page.locator('mat-error').first()).toBeVisible();
  });

  test('Reklamacja requires a reason — submission blocked without it', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('combobox', { name: /Typ zgłoszenia/ }).click();
    await page.getByRole('option', { name: 'Reklamacja' }).click();

    await page.getByRole('combobox', { name: /Kategoria sprzętu/ }).click();
    await page.getByRole('option', { name: 'Laptopy i komputery' }).click();
    await page.getByRole('textbox', { name: /Model/ }).fill('Dell XPS 15');
    await page.getByRole('textbox', { name: /Data zakupu/ }).fill('15.01.2026');

    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.getByRole('button', { name: /Wybierz zdjęcie/ }).click(),
    ]);
    await fileChooser.setFiles(IMAGES.laptopPng);

    await page.getByRole('button', { name: /Wyślij zgłoszenie/ }).click();

    await expect(page).toHaveURL('/');
    await expect(page.locator('mat-error')).toContainText(/wymagany|usterki/i);
  });

  test('manually-typed Polish date (DD.MM.RRRR) is accepted (regression for the date adapter)', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('textbox', { name: /Data zakupu/ }).fill('15.01.2026');
    // Blur to trigger parsing
    await page.getByRole('textbox', { name: /Model/ }).click();

    // The date field must NOT show a "podaj datę" error for a valid Polish-format date.
    const dateField = page.locator('mat-form-field', { hasText: 'Data zakupu' });
    await expect(dateField.locator('mat-error')).toHaveCount(0);
  });

  test('image is required — error shown when not uploaded', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('combobox', { name: /Kategoria sprzętu/ }).click();
    await page.getByRole('option', { name: 'Laptopy i komputery' }).click();
    await page.getByRole('textbox', { name: /Model/ }).fill('Dell XPS 15');
    await page.getByRole('textbox', { name: /Data zakupu/ }).fill('15.01.2026');

    await page.getByRole('button', { name: /Wyślij zgłoszenie/ }).click();

    await expect(page).toHaveURL('/');
    await expect(page.locator('.upload-error')).toContainText(/wymagane/i);
  });
});
