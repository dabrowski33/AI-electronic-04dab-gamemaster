import { Page, expect } from '@playwright/test';
import * as path from 'path';

/** Real device photos used across the E2E suite (never synthetic blobs — see app/e2e/AGENTS.md). */
export const IMAGE_DIR = path.join(__dirname, '../../../assets/example-images-for-tests');
export const IMAGES = {
  laptopPng: path.join(IMAGE_DIR, 'laptop-1.png'),
  laptopWebp: path.join(IMAGE_DIR, 'laptop-2.webp'),
  phoneJpg: path.join(IMAGE_DIR, 'phone-1.jpg'),
  phoneJpeg: path.join(IMAGE_DIR, 'phone-2.jpeg'),
  phoneJpeg3: path.join(IMAGE_DIR, 'phone-3.jpeg'),
};

/** The four valid decision labels — E2E asserts the result is ONE OF these, never a specific one
 *  (the real model is nondeterministic; category-logic lives in BE WireMock integration tests). */
export const DECISION_LABELS = [
  'Kwalifikuje się',
  'Nie kwalifikuje się',
  'Wymaga weryfikacji przez konsultanta',
  'Wymagane dodatkowe informacje',
];

/** Deterministic disclaimer substring appended by the backend MessageComposer (AC-24). */
export const DISCLAIMER_FRAGMENT = 'wstępna, automatyczna ocena';

export interface FormInput {
  type?: 'ZWROT' | 'REKLAMACJA';
  /** Visible category label, e.g. 'Laptopy i komputery'. */
  categoryLabel?: string;
  model?: string;
  /** Polish DD.MM.RRRR format, typed manually to exercise the real date parsing. */
  purchaseDate?: string;
  reason?: string;
  imagePath?: string;
}

/** Fills the intake form with real data + a real image and submits it against the real backend. */
export async function fillAndSubmitForm(
  page: Page,
  {
    type = 'ZWROT',
    categoryLabel = 'Laptopy i komputery',
    model = 'Dell XPS 15',
    purchaseDate = '15.01.2026',
    reason = '',
    imagePath,
  }: FormInput = {}
): Promise<void> {
  await page.goto('/');

  // Type
  await page.getByRole('combobox', { name: /Typ zgłoszenia/ }).click();
  await page.getByRole('option', { name: type === 'REKLAMACJA' ? 'Reklamacja' : 'Zwrot' }).click();

  // Category (by visible label)
  await page.getByRole('combobox', { name: /Kategoria sprzętu/ }).click();
  await page.getByRole('option', { name: categoryLabel }).click();

  // Model
  await page.getByRole('textbox', { name: /Model/ }).fill(model);

  // Purchase date — typed in Polish format (real adapter must parse it)
  await page.getByRole('textbox', { name: /Data zakupu/ }).fill(purchaseDate);

  // Reason (required for REKLAMACJA)
  if (reason) {
    await page.getByRole('textbox', { name: /Opis usterki/ }).fill(reason);
  }

  // Image upload — always a real device photo
  const [fileChooser] = await Promise.all([
    page.waitForEvent('filechooser'),
    page.getByRole('button', { name: /Wybierz zdjęcie/ }).click(),
  ]);
  await fileChooser.setFiles(imagePath ?? IMAGES.laptopPng);

  // Submit
  await page.getByRole('button', { name: /Wyślij zgłoszenie/ }).click();
}

/** Waits for the chat screen and asserts a valid decision badge + the mandatory disclaimer. */
export async function expectDecisionAndDisclaimer(page: Page): Promise<void> {
  // The real LLM (vision + decision) can take several seconds.
  await expect(page).toHaveURL(/\/chat\/.+/, { timeout: 45_000 });
  await expect(page.locator('.decision-badge')).toBeVisible({ timeout: 45_000 });

  // The badge is rendered uppercase via CSS text-transform, so compare case-insensitively.
  const badgeText = (await page.locator('.decision-badge').innerText()).trim().toLowerCase();
  expect(DECISION_LABELS.map((l) => l.toLowerCase())).toContain(badgeText);

  await expect(page.locator('body')).toContainText(DISCLAIMER_FRAGMENT, { timeout: 10_000 });
}
