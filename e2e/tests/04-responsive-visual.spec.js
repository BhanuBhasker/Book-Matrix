// Visual QA: key pages at four screen sizes — no horizontal overflow, no console errors.
const { test, expect } = require('@playwright/test');
const { admin, watchConsole, shot, login } = require('./helpers');

const SIZES = { desktop: { width: 1440, height: 900 }, laptop: { width: 1280, height: 800 }, tablet: { width: 820, height: 1180 }, mobile: { width: 390, height: 844 } };
const PUBLIC = ['/', '/events.html', '/event.html?id=1', '/login.html', '/register.html'];
const ADMIN = ['/admin/index.html', '/admin/events.html', '/admin/users.html', '/admin/bookings.html', '/admin/inventory.html', '/admin/data.html', '/admin/java-lab.html'];

async function noOverflow(page) {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
}

for (const [name, viewport] of Object.entries(SIZES)) {
  test(`public pages fit on ${name}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const problems = watchConsole(page);
    for (const url of PUBLIC) {
      await page.goto(url, { waitUntil: 'networkidle' });
      expect(await noOverflow(page), `${url} overflows on ${name}`).toBe(true);
    }
    await page.goto('/', { waitUntil: 'networkidle' });
    await shot(page, `30-home-${name}`);
    expect(problems).toEqual([]);
  });

  test(`admin pages fit on ${name}`, async ({ page }) => {
    test.skip(!admin.email, 'admin not configured');
    await page.setViewportSize(viewport);
    await login(page, admin.email, admin.password);
    const problems = watchConsole(page);
    for (const url of ADMIN) {
      await page.goto(url, { waitUntil: 'networkidle' });
      expect(await noOverflow(page), `${url} overflows on ${name}`).toBe(true);
    }
    await page.goto('/admin/index.html', { waitUntil: 'networkidle' });
    await shot(page, `31-admin-${name}`);
    expect(problems).toEqual([]);
  });
}

test('mobile navigation menu opens and is keyboard accessible', async ({ page }) => {
  await page.setViewportSize(SIZES.mobile);
  await page.goto('/');
  const toggle = page.locator('.nav-toggle');
  await expect(page.locator('#main-nav')).toBeHidden();
  await toggle.focus();
  await page.keyboard.press('Enter');
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('#main-nav')).toBeVisible();
  await page.click('#main-nav >> text=Events');
  await expect(page).toHaveURL(/events\.html/);
  await page.click('.filters-toggle');
  await expect(page.locator('#filters')).toBeVisible();
  await shot(page, '32-mobile-filters');
});

test('accessibility basics: labels, alt text, skip link, dialog focus', async ({ page }) => {
  await page.goto('/events.html');
  const unlabeled = await page.evaluate(() => [...document.querySelectorAll('input, select, textarea')]
    .filter(el => el.type !== 'hidden' && !el.labels?.length && !el.getAttribute('aria-label')).map(el => el.name || el.id));
  expect(unlabeled).toEqual([]);
  await page.goto('/');
  const imgsWithoutAlt = await page.evaluate(() => [...document.images].filter(i => !i.hasAttribute('alt')).length);
  expect(imgsWithoutAlt).toBe(0);
  // Safari/WebKit only tabs to links with Option+Tab (its default setting)
  await page.keyboard.press(page.context().browser().browserType().name() === 'webkit' ? 'Alt+Tab' : 'Tab');
  await expect(page.locator('.skip-link')).toBeFocused();
});
