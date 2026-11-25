import { $, html, api, param, qs, formatDateTime, formatMoney, formatPrice, badge, errorState, emptyState, confirmDialog,
  toast, withBusy, renderPagination, plural } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('events');
const id = Number(param('id'));
const root = $('#event-root');
let page = 1;
if (param('saved')) toast('Saved.');

async function load() {
  try {
    const { data: e } = await api(`/api/admin/events/${id}`);
    render(e);
    loadBookings();
  } catch (err) {
    root.innerHTML = err.status === 404 || !id
      ? emptyState('Event not found', 'It may have been deleted.', html`<a class="btn" href="/admin/events.html">All events</a>`)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function render(e) {
  document.title = `${e.title} — BookMatrix Admin`;
  const editable = !['CANCELLED', 'COMPLETED'].includes(e.displayStatus) && !e.archived;
  const pct = Math.round(100 * e.ticketsSold / e.capacity);
  root.innerHTML = html`
    <div class="page-title">
      <div><p class="eyebrow"><a href="/admin/events.html">Events</a> / #${e.id}</p><h1>${e.title}</h1>
        <div class="row" style="margin-top:8px">${badge(e.displayStatus, e.displayStatusLabel)}${e.featured ? html`<span class="badge badge-accent">Featured</span>` : ''}
          <span class="small muted">${e.categoryName}</span></div></div>
      <div class="row">
        ${!e.archived ? html`<a class="btn btn-secondary" href="/event.html?id=${e.id}&v=2026112505" target="_blank" rel="noopener">View public page</a>` : ''}
        ${editable ? html`<a class="btn" href="/admin/event-form.html?id=${e.id}">Edit</a>` : ''}
      </div>
    </div>
    <div class="grid-2">
      <section class="card">
        <h2>Details</h2>
        <dl class="kv">
          <dt>When</dt><dd>${formatDateTime(e.startsAt)} – ${formatDateTime(e.endsAt)}</dd>
          <dt>Venue</dt><dd>${e.venueName}, ${e.city}${e.venueAddress ? html`<div class="small muted">${e.venueAddress}</div>` : ''}</dd>
          <dt>Ticket price</dt><dd>${formatPrice(e.ticketPrice)}</dd>
          <dt>Seating</dt><dd>${e.seatingType === 'RESERVED' ? `Reserved seats — ${e.seatRows} rows × ${e.seatsPerRow} seats` : 'General admission'}</dd>
          ${e.cancelledAt ? html`<dt>Cancelled</dt><dd>${formatDateTime(e.cancelledAt)}</dd>` : ''}
          <dt>Booking</dt><dd>${e.bookable ? 'Open' : e.bookingBlockReason}</dd>
        </dl>
      </section>
      <section class="card">
        <h2>Inventory</h2>
        <div class="metrics" style="grid-template-columns:repeat(3,1fr)">
          <div class="metric"><div class="label">Capacity</div><div class="value">${e.capacity}</div></div>
          <div class="metric"><div class="label">Sold</div><div class="value">${e.ticketsSold}</div></div>
          <div class="metric"><div class="label">Available</div><div class="value">${e.availableTickets}</div></div>
        </div>
        <div class="meter ${pct >= 100 ? 'full' : pct >= 85 ? 'warn' : ''}"><span style="width:${Math.min(100, pct)}%"></span></div>
        <p class="small muted">${pct}% sold. Capacity can be raised any time, but never lowered below tickets already sold.</p>
      </section>
    </div>
    ${e.seatingType === 'RESERVED' ? html`<section class="card" style="margin-top:24px"><h2>Seat occupancy</h2><div id="admin-seatmap" class="small muted">Loading seats…</div></section>` : ''}
    <section class="card" style="margin-top:24px">
      <h2>Actions</h2>
      <div class="row">
        ${editable ? html`<button class="btn btn-secondary" type="button" data-feature>${e.featured ? 'Remove from featured' : 'Feature on home page'}</button>` : ''}
        ${e.status !== 'CANCELLED' && e.displayStatus !== 'COMPLETED' && !e.archived ? html`<button class="btn btn-danger" type="button" data-cancel-event>Cancel event…</button>` : ''}
        ${e.archived ? html`<button class="btn btn-secondary" type="button" data-restore>Restore from archive</button>` : html`<button class="btn btn-danger" type="button" data-delete>Delete / archive…</button>`}
      </div>
      <p class="small muted" style="margin:10px 0 0">Cancelling refunds every booking and frees all tickets. Events with bookings are archived (hidden but kept) instead of deleted.</p>
    </section>
    <section style="margin-top:24px">
      <div class="spread"><h2 style="font-size:18px">Bookings for this event</h2>
        <select class="input" id="bk-filter" style="width:auto" aria-label="Filter bookings"><option value="ALL">All</option><option value="CONFIRMED">Confirmed</option><option value="CANCELLED">Cancelled</option></select></div>
      <div id="bookings" style="margin-top:12px"></div>
      <nav class="pagination" id="pagination" aria-label="Pagination"></nav>
    </section>`;
  $('#bk-filter').addEventListener('change', () => { page = 1; loadBookings(); });
  if (e.seatingType === 'RESERVED' && !e.archived) loadSeatMap();
  $('[data-feature]')?.addEventListener('click', ev => withBusy(ev.currentTarget, async () => {
    try {
      const res = await api(`/api/admin/events/${id}/featured`, { method: 'PATCH', body: { featured: !e.featured } });
      toast(res.data.featured ? 'Event featured on the home page.' : 'Removed from featured.');
      render(res.data); loadBookings();
    } catch (err) { toast(err.message, 'error'); }
  }));
  $('[data-cancel-event]')?.addEventListener('click', async ev => {
    const button = ev.currentTarget;   // read before any await: currentTarget is null afterwards
    const answer = await confirmDialog({
      title: 'Cancel this event?', danger: true, confirmText: 'Cancel event', cancelText: 'Keep event',
      body: `All confirmed bookings (${plural(e.ticketsSold, 'ticket')}) will be cancelled and refunded. This cannot be undone.`,
      input: { label: 'Reason shown to customers (optional)', max: 200 },
    });
    if (!answer) return;
    withBusy(button, async () => {
      try {
        const res = await api(`/api/admin/events/${id}/cancel`, { method: 'POST', body: { reason: answer.reason || null } });
        toast(res.message);
        load();
      } catch (err) { toast(err.message, 'error'); }
    });
  });
  $('[data-delete]')?.addEventListener('click', async ev => {
    const button = ev.currentTarget;   // read before any await: currentTarget is null afterwards
    const ok = await confirmDialog({
      title: 'Delete this event?', danger: true, confirmText: 'Delete', requireText: 'DELETE',
      body: 'Events without bookings are deleted permanently. Events with booking history are archived instead (only possible once the event is cancelled or completed).',
    });
    if (!ok) return;
    withBusy(button, async () => {
      try {
        const res = await api(`/api/admin/events/${id}`, { method: 'DELETE' });
        toast(res.message);
        if (res.data.outcome === 'DELETED') location.href = '/admin/events.html';
        else load();
      } catch (err) { toast(err.message, 'error'); }
    });
  });
  $('[data-restore]')?.addEventListener('click', ev => withBusy(ev.currentTarget, async () => {
    try {
      const res = await api(`/api/admin/events/${id}/restore`, { method: 'POST' });
      toast(res.message);
      render(res.data); loadBookings();
    } catch (err) { toast(err.message, 'error'); }
  }));
}

/** Read-only seat map: which seats are taken (held or sold). */
async function loadSeatMap() {
  const box = $('#admin-seatmap');
  try {
    const { data: m } = await api(`/api/events/${id}/seats`);
    const taken = new Set(m.taken);
    const numbers = Array.from({ length: m.seatsPerRow }, (_, i) => i + 1);
    box.innerHTML = html`<div class="seatmap-scroll"><div class="seatmap readonly" aria-label="Seat occupancy">
      <div class="stage" aria-hidden="true">Stage</div>
      ${m.rowLabels.map(r => html`<div class="seat-row"><span class="row-label">${r}</span>
        ${numbers.map(n => html`<span class="seat ${taken.has(r + n) ? 'taken' : ''}" title="${r}${n}${taken.has(r + n) ? ' — taken' : ' — free'}">${n}</span>`)}
        <span class="row-label">${r}</span></div>`)}
    </div></div>
    <p class="small muted" style="margin:8px 0 0">${m.taken.length} of ${m.rows * m.seatsPerRow} seats taken · ${m.available} free</p>`;
  } catch (err) {
    box.textContent = err.message;
  }
}

async function loadBookings() {
  const box = $('#bookings');
  if (!box) return;
  try {
    const { data } = await api(`/api/admin/events/${id}/bookings${qs({ status: $('#bk-filter').value, page, size: 10 })}`);
    box.innerHTML = data.items.length ? html`<div class="table-wrap"><table class="table">
      <thead><tr><th>Reference</th><th>Customer</th><th>Booked</th><th class="num">Tickets</th><th>Seats</th><th class="num">Total</th><th>Status</th></tr></thead>
      <tbody>${data.items.map(b => html`<tr>
        <td><a class="mono" href="/admin/booking.html?ref=${b.reference}">${b.reference}</a></td>
        <td><a href="/admin/user.html?id=${b.userId}">${b.userName}</a><div class="small muted">${b.userEmail}</div></td>
        <td class="small">${formatDateTime(b.bookedAt)}</td><td class="num">${b.quantity}</td><td class="small">${b.seatLabels || '—'}</td>
        <td class="num">${formatMoney(b.totalAmount)}</td><td>${badge(b.status, b.status === 'PENDING_PAYMENT' ? 'Awaiting payment' : null)}</td></tr>`)}</tbody></table></div>`
      : html`<p class="muted small">No bookings in this view.</p>`;
    renderPagination($('#pagination'), data, p => { page = p; loadBookings(); });
  } catch (err) {
    box.innerHTML = errorState(err);
  }
}

load();
