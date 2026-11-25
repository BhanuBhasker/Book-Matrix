// Runs once after all browser tests (even when some failed): cancels and archives every
// event whose title starts with "E2E " so automated test data never stays on the public site.
const { request } = require('@playwright/test');
const { admin } = require('./tests/helpers');

module.exports = async function globalTeardown(config) {
  if (!admin.email) return;
  const baseURL = config.projects[0].use.baseURL;
  const ctx = await request.newContext({ baseURL });
  try {
    let token = (await (await ctx.get('/api/auth/session')).json()).data.csrfToken;
    const login = await ctx.post('/api/auth/login', { data: admin, headers: { 'X-CSRF-Token': token } });
    token = (await login.json()).data.csrfToken;
    const list = await (await ctx.get('/api/admin/events?q=E2E&size=100&archived=EXCLUDE')).json();
    for (const e of list.data.items.filter(ev => ev.title.startsWith('E2E '))) {
      await ctx.post(`/api/admin/events/${e.id}/cancel`, { data: { reason: 'Automated test clean-up' }, headers: { 'X-CSRF-Token': token } });
      await ctx.delete(`/api/admin/events/${e.id}`, { headers: { 'X-CSRF-Token': token } });
    }
  } finally {
    await ctx.dispose();
  }
};
