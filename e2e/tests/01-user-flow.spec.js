// The complete user journey from the brief, in a real browser.
const { test, expect } = require('@playwright/test');
const { uniqueEmail, watchConsole, shot, logout, createEventViaApi, retireEvent, payWithTestCard, RUN } = require('./helpers');

test.describe.configure({ mode: 'serial' });

let event;
test.beforeAll(async ({ playwright, baseURL }) => {
  event = await createEventViaApi(playwright, baseURL, { title: `E2E Evening ${RUN}`, capacity: 20, price: 450 });
});
test.afterAll(async ({ playwright, baseURL }) => {
  await retireEvent(playwright, baseURL, event.id);
});

test('user journey: register → book → cancel → profile → logout', async ({ page }) => {
  const problems = watchConsole(page);
  const email = uniqueEmail('user');
  const password = 'firstPass123';

  await test.step('1-4 home page loads with real events and no console errors', async () => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Find something worth going out for.');
    await expect(page.locator('#upcoming .event-row').first()).toBeVisible();
    await expect(page.locator('#home-categories .chip')).toHaveCount(8);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(overflow).toBe(false);
    await shot(page, '01-home');
  });

  await test.step('5-6 register a new account', async () => {
    await page.click('#main-nav a.nav-cta');
    await page.fill('#fullName', 'Priya Sharma');
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.fill('#confirmPassword', password);
    await shot(page, '02-register');
    await page.click('#register-form button[type=submit]');
    await expect(page).toHaveURL(/login\.html\?reason=registered/);
    await expect(page.locator('#login-notice')).toContainText('Account created');
    await expect(page.locator('#email')).toHaveValue(email);
  });

  await test.step('7 log in', async () => {
    await page.fill('#password', password);
    await page.click('#login-form button[type=submit]');
    await expect(page.locator('#main-nav')).toContainText('My bookings');
    await expect(page.locator('.nav-user')).toHaveText('Priya Sharma');
  });

  await test.step('8-10 browse, search and filter events', async () => {
    await page.click('#main-nav >> text=Events');
    await expect(page).toHaveURL(/events\.html/);
    await expect(page.locator('#result-count')).toContainText('found');
    await page.fill('#f-q', `E2E Evening ${RUN}`);
    await expect(page.locator('#results .event-row')).toHaveCount(1);
    await page.selectOption('#f-status', 'AVAILABLE');
    await page.selectOption('#f-sort', 'PRICE_ASC');
    await page.fill('input[name=maxPrice]', '500');
    await page.locator('input[name=maxPrice]').dispatchEvent('change');
    await expect(page.locator('#results .event-row')).toHaveCount(1);
    await expect(page).toHaveURL(/status=AVAILABLE/);
    await shot(page, '03-events-filtered');
  });

  await test.step('11 open the event detail page', async () => {
    await page.click(`#results .event-row:has-text("E2E Evening ${RUN}")`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(`E2E Evening ${RUN}`);
    await expect(page.locator('#book-panel')).toContainText('20 tickets left');
  });

  let reference;
  await test.step('12-14 choose 3 tickets, review, pay with the demo gateway, see confirmation', async () => {
    await page.click('[data-step="1"]');
    await page.click('[data-step="1"]');
    await expect(page.locator('#qty')).toHaveValue('3');
    await expect(page.locator('[data-total]')).toContainText('1,350');
    await page.click('[data-review]');
    await expect(page.locator('#booking-area')).toContainText('Review your booking');
    await shot(page, '04-booking-review');
    await page.click('[data-confirm]');
    // a declined test card keeps the hold and shows a clear message
    await page.fill('#cardNumber', '4000 0000 0000 0002');
    await page.fill('#cardName', 'Priya Sharma');
    await page.fill('#expiry', '12/30');
    await page.fill('#cvv', '123');
    await page.click('#pay-btn');
    await page.fill('#otp', '123456');
    await page.click('[data-verify]');
    await expect(page.locator('#pay-form .form-alert')).toContainText('declined');
    reference = await payWithTestCard(page, { shotName: '05a-checkout' });
    expect(reference).toMatch(/^BM[A-Z0-9]{8}$/);
    await expect(page.locator('.ticket')).toContainText('Visa •••• 4242');
    await shot(page, '05-booking-confirmed');
    await page.goto(`/event.html?id=${event.id}`);
    await expect(page.locator('#book-panel')).toContainText('17 tickets left');
  });

  await test.step('15-17 booking appears in My bookings and opens', async () => {
    await page.click('#main-nav >> text=My bookings');
    await expect(page.locator('.booking-row').first()).toContainText(reference);
    await shot(page, '06-my-bookings');
    await page.click(`.booking-row:has-text("${reference}")`);
    await expect(page.locator('.mono').first()).toHaveText(reference);
    await expect(page.locator('#booking-root')).toContainText('Total paid');
  });

  await test.step('18-19 cancel the booking through the confirmation dialog', async () => {
    await page.click('[data-cancel]');
    const dialog = page.locator('dialog.dialog');
    await expect(dialog).toBeVisible();
    await dialog.locator('textarea').fill('Plans changed');
    await shot(page, '07-cancel-dialog');
    await dialog.locator('[data-ok]').click();
    await expect(page.locator('#booking-root .badge').first()).toHaveText(/cancelled/i);
    await expect(page.locator('#booking-root')).toContainText('Plans changed');
    await expect(page.locator('#booking-root')).toContainText('Refunded');
  });

  await test.step('20 inventory is restored on the event page', async () => {
    await page.click('text=View event');
    await expect(page.locator('#book-panel')).toContainText('20 tickets left');
  });

  await test.step('21 update profile', async () => {
    await page.click('#main-nav >> text=Profile');
    await expect(page.locator('#account-info')).toContainText(email);
    await page.fill('#fullName', 'Priya S. Sharma');
    await page.fill('#phone', '+91 98200 12345');
    await page.click('#profile-form button[type=submit]');
    await expect(page.locator('.toast').last()).toContainText('Profile updated');
    await expect(page.locator('.nav-user')).toHaveText('Priya S. Sharma');
    await shot(page, '08-profile');
  });

  await test.step('22 change password', async () => {
    await page.fill('#currentPassword', password);
    await page.fill('#newPassword', 'secondPass456');
    await page.fill('#confirmPassword', 'secondPass456');
    await page.click('#password-form button[type=submit]');
    await expect(page.locator('.toast').last()).toContainText('Password changed');
  });

  await test.step('23-24 log out; protected pages now redirect to login', async () => {
    await logout(page);
    await page.goto('/bookings.html');
    await expect(page).toHaveURL(/login\.html\?next=%2Fbookings\.html/);
    await page.goto('/profile.html');
    await expect(page).toHaveURL(/login\.html/);
    await page.goto('/admin/index.html');
    await expect(page).toHaveURL(/login\.html/);
  });

  await test.step('new password works, and login returns to the requested page', async () => {
    await page.goto('/login.html?next=%2Fbookings.html');
    await page.fill('#email', email);
    await page.fill('#password', 'secondPass456');
    await page.click('#login-form button[type=submit]');
    await expect(page).toHaveURL(/bookings\.html/);
    await expect(page.locator('.booking-row')).toHaveCount(1);
  });

  expect(problems, problems.join('\n')).toEqual([]);
});
