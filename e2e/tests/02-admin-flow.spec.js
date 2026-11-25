// The administrator journey from the brief, in a real browser.
const { test, expect } = require('@playwright/test');
const { admin, uniqueEmail, watchConsole, shot, login, logout, apiSession, registerViaApi, isoLocal, RUN } = require('./helpers');

test.describe.configure({ mode: 'serial' });

test('admin journey: dashboard → event CRUD → users → bookings → inventory → archive', async ({ page, playwright, baseURL }) => {
  test.skip(!admin.email || !admin.password, 'BOOKMATRIX_ADMIN_EMAIL/PASSWORD are not configured');
  const problems = watchConsole(page);
  const title = `E2E Admin Show ${RUN}`;
  const customerEmail = uniqueEmail('customer');
  await registerViaApi(playwright, baseURL, customerEmail, 'customer123', 'Rohan Customer');

  await test.step('1-3 log in as admin; dashboard shows real statistics', async () => {
    await login(page, admin.email, admin.password);
    await expect(page).toHaveURL(/admin\/index\.html/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Dashboard');
    const api = await (await page.request.get('/api/admin/dashboard')).json();
    const sold = Number(api.data.counts.tickets_sold).toLocaleString('en-IN');
    await expect(page.locator('.metric:has-text("Tickets sold") .value')).toHaveText(sold);
    await expect(page.locator('.metric:has-text("Users") .value')).toHaveText(Number(api.data.counts.total_users).toLocaleString('en-IN'));
    await shot(page, '10-admin-dashboard');
  });

  let eventId;
  await test.step('4-5 create an event', async () => {
    await page.click('.admin-side >> text=Events');
    await page.click('text=New event');
    await page.fill('#title', title);
    await page.fill('#description', 'A showcase evening created by the admin browser test.');
    await page.selectOption('#categoryId', { label: 'Comedy' });
    await page.fill('#venueName', 'Laugh Store');
    await page.fill('#city', 'Pune');
    await page.fill('#startsAt', isoLocal(15, 20));
    await page.fill('#endsAt', isoLocal(15, 22));
    await page.fill('#ticketPrice', '300');
    await page.fill('#capacity', '40');
    await shot(page, '11-admin-event-form');
    await page.click('#save-btn');
    await expect(page).toHaveURL(/admin\/event\.html\?id=\d+/);
    eventId = Number(new URL(page.url()).searchParams.get('id'));
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
  });

  await test.step('6-8 open and edit the event; verify the update', async () => {
    await page.click('a.btn:has-text("Edit")');
    await expect(page.locator('#form-title')).toHaveText('Edit event');
    await page.fill('#ticketPrice', '350');
    await page.fill('#capacity', '50');
    await page.click('#save-btn');
    await expect(page.locator('.metric:has-text("Capacity") .value')).toHaveText('50');
    await expect(page.locator('#event-root')).toContainText('₹350');
  });

  await test.step('9-10 search and filter events', async () => {
    await page.goto('/admin/events.html');
    await page.fill('#a-q', `Admin Show ${RUN}`);
    await expect(page.locator('#table tbody tr')).toHaveCount(1);
    await page.selectOption('#a-status', 'UPCOMING');
    await expect(page.locator('#table tbody tr')).toContainText(title);
    await page.selectOption('#a-status', 'CANCELLED');
    await expect(page.locator('#table tbody')).toContainText('No events match');
    await shot(page, '12-admin-events');
  });

  let reference;
  await test.step('11 a customer books; admin sees it on the event page', async () => {
    const customer = await apiSession(playwright, baseURL, customerEmail, 'customer123');
    const r = await customer.call('POST', '/api/bookings', { eventId, quantity: 4 });
    expect(r.status).toBe(201);
    reference = r.body.data.booking.reference;
    await customer.dispose();
    await page.goto(`/admin/event.html?id=${eventId}`);
    await expect(page.locator('#bookings')).toContainText(reference);
    await expect(page.locator('.metric:has-text("Sold") .value')).toHaveText('4');
  });

  await test.step('12-14 search users, open details, see booking history', async () => {
    await page.click('.admin-side >> text=Users');
    await page.fill('#u-q', customerEmail);
    await expect(page.locator('#table tbody tr')).toHaveCount(1);
    await page.click(`#table >> text=Rohan Customer`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Rohan Customer');
    await expect(page.locator('#bookings')).toContainText(reference);
    await shot(page, '13-admin-user');
  });

  await test.step('15 deactivate then re-activate the user', async () => {
    await page.click('[data-status="deactivate"]');
    await page.locator('dialog [data-ok]').click();
    await expect(page.locator('.page-title')).toContainText(/deactivated/i);
    const blocked = await apiSession(playwright, baseURL);
    const r = await blocked.call('POST', '/api/auth/login', { email: customerEmail, password: 'customer123' });
    expect(r.body.error.code).toBe('ACCOUNT_DEACTIVATED');
    await blocked.dispose();
    await page.click('[data-status="activate"]');
    await expect(page.locator('.page-title .badge').nth(1)).toHaveText(/active/i);
  });

  await test.step('16-18 find the booking, cancel it, inventory stays consistent', async () => {
    await page.click('.admin-side >> text=Bookings');
    await page.fill('#bk-q', reference);
    await expect(page.locator('#table tbody tr')).toHaveCount(1);
    await page.click(`#table >> text=${reference}`);
    await page.click('[data-cancel]');
    await page.locator('dialog textarea').fill('Duplicate order');
    await page.locator('dialog [data-ok]').click();
    await expect(page.locator('#booking-root')).toContainText('Duplicate order');
    await shot(page, '14-admin-booking-cancelled');
    await page.click('.admin-side >> text=Inventory');
    await expect(page.locator('.metric:has-text("Consistency") .value')).toHaveText('All OK');
    await expect(page.locator(`#table tr:has-text("${title}")`)).toContainText('Match');
    await shot(page, '15-admin-inventory');
  });

  await test.step('19 cancel and archive the event', async () => {
    await page.goto(`/admin/event.html?id=${eventId}`);
    await page.click('[data-cancel-event]');
    await page.locator('dialog textarea').fill('Venue unavailable');
    await page.locator('dialog [data-ok]').click();
    await expect(page.locator('.page-title .badge').first()).toHaveText(/cancelled/i);
    await page.click('[data-delete]');
    await page.locator('#dlg-confirm').fill('DELETE');
    await page.locator('dialog [data-ok]').click();
    await expect(page.locator('.page-title')).toContainText(/archived/i);
    const pub = await page.request.get(`/api/events/${eventId}`);
    expect(pub.status()).toBe(404);
  });

  await test.step('data page: export links and activity log', async () => {
    await page.click('.admin-side >> text=Import & export');
    await expect(page.locator('#activity tbody tr').first()).toBeVisible();
    const csv = await page.request.get('/api/admin/data/events.csv');
    expect(await csv.text()).toContain(title);
    await shot(page, '16-admin-data');
  });

  await test.step('java lab runs a demo', async () => {
    await page.click('.admin-side >> text=Java Lab');
    await page.click('[data-demo="race"]');
    await expect(page.locator('#console')).toContainText('OVERSOLD', { timeout: 20000 });
    await expect(page.locator('#console')).toContainText('synchronized method');
    await shot(page, '17-admin-java-lab');
  });

  await test.step('20 a normal user cannot reach the admin area', async () => {
    await logout(page);
    await login(page, customerEmail, 'customer123');
    await page.goto('/admin/index.html');
    await expect(page).toHaveURL(/\/\?denied=admin/);
    await expect(page.locator('.toast')).toContainText('administrators only');
    const r = await page.request.get('/api/admin/dashboard');
    expect(r.status()).toBe(403);
  });

  expect(problems, problems.join('\n')).toEqual([]);
});
