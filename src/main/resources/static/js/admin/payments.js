import { $, html, api, qs, formatDateTime, formatMoney, badge, errorState, renderPagination, debounce, skeletonRows } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('payments');
const form = $('#toolbar');
let page = 1;
let requestId = 0;
let lastKey = null;

async function load(force = false) {
  const params = Object.fromEntries(new FormData(form).entries());
  const key = JSON.stringify({ ...params, page });
  if (key === lastKey && !force) return;
  lastKey = key;
  const mine = ++requestId;
  $('#table').innerHTML = skeletonRows(4);
  try {
    const { data } = await api(`/api/admin/payments${qs({ ...params, page, size: 20 })}`);
    if (mine !== requestId) return;
    $('#table').innerHTML = html`<div class="table-wrap"><table class="table">
      <thead><tr><th>When</th><th>Booking</th><th>Type</th><th>Method</th><th>Transaction</th><th class="num">Amount</th><th>Result</th></tr></thead>
      <tbody>${data.items.length ? data.items.map(p => html`<tr>
        <td class="small">${formatDateTime(p.createdAt)}</td>
        <td><a class="mono" href="/admin/booking.html?ref=${p.bookingReference}">${p.bookingReference}</a></td>
        <td>${badge(p.kind, p.kind === 'REFUND' ? 'Refund' : 'Charge')}</td>
        <td class="small">${p.methodLabel}</td>
        <td class="mono small">${p.gatewayRef}</td>
        <td class="num">${p.kind === 'REFUND' ? '−' : ''}${formatMoney(p.amount)}</td>
        <td>${badge(p.status, p.status === 'FAILED' ? 'Declined' : 'Succeeded')}${p.failureReason ? html`<div class="small muted">${p.failureReason}</div>` : ''}</td></tr>`)
        : html`<tr><td colspan="7" class="table-empty">No payments yet.</td></tr>`}</tbody></table></div>`;
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
