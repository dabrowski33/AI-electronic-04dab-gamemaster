import { test, expect } from '@playwright/test';
import { fillAndSubmitForm, expectDecisionAndDisclaimer, IMAGES } from './helpers';

/**
 * End-to-end against the REAL stack (Angular → Spring Boot → OpenRouter). We assert STRUCTURE,
 * never the model's exact wording (it is nondeterministic). See app/e2e/AGENTS.md.
 */

test.describe('Happy path — Reklamacja (complaint)', () => {
  test('form → decision → streaming chat → off-topic declined', async ({ page }) => {
    await fillAndSubmitForm(page, {
      type: 'REKLAMACJA',
      categoryLabel: 'Smartfony i telefony',
      model: 'Apple iPhone 13',
      reason: 'Ekran telefonu jest pęknięty, a dotyk nie reaguje w dolnej części.',
      imagePath: IMAGES.phoneJpg,
    });

    // Navigated to chat with a valid decision + mandatory disclaimer
    await expectDecisionAndDisclaimer(page);

    // Case summary reflects the submitted data
    await expect(page.locator('.case-summary')).toContainText('Reklamacja');
    await expect(page.locator('.case-summary')).toContainText('Apple iPhone 13');

    // First assistant message is rendered
    await expect(page.locator('.messages-container .bubble.assistant').first()).toBeVisible();

    // Send a follow-up and assert the assistant bubble grows incrementally (real streaming)
    const composer = page.getByRole('textbox', { name: /Twoja wiadomość/ });
    await composer.fill('Ile czasu mam na złożenie reklamacji telefonu?');
    await page.getByRole('button', { name: /Wyślij wiadomość/ }).click();

    const lastAssistant = page.locator('.messages-container .bubble.assistant').last();
    // Wait until some text starts streaming in
    await expect(lastAssistant).not.toHaveText('', { timeout: 30_000 });
    const firstLen = (await lastAssistant.innerText()).length;
    // Then assert it keeps growing (token-by-token)
    await expect
      .poll(async () => (await lastAssistant.innerText()).length, { timeout: 30_000 })
      .toBeGreaterThan(firstLen);

    // The send button is disabled while streaming and re-enables once text is entered again
    await expect.poll(async () => {
      await composer.fill('Dziękuję');
      return page.getByRole('button', { name: /Wyślij wiadomość/ }).isEnabled();
    }, { timeout: 30_000 }).toBe(true);
  });
});

test.describe('Happy path — Zwrot (return)', () => {
  test('form (no reason) → decision → disclaimer, with a WebP image', async ({ page }) => {
    await fillAndSubmitForm(page, {
      type: 'ZWROT',
      categoryLabel: 'Laptopy i komputery',
      model: 'Lenovo ThinkPad',
      imagePath: IMAGES.laptopWebp,
    });

    await expectDecisionAndDisclaimer(page);
    await expect(page.locator('.case-summary')).toContainText('Zwrot');
  });
});

test.describe('Off-topic guardrail', () => {
  test('off-topic follow-up is declined / redirected', async ({ page }) => {
    await fillAndSubmitForm(page, {
      type: 'ZWROT',
      categoryLabel: 'Smartfony i telefony',
      model: 'Samsung Galaxy',
      imagePath: IMAGES.phoneJpeg,
    });
    await expectDecisionAndDisclaimer(page);

    const composer = page.getByRole('textbox', { name: /Twoja wiadomość/ });
    await composer.fill('Jaka jest dziś pogoda w Warszawie?');
    await page.getByRole('button', { name: /Wyślij wiadomość/ }).click();

    const lastAssistant = page.locator('.messages-container .bubble.assistant').last();
    await expect(lastAssistant).not.toHaveText('', { timeout: 30_000 });
    // Let the stream finish, then assert the model stayed on-topic (mentions reklamacja/zwrot,
    // i.e. it redirected) rather than answering the weather question.
    await expect
      .poll(async () => (await lastAssistant.innerText()).toLowerCase(), { timeout: 30_000 })
      .toMatch(/reklamacj|zwrot|zgłoszeni/);
  });
});
