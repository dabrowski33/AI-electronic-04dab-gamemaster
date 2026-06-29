import { test } from '@playwright/test';
import { fillAndSubmitForm, expectDecisionAndDisclaimer, IMAGES } from './helpers';

/**
 * Image-format coverage against the REAL stack. We do NOT assert a specific decision category here —
 * the live model is nondeterministic, so deterministic "signs-of-use ⇒ NOT_ELIGIBLE" logic is
 * verified in BE integration tests (WireMock), not E2E (see app/e2e/AGENTS.md). Here we only prove
 * that every declared image format flows end-to-end and yields ONE OF the four valid decisions plus
 * the mandatory disclaimer.
 */

test.describe('Decision is always a valid category + disclaimer (per format)', () => {
  test('PNG laptop (Zwrot)', async ({ page }) => {
    await fillAndSubmitForm(page, {
      type: 'ZWROT',
      categoryLabel: 'Laptopy i komputery',
      model: 'HP EliteBook',
      imagePath: IMAGES.laptopPng,
    });
    await expectDecisionAndDisclaimer(page);
  });

  test('JPEG phone (Reklamacja)', async ({ page }) => {
    await fillAndSubmitForm(page, {
      type: 'REKLAMACJA',
      categoryLabel: 'Smartfony i telefony',
      model: 'Xiaomi Redmi',
      reason: 'Bateria puchnie i obudowa się rozszczelnia.',
      imagePath: IMAGES.phoneJpeg3,
    });
    await expectDecisionAndDisclaimer(page);
  });
});
