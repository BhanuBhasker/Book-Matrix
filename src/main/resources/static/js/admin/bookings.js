import { $, html, api, qs, formatDateTime, formatMoney, badge, errorState, renderPagination, debounce, skeletonRows } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('bookings');
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
    const { data } = await api(`/api/admin/bookings${qs({ ...params, page, size: 15 })}`);
    if (mine !== requestId) return;
    $('#table').innerHTML = html`<div class="table-wrap"><table class="table">
      <thead><tr><th>Reference</th><th>Customer</th><th>Event</th><th>Booked</th><th class="num">Tickets</th><th class="num">Total</th><th>Status</th></tr></thead>
      <tbody>${data.items.length ? data.items.map(b => html`<tr>
        <td><a class="mono" href="/admin/booking.html?ref=${b.reference}">${b.reference}</a></td>
        <td><a href="/admin/user.html?id=${b.userId}">${b.userName}</a><div class="small muted">${b.userEmail}</div></td>
        <td><a href="/admin/event.html?id=${b.eventId}">${b.eventTitle}</a><div class="small muted">${formatDateTime(b.eventStartsAt)}</div></td>
        <td class="small">${formatDateTime(b.bookedAt)}</td>
        <td class="num">${b.quantity}</td><td class="num">${formatMoney(b.totalAmount)}</td>
        <td>${badge(b.status, b.status === 'PENDING_PAYMENT' ? 'Awaiting payment' : null)}${b.seatLabels ? html`<div class="small muted">${b.seatLabels}</div>` : ''}${b.cancelledByName ? html`<div class="small muted">by ${b.cancelledByName}</div>` : ''}</td></tr>`)
        : html`<tr><td colspan="7" class="table-empty">No bookings match.</td></tr>`}</tbody></table></div>`;
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
