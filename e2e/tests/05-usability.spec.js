// Usability helpers: quick dates, filter chips, password helpers, mobile booking bar, calendar export.
const fs = require('fs');
const { test, expect } = require('@playwright/test');
const { uniqueEmail, watchConsole, shot, login, registerViaApi, createEventViaApi, retireEvent, payWithTestCard, RUN } = require('./helpers');

test('quick date shortcuts and removable filter chips', async ({ page }) => {
  const problems = watchConsole(page);
  await page.goto('/events.html?city=Pune&maxPrice=1000');
  await expect(page.locator('#active-filters .filter-chip')).toHaveCount(2);
  await page.click('[data-preset="week"]');
  await expect(page.locator('[data-preset="week"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#f-from')).not.toHaveValue('');
  await expect(page.locator('#active-filters .filter-chip')).toHaveCount(3);
  await expect(page).toHaveURL(/from=\d{4}-\d{2}-\d{2}/);
  await shot(page, '40-events-quick-dates');

  await page.click('#active-filters [data-remove="city"]');                 // remove just one filter
  await expect(page.locator('#active-filters .filter-chip')).toHaveCount(2);
  await expect(page.locator('#f-city')).toHaveValue('');
  await page.click('[data-clear-all]');
  await expect(page.locator('#active-filters .filter-chip')).toHaveCount(0);
  await expect(page.locator('[data-preset=""]')).toHaveAttribute('aria-pressed', 'true');
  expect(problems).toEqual([]);
});

test('password fields can be revealed and the checklist guides the user', async ({ page }) => {
  await page.goto('/register.html');
  await expect(page.locator('#fullName')).toBeFocused();
  await page.fill('#password', 'abc');
  await expect(page.locator('[data-rule="len"]')).not.toHaveClass(/ok/);
  await page.fill('#password', 'abcdef12');
  await page.fill('#confirmPassword', 'abcdef12');
  await expect(page.locator('.pw-checklist li.ok')).toHaveCount(3);
  const toggle = page.locator('#password + .password-toggle');
  await toggle.click();
  await expect(page.locator('#password')).toHaveAttribute('type', 'text');
  await expect(toggle).toHaveAttribute('aria-pressed', 'true');
  await toggle.click();
  await expect(page.locator('#password')).toHaveAttribute('type', 'password');
});

test('events in the next few days get a friendly "Tomorrow" label', async ({ page, playwright, baseURL }) => {
  const ev = await createEventViaApi(playwright, baseURL, { title: `E2E Soon ${RUN}`, days: 1 });
  try {
    await page.goto(`/events.html?q=${encodeURIComponent(`E2E Soon ${RUN}`)}`);
    await expect(page.locator('.event-row .soon')).toHaveText(/Tomorrow|Tonight/);
  } finally {
    await retireEvent(playwright, baseURL, ev.id);
  }
});

test('mobile: sticky "Book tickets" bar, calendar file and copy link', async ({ page, playwright, baseURL }) => {
  const ev = await createEventViaApi(playwright, baseURL, { title: `E2E Mobile ${RUN}`, capacity: 30, price: 250 });
  const email = uniqueEmail('mobile');
  await registerViaApi(playwright, baseURL, email, 'password123');
  try {
    await page.setViewportSize({ width: 390, height: 640 });
    await login(page, email, 'password123');
    await page.goto(`/event.html?id=${ev.id}`);
    const bar = page.locator('#mobile-book-bar');
    await expect(bar).toBeVisible();
    await expect(bar).not.toHaveClass(/hidden/);
    await expect(bar).toContainText('30 tickets left');
    await shot(page, '41-mobile-book-bar');
    await bar.locator('[data-jump]').click();
    await expect(page.locator('#book-panel')).toBeInViewport();
    await expect(bar).toHaveClass(/hidden/);

    const [download] = await Promise.all([page.waitForEvent('download'), page.click('[data-calendar]')]);
    expect(download.suggestedFilename()).toMatch(/\.ics$/);
    const ics = fs.readFileSync(await download.path(), 'utf8');
    expect(ics).toContain('BEGIN:VEVENT');
    expect(ics).toContain(`SUMMARY:E2E Mobile ${RUN}`);
    expect(ics).toMatch(/DTSTART:\d{8}T\d{6}/);

    if (page.context().browser().browserType().name() === 'chromium') {
      await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    }
    await page.click('[data-share]');
    await expect(page.locator('.toast').last()).toContainText('Link copied');

    // the booking flow shows its steps, and the confirmation offers "Add to calendar"
    await expect(page.locator('.steps li[aria-current="step"]')).toHaveText(/Tickets/);
    await page.click('[data-review]');
    await expect(page.locator('.steps li[aria-current="step"]')).toHaveText(/Review/);
    await page.click('[data-confirm]');
    await payWithTestCard(page);
    await expect(page.locator('.ticket [data-calendar]')).toBeVisible();
    await expect(page.locator('#mobile-book-bar')).toHaveCount(0);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow).toBe(false);
    await shot(page, '42-mobile-booking-done');
  } finally {
    await retireEvent(playwright, baseURL, ev.id);
  }
});
