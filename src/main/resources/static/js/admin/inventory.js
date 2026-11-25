import { $, html, api, qs, formatDateTime, formatMoney, badge, errorState, skeletonRows } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('inventory');

async function load() {
  $('#table').innerHTML = skeletonRows(4);
  try {
    const { data } = await api(`/api/admin/inventory${qs({ includeArchived: $('#include-archived').checked })}`);
    const totals = data.reduce((t, r) => ({ cap: t.cap + r.capacity, sold: t.sold + r.ticketsSold, bad: t.bad + (r.consistent ? 0 : 1) }), { cap: 0, sold: 0, bad: 0 });
    $('#summary').innerHTML = html`<div class="metrics">
      <div class="metric"><div class="label">Events</div><div class="value">${data.length}</div></div>
      <div class="metric"><div class="label">Total capacity</div><div class="value">${totals.cap.toLocaleString('en-IN')}</div></div>
      <div class="metric"><div class="label">Sold</div><div class="value">${totals.sold.toLocaleString('en-IN')}</div></div>
      <div class="metric"><div class="label">Available</div><div class="value">${(totals.cap - totals.sold).toLocaleString('en-IN')}</div></div>
      <div class="metric ${totals.bad ? 'emph' : ''}"><div class="label">Consistency</div><div class="value">${totals.bad ? `${totals.bad} issue(s)` : 'All OK'}</div></div></div>`;
    $('#table').innerHTML = html`<div class="table-wrap"><table class="table">
      <thead><tr><th>Event</th><th>Starts</th><th class="num">Capacity</th><th class="num">Sold + held</th><th class="num">Paid / held</th><th class="num">Seats taken</th><th class="num">Available</th><th class="num">Revenue</th><th>Check</th></tr></thead>
      <tbody>${data.length ? data.map(r => html`<tr>
        <td><a href="/admin/event.html?id=${r.eventId}">${r.title}</a><div class="small muted">${r.city}${r.archived ? ' · archived' : ''}${r.status === 'CANCELLED' ? ' · cancelled' : ''}</div></td>
        <td class="small">${formatDateTime(r.startsAt)}</td>
        <td class="num">${r.capacity}</td><td class="num">${r.ticketsSold} <span class="muted small">(${r.percentSold}%)</span></td>
        <td class="num">${r.confirmedTickets} / ${r.heldTickets}</td><td class="num">${r.seatsTaken ?? '—'}</td><td class="num">${r.available}</td><td class="num">${formatMoney(r.revenue)}</td>
        <td>${r.consistent ? badge('ACTIVE', 'Match') : badge('CANCELLED', 'Mismatch')}</td></tr>`)
        : html`<tr><td colspan="9" class="table-empty">No events yet.</td></tr>`}</tbody></table></div>`;
  } catch (err) {
    $('#table').innerHTML = errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

$('#include-archived').addEventListener('change', load);
load();
