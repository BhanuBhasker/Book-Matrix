import { $, html, api, qs, formatDateTime, formatMoney, badge, errorState, renderPagination, debounce, skeletonRows } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('users');
const form = $('#toolbar');
let page = 1;
let requestId = 0;
let lastKey = null;

async function load(force = false) {
  const key = JSON.stringify({ ...Object.fromEntries(new FormData(form).entries()), page });
  if (key === lastKey && !force) return;
  lastKey = key;
  const mine = ++requestId;
  $('#table').innerHTML = skeletonRows(4);
  try {
    const params = Object.fromEntries(new FormData(form).entries());
    const { data } = await api(`/api/admin/users${qs({ ...params, page, size: 15 })}`);
    if (mine !== requestId) return;
    $('#table').innerHTML = html`<div class="table-wrap"><table class="table">
      <thead><tr><th>Name</th><th>Role</th><th>Status</th><th class="num">Bookings</th><th class="num">Spent</th><th>Joined</th></tr></thead>
      <tbody>${data.items.length ? data.items.map(r => html`<tr>
        <td><a href="/admin/user.html?id=${r.user.id}">${r.user.fullName}</a><div class="small muted">${r.user.email}</div></td>
        <td>${badge(r.user.role, r.user.role === 'ADMIN' ? 'Admin' : 'Member')}</td>
        <td>${badge(r.user.status)}</td>
        <td class="num">${r.bookingCount}</td>
        <td class="num">${formatMoney(r.totalSpent)}</td>
        <td class="small">${formatDateTime(r.user.createdAt)}</td></tr>`)
        : html`<tr><td colspan="6" class="table-empty">No users match.</td></tr>`}</tbody></table></div>`;
    renderPagination($('#pagination'), data, p => { page = p; load(); });
  } catch (err) {
    $('#table').innerHTML = errorState(err);
    $('[data-retry]')?.addEventListener('click', () => load(true));
  }
}

form.addEventListener('change', () => { page = 1; load(); });
form.elements.q.addEventListener('input', debounce(() => { page = 1; load(); }, 350));
form.addEventListener('submit', e => { e.preventDefault(); load(); });
load();
