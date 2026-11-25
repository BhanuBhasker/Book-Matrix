// Reserved seating + demo payment gateway, end to end in a real browser.
const { test, expect } = require('@playwright/test');
const { uniqueEmail, watchConsole, shot, login, logout, registerViaApi, createEventViaApi, retireEvent, RUN } = require('./helpers');

test.describe.configure({ mode: 'serial' });

let event;
test.beforeAll(async ({ playwright, baseURL }) => {
  event = await createEventViaApi(playwright, baseURL, { title: `E2E Theatre ${RUN}`, price: 600, seatRows: 5, seatsPerRow: 8 });
});
test.afterAll(async ({ playwright, baseURL }) => {
  await retireEvent(playwright, baseURL, event.id);
});

test('pick seats on the map, pay by UPI, and the seats show as booked for everyone else', async ({ page, playwright, baseURL }) => {
  const problems = watchConsole(page);
  const first = uniqueEmail('seats1');
  const second = uniqueEmail('seats2');
  await registerViaApi(playwright, baseURL, first, 'password123', 'Seat Buyer');
  await registerViaApi(playwright, baseURL, second, 'password123', 'Second Buyer');

  await test.step('choose two seats; chips and total follow the selection', async () => {
    await login(page, first, 'password123');
    await page.goto(`/event.html?id=${event.id}`);
    await expect(page.locator('.seat-row')).toHaveCount(5);
    await expect(page.locator('button.seat')).toHaveCount(40);
    await expect(page.locator('[data-review]')).toBeDisabled();
    await page.click('[data-seat="C4"]');
    await page.click('[data-seat="C5"]');
    await page.click('[data-seat="A1"]');
    await page.click('[data-unpick="A1"]');                  // remove a seat from its chip
    await expect(page.locator('.seat-pill')).toHaveText([/C4/, /C5/]);
    await expect(page.locator('[data-total]')).toContainText('1,200');
    await expect(page.locator('[data-review]')).toHaveText('Continue with 2 seats');
    await shot(page, '43-seat-selection');
  });

  await test.step('review, then pay with UPI at checkout', async () => {
    await page.click('[data-review]');
    await expect(page.locator('#booking-area')).toContainText('C4');
    await page.click('[data-confirm]');
    await expect(page).toHaveURL(/checkout\.html/);
    await expect(page.locator('#hold-timer')).toContainText('held for');
    await page.click('[data-method="UPI"]');
    await page.fill('#upiId', 'seatbuyer@okbank');
    await page.click('#pay-btn');
    await expect(page.locator('.pay-dialog')).toContainText('Approve the payment request');
    await shot(page, '44-upi-approval');
    await page.click('[data-approve]');
    await expect(page.locator('.ticket-head')).toContainText('Booking confirmed', { timeout: 15_000 });
    await expect(page.locator('.ticket')).toContainText('C4, C5');
    await expect(page.locator('.ticket')).toContainText('UPI');
    await shot(page, '45-payment-confirmed');
  });

  await test.step('another user sees those seats as booked and cannot pick them', async () => {
    await logout(page);
    await login(page, second, 'password123');
    await page.goto(`/event.html?id=${event.id}`);
    await expect(page.locator('[data-seat="C4"]')).toBeDisabled();
    await expect(page.locator('[data-seat="C5"]')).toHaveClass(/taken/);
    await expect(page.locator('[data-seat="C6"]')).toBeEnabled();
  });

  await test.step('a wrong OTP is rejected, and releasing a hold frees the seat', async () => {
    await page.click('[data-seat="E8"]');
    await page.click('[data-review]');
    await page.click('[data-confirm]');
    await page.fill('#cardNumber', '4242 4242 4242 4242');
    await page.fill('#cardName', 'Second Buyer');
    await page.fill('#expiry', '12/30');
    await page.fill('#cvv', '123');
    await page.click('#pay-btn');
    await page.fill('#otp', '000000');
    await page.click('[data-verify]');
    await expect(page.locator('[data-otp-error]')).toContainText('Incorrect OTP');
    await page.click('.pay-dialog [data-cancel]');
    await page.click('[data-release]');
    await page.locator('dialog.dialog [data-ok]').click();
    await expect(page).toHaveURL(new RegExp(`event\\.html\\?id=${event.id}`));
    await expect(page.locator('[data-seat="E8"]')).toBeEnabled();
  });

  expect(problems, problems.join('\n')).toEqual([]);
});
