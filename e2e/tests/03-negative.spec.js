// Negative and edge-case testing through the real UI.
const { test, expect } = require('@playwright/test');
const { uniqueEmail, watchConsole, login, apiSession, registerViaApi, createEventViaApi, retireEvent, shot, RUN } = require('./helpers');

test('invalid registration, duplicate email and empty forms show inline errors', async ({ page, playwright, baseURL }) => {
  const problems = watchConsole(page);
  await page.goto('/register.html');
  await page.click('#register-form button[type=submit]');
  await expect(page.locator('#register-form .field.invalid')).toHaveCount(4);
  await page.fill('#fullName', 'X');
  await page.fill('#email', 'not-an-email');
  await page.fill('#password', 'password123');
  await page.fill('#confirmPassword', 'password123');
  await page.click('#register-form button[type=submit]');
  await expect(page.locator('#register-form')).toContainText('Enter a valid email address.');
  await expect(page.locator('#register-form')).toContainText('Must be between 2 and 100 characters.');
  await page.fill('#password', 'short');
  await page.fill('#confirmPassword', 'different');
  await page.click('#register-form button[type=submit]');
  await expect(page.locator('#register-form')).toContainText('Use at least 8 characters.');
  await expect(page.locator('#register-form')).toContainText('Passwords do not match.');
  await shot(page, '20-register-errors');

  const taken = uniqueEmail('taken');
  await registerViaApi(playwright, baseURL, taken, 'password123');
  await page.fill('#fullName', 'Someone Else');
  await page.fill('#email', taken);
  await page.fill('#password', 'password123');
  await page.fill('#confirmPassword', 'password123');
  await page.click('#register-form button[type=submit]');
  await expect(page.locator('.form-alert')).toContainText('already exists');
  expect(problems).toEqual([]);
});

test('wrong password and empty login form', async ({ page, playwright, baseURL }) => {
  const email = uniqueEmail('wrongpw');
  await registerViaApi(playwright, baseURL, email, 'password123');
  await page.goto('/login.html');
  await page.click('#login-form button[type=submit]');
  await expect(page.locator('#login-form .field.invalid')).toHaveCount(2);
  await page.fill('#email', email);
  await page.fill('#password', 'not-the-password1');
  await page.click('#login-form button[type=submit]');
  await expect(page.locator('.form-alert')).toHaveText('Email or password is incorrect.');
  await expect(page).toHaveURL(/login\.html/);
});

test('booking limits, stale availability and sold-out events', async ({ page, playwright, baseURL }) => {
  const small = await createEventViaApi(playwright, baseURL, { title: `E2E Tiny ${RUN}`, capacity: 3, price: 100 });
  const email = uniqueEmail('limits');
  await registerViaApi(playwright, baseURL, email, 'password123');
  await login(page, email, 'password123');
  await page.goto(`/event.html?id=${small.id}`);

  // the stepper cannot go below 1 or above what is available
  await expect(page.locator('[data-step="-1"]')).toBeDisabled();
  await page.fill('#qty', '-4');
  await page.locator('#qty').dispatchEvent('change');
  await expect(page.locator('#qty')).toHaveValue('1');
  await page.fill('#qty', '50');
  await page.locator('#qty').dispatchEvent('change');
  await expect(page.locator('#qty')).toHaveValue('3');

  // another customer buys 2 tickets while this page is open -> the server rejects the stale request
  const rival = uniqueEmail('rival');
  await registerViaApi(playwright, baseURL, rival, 'password123');
  const r = await apiSession(playwright, baseURL, rival, 'password123');
  expect((await r.call('POST', '/api/bookings', { eventId: small.id, quantity: 2 })).status).toBe(201);
  await page.click('[data-review]');
  await page.click('[data-confirm]');
  await expect(page.locator('#booking-area .alert-error')).toContainText('Only 1 ticket(s) left');
  await expect(page.locator('#book-panel')).toContainText('1 ticket left');
  await shot(page, '21-booking-stale-availability');

  // the rival takes the last ticket -> the page shows the event as sold out with no booking button
  expect((await r.call('POST', '/api/bookings', { eventId: small.id, quantity: 1 })).status).toBe(201);
  await r.dispose();
  await page.reload();
  await expect(page.locator('#booking-area')).toContainText('sold out');
  await expect(page.locator('[data-review]')).toHaveCount(0);
  await shot(page, '22-sold-out');
  await retireEvent(playwright, baseURL, small.id);
});

test('another user\'s booking, invalid ids and double cancellation', async ({ page, playwright, baseURL }) => {
  const ev = await createEventViaApi(playwright, baseURL, { title: `E2E Private ${RUN}`, capacity: 10, price: 100 });
  const owner = uniqueEmail('owner');
  await registerViaApi(playwright, baseURL, owner, 'password123');
  const o = await apiSession(playwright, baseURL, owner, 'password123');
  const ref = (await o.call('POST', '/api/bookings', { eventId: ev.id, quantity: 1 })).body.data.booking.reference;

  const snoop = uniqueEmail('snoop');
  await registerViaApi(playwright, baseURL, snoop, 'password123');
  await login(page, snoop, 'password123');
  await page.goto(`/booking.html?ref=${ref}`);
  await expect(page.locator('#booking-root')).toContainText('Booking not found');
  await page.goto('/booking.html?ref=../../etc/passwd');
  await expect(page.locator('#booking-root')).toContainText('Booking not found');
  await page.goto('/event.html?id=99999999');
  await expect(page.locator('#event-root')).toContainText('Event not found');
  await page.goto('/event.html?id=abc');
  await expect(page.locator('#event-root')).toContainText('Event not found');
  await page.goto('/admin/index.html');
  await expect(page).toHaveURL(/denied=admin/);

  // double cancellation: owner cancels in the UI while a stale tab tries again
  await page.click('[data-logout]');
  await login(page, owner, 'password123');
  await page.goto(`/booking.html?ref=${ref}`);
  const second = await page.context().newPage();
  await second.goto(`/booking.html?ref=${ref}`);
  await page.click('[data-cancel]');
  await page.locator('dialog [data-ok]').click();
  await expect(page.locator('#booking-root .badge').first()).toHaveText(/cancelled/i);
  await second.click('[data-cancel]');
  await second.locator('dialog [data-ok]').click();
  await expect(second.locator('.toast')).toContainText('already cancelled');
  await expect(second.locator('#booking-root .badge').first()).toHaveText(/cancelled/i);
  const api = await o.call('GET', `/api/events/${ev.id}`);
  expect(api.body.data.ticketsSold).toBe(0);
  await o.dispose();
  await retireEvent(playwright, baseURL, ev.id);
});

test('expired session sends the user back to login', async ({ page, context, playwright, baseURL }) => {
  const email = uniqueEmail('expiry');
  await registerViaApi(playwright, baseURL, email, 'password123');
  await login(page, email, 'password123');
  await page.goto('/bookings.html');
  // simulate the server-side session expiring: the browser still sends a cookie that is no longer valid
  await context.clearCookies();   // WebKit would otherwise keep the real cookie next to the fake one
  await context.addCookies([{ name: 'BMSESSION', value: 'expired-session-id', url: baseURL }]);
  await page.locator('.tabs [data-filter="PAST"]').click();
  await expect(page).toHaveURL(/login\.html\?next=.*reason=expired/);
  await expect(page.locator('#login-notice')).toContainText('session expired');
});

test('API unavailable shows a friendly error instead of a blank page', async ({ page }) => {
  await page.route('**/api/home', route => route.abort('connectionrefused'));
  await page.goto('/');
  await expect(page.locator('#upcoming')).toContainText('Cannot reach BookMatrix');
  await page.unroute('**/api/home');
  await page.click('[data-retry]');
  await expect(page.locator('#upcoming .event-row').first()).toBeVisible();
});

test('broken event images fall back to the typographic poster', async ({ page }) => {
  await page.route('**/images/events/**', route => route.fulfill({ status: 404, body: '' }));
  await page.goto('/');
  await expect(page.locator('#featured .poster-fallback').first()).toBeVisible();
  await expect(page.locator('#featured img[data-poster-img]')).toHaveCount(0);
});
