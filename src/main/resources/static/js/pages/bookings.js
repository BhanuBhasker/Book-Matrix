import { $, $$, html, api, qs, renderLayout, badge, formatDate, formatTime, formatMoney, emptyState, errorState,
  skeletonRows, renderPagination, debounce, plural, relativeDay, bookingBadge } from '../core.js?v=2026112505';

await renderLayout({ active: 'bookings', requireLogin: true });
let filter = 'ALL';
let page = 1;
let requestId = 0;
let lastKey = null;

async function load(force = false) {
  const key = JSON.stringify({ filter, q: $('#b-q').value.trim(), page });
  if (key === lastKey && !force) return;
  lastKey = key;
  const mine = ++requestId;
  $('#list').innerHTML = skeletonRows(3);
  try {
    const { data } = await api(`/api/bookings${qs({ status: filter, q: $('#b-q').value.trim(), page, size: 10 })}`);
    if (mine !== requestId) return;
    if (!data.items.length) {
      const searching = $('#b-q').value.trim() || filter !== 'ALL';
      $('#list').innerHTML = searching
        ? emptyState('Nothing here', 'No bookings match this view.')
        : emptyState('No bookings yet', 'Find something you like, pick how many tickets you need, and your bookings will show up here.', html`<a class="btn" href="/events.html">Browse events</a>`);
    } else {
      $('#list').innerHTML = html`<div style="border-top:1px solid var(--line)">${data.items.map(row)}</div>`;
    }
    renderPagination($('#pagination'), data, p => { page = p; load(); });
  } catch (err) {
    $('#list').innerHTML = errorState(err);
    $('[data-retry]')?.addEventListener('click', () => load(true));
  }
}

function row(b) {
  const status = bookingBadge(b);
  return html`
    <a class="booking-row" href="${b.payable ? `/checkout.html?ref=${b.reference}` : `/booking.html?ref=${b.reference}`}">
      <div>
        <div class="row" style="gap:8px">${status}<span class="ref">${b.reference}</span></div>
        <p class="event-title" style="margin-top:6px">${b.eventTitle}${b.status === 'CONFIRMED' && relativeDay(b.eventStartsAt) ? html` <span class="soon">${relativeDay(b.eventStartsAt)}</span>` : ''}</p>
        <div class="event-meta"><span>${formatDate(b.eventStartsAt)} · ${formatTime(b.eventStartsAt)}</span><span>${b.venueName}, ${b.city}</span></div>
      </div>
      <div class="event-side">
        <span class="price">${formatMoney(b.totalAmount)}</span>
        <span class="avail">${plural(b.quantity, 'ticket')}${b.seatLabels ? ` · ${b.seatLabels}` : ''} · booked ${formatDate(b.bookedAt, { day: 'numeric', month: 'short' })}</span>
        ${b.payable ? html`<span class="btn btn-accent btn-sm" aria-hidden="true">Pay now</span>` : ''}
      </div>
    </a>`;
}

$$('.tabs [data-filter]').forEach(tab => tab.addEventListener('click', () => {
  $$('.tabs [data-filter]').forEach(t => t.setAttribute('aria-selected', String(t === tab)));
  filter = tab.dataset.filter;
  page = 1;
  load();
}));
$('#b-q').addEventListener('input', debounce(() => { page = 1; load(); }, 350));
load();
