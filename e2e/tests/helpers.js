const fs = require('fs');
const path = require('path');
const { expect } = require('@playwright/test');

const ROOT = path.resolve(__dirname, '..', '..');
const SHOTS = path.join(ROOT, 'docs', 'screenshots');

/** Reads KEY=VALUE pairs from the project's .env (admin credentials for the admin flow). */
function readEnv() {
  const file = path.join(ROOT, '.env');
  const env = {};
  if (fs.existsSync(file)) {
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
      const i = line.indexOf('=');
      if (i > 0 && !line.trim().startsWith('#')) env[line.slice(0, i).trim()] = line.slice(i + 1).trim();
    }
  }
  return { ...env, ...process.env };
}

const env = readEnv();
const admin = { email: env.BOOKMATRIX_ADMIN_EMAIL, password: env.BOOKMATRIX_ADMIN_PASSWORD };
const RUN = Date.now().toString(36);

function uniqueEmail(prefix) {
  return `${prefix}.${RUN}.${Math.random().toString(36).slice(2, 7)}@e2e.test`;
}

/** Collects console errors and failed API requests so every test can assert there were none. */
function watchConsole(page) {
  const problems = [];
  page.on('console', m => { if (m.type() === 'error' && !/status of (400|401|402|403|404|409)/.test(m.text())) problems.push(m.text()); });
  page.on('pageerror', e => problems.push(e.message));
  page.on('response', r => { if (r.status() >= 500) problems.push(`${r.status()} ${r.url()}`); });
  return problems;
}

async function shot(page, name) {
  fs.mkdirSync(SHOTS, { recursive: true });
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: true });
}

async function login(page, email, password) {
  await page.goto('/login.html');
  await page.fill('#email', email);
  await page.fill('#password', password);
  await page.click('#login-form button[type=submit]');
  await expect(page.locator('[data-logout]')).toBeAttached();   // may sit inside the collapsed mobile menu
}

async function logout(page) {
  if (!(await page.locator('[data-logout]').isVisible())) await page.click('.nav-toggle');
  await page.click('[data-logout]');
  await expect(page.locator('#main-nav a', { hasText: 'Log in' })).toBeVisible();
}

/** API helper that behaves like the frontend: session cookie + CSRF header. */
async function apiSession(playwright, baseURL, email, password) {
  const ctx = await playwright.request.newContext({ baseURL });
  let token = (await (await ctx.get('/api/auth/session')).json()).data.csrfToken;
  if (email) {
    const res = await ctx.post('/api/auth/login', { data: { email, password }, headers: { 'X-CSRF-Token': token } });
    expect(res.ok(), await res.text()).toBeTruthy();
    token = (await res.json()).data.csrfToken;
  }
  const call = async (method, url, data) => {
    const res = await ctx.fetch(url, { method, data, headers: { 'X-CSRF-Token': token } });
    const body = await res.json();
    return { status: res.status(), body };
  };
  return { ctx, call, dispose: () => ctx.dispose() };
}

async function registerViaApi(playwright, baseURL, email, password, name = 'Test Person') {
  const s = await apiSession(playwright, baseURL);
  const r = await s.call('POST', '/api/auth/register', { fullName: name, email, password, confirmPassword: password });
  expect(r.status, JSON.stringify(r.body)).toBe(201);
  await s.dispose();
}

function isoLocal(daysAhead, hour = 19) {
  const d = new Date(Date.now() + daysAhead * 864e5);
  d.setHours(hour, 0, 0, 0);
  const pad = x => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function createEventViaApi(playwright, baseURL, { title, capacity = 20, price = 450, days = 12, seatRows = null, seatsPerRow = null }) {
  const s = await apiSession(playwright, baseURL, admin.email, admin.password);
  const r = await s.call('POST', '/api/admin/events', {
    title, description: 'An event created by the automated browser tests.', categoryId: 1,
    venueName: 'E2E Hall', city: 'Testville', venueAddress: '1 Test Street',
    startsAt: isoLocal(days), endsAt: isoLocal(days, 22), ticketPrice: price, capacity, featured: false,
    ...(seatRows ? { seatingType: 'RESERVED', seatRows, seatsPerRow, capacity: seatRows * seatsPerRow } : {}),
  });
  expect(r.status, JSON.stringify(r.body)).toBe(201);
  await s.dispose();
  return r.body.data;
}

/** Cancels + archives an event created by a test so test data does not clutter the public site. */
async function retireEvent(playwright, baseURL, id) {
  const s = await apiSession(playwright, baseURL, admin.email, admin.password);
  await s.call('POST', `/api/admin/events/${id}/cancel`, { reason: 'Automated test clean-up' });
  await s.call('DELETE', `/api/admin/events/${id}`);
  await s.dispose();
}

/**
 * On the checkout page: pays with the demo gateway's always-approved Visa test card,
 * enters the demo bank OTP and waits for the confirmation ticket. Returns the booking reference.
 */
async function payWithTestCard(page, { card = '4242 4242 4242 4242', shotName } = {}) {
  await expect(page).toHaveURL(/checkout\.html\?ref=BM/);
  await page.fill('#cardNumber', card);
  await page.fill('#cardName', 'Test Person');
  await page.fill('#expiry', `12/${String((new Date().getFullYear() + 2) % 100).padStart(2, '0')}`);
  await page.fill('#cvv', '123');
  if (shotName) await shot(page, shotName);
  await page.click('#pay-btn');
  await page.fill('#otp', '123456');
  await page.click('[data-verify]');
  await expect(page.locator('.ticket-head')).toContainText('Booking confirmed', { timeout: 15_000 });
  return (await page.locator('.ticket-foot .ref').innerText()).trim();
}

module.exports = { admin, uniqueEmail, watchConsole, shot, login, logout, apiSession, registerViaApi,
  createEventViaApi, retireEvent, isoLocal, payWithTestCard, RUN };
