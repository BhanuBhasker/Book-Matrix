import { $, html, api, param, renderLayout, badge, formatDateTime, formatMoney, emptyState, errorState,
  confirmDialog, toast, withBusy, plural, relativeDay, downloadCalendarFile, directionsUrl, bookingBadge } from '../core.js?v=2026112505';

await renderLayout({ active: 'bookings', requireLogin: true });
const root = $('#booking-root');
const ref = (param('ref') || '').trim().toUpperCase();

async function load() {
  if (!/^BM[A-Z0-9]{8}$/.test(ref)) {       // obviously invalid references never reach the server
    root.innerHTML = emptyState('Booking not found', 'Check the reference, or open it from your bookings list.', html`<a class="btn" href="/bookings.html">My bookings</a>`);
    return;
  }
  try {
    const { data } = await api(`/api/bookings/${ref}`);
    render(data);
  } catch (err) {
    root.innerHTML = err.status === 404
      ? emptyState('Booking not found', 'Check the reference, or open it from your bookings list.', html`<a class="btn" href="/bookings.html">My bookings</a>`)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function render(b) {
  document.title = `Booking ${b.reference} — BookMatrix`;
  const cancelled = b.status === 'CANCELLED';
  root.innerHTML = html`
    <nav class="small muted" style="padding-top:20px" aria-label="Breadcrumb"><a href="/bookings.html">My bookings</a> / ${b.reference}</nav>
    <div class="page-head spread" style="margin-bottom:20px">
      <div>
        <div class="row">${bookingBadge(b)}
          <span class="mono">${b.reference}</span></div>
        <h1 style="margin-top:8px">${b.eventTitle}</h1>
        <p class="muted" style="margin:0">${formatDateTime(b.eventStartsAt)}${!cancelled && relativeDay(b.eventStartsAt) ? html` <span class="soon">${relativeDay(b.eventStartsAt)}</span>` : ''} · ${b.venueName}, ${b.city}</p>
      </div>
      <div class="row">
        ${!cancelled && !b.eventPast ? html`<button type="button" class="btn btn-secondary" data-calendar>Add to calendar</button>` : ''}
        <a class="btn btn-secondary" href="${directionsUrl(b.venueName, '', b.city)}" target="_blank" rel="noopener">Directions ↗</a>
        <a class="btn btn-secondary" href="/api/bookings/${b.reference}/receipt" download>Download receipt</a>
        <a class="btn btn-secondary" href="/event.html?id=${b.eventId}&v=2026112505">View event</a>
      </div>
    </div>
    ${b.eventStatus === 'CANCELLED' ? html`<div class="alert alert-warn">The organiser cancelled this event. Your booking was cancelled${b.paidAt ? ' and refunded in full' : ''}.</div>` : ''}
    ${b.payable ? html`<div class="alert alert-warn spread" role="status"><span>Payment pending — your ${b.seatLabels ? 'seats are' : 'tickets are'} held until ${formatDateTime(b.holdExpiresAt)}.</span>
        <a class="btn btn-accent btn-sm" href="/checkout.html?ref=${b.reference}">Complete payment</a></div>` : ''}
    ${b.status === 'EXPIRED' ? html`<div class="alert alert-info">This booking expired because it wasn't paid in time. Nothing was charged. <a href="/event.html?id=${b.eventId}&v=2026112505">Book again</a></div>` : ''}
    <div class="grid-2">
      <section class="card">
        <h2>Payment</h2>
        <table class="summary">
          <tr><td>Tickets</td><td>${b.quantity}</td></tr>
          ${b.seatLabels ? html`<tr><td>Seats</td><td><strong>${b.seatLabels}</strong></td></tr>` : ''}
          <tr><td>Price per ticket (at booking time)</td><td>${formatMoney(b.unitPrice)}</td></tr>
          <tr class="total"><td>${cancelled && b.paidAt ? 'Refunded' : b.paidAt ? 'Total paid' : 'Total due'}</td><td>${formatMoney(b.totalAmount)}</td></tr>
        </table>
        ${paymentList(b.payments)}
      </section>
      <section class="card">
        <h2>Details</h2>
        <dl class="kv">
          <dt>Booked on</dt><dd>${formatDateTime(b.bookedAt)}</dd>
          <dt>Status</dt><dd>${bookingBadge(b)}</dd>
          ${b.paidAt ? html`<dt>Paid on</dt><dd>${formatDateTime(b.paidAt)}</dd>` : ''}
          ${cancelled ? html`<dt>Cancelled on</dt><dd>${formatDateTime(b.cancelledAt)}</dd>
            <dt>Reason</dt><dd>${b.cancellationReason || '—'}</dd>` : ''}
        </dl>
      </section>
    </div>
    <section style="margin-top:24px" id="cancel-area">
      ${cancelled || b.status === 'EXPIRED' ? '' : b.cancellable && b.status === 'PENDING_PAYMENT'
        ? html`<div class="card spread"><div><h2 style="margin:0">Changed your mind?</h2><p class="small muted" style="margin:4px 0 0">Release the hold so others can book. Nothing has been charged.</p></div>
            <button class="btn btn-danger" type="button" data-cancel>Release hold</button></div>`
        : b.cancellable
        ? html`<div class="card spread"><div><h2 style="margin:0">Can't make it?</h2><p class="small muted" style="margin:4px 0 0">Cancel now to release ${plural(b.quantity, 'ticket')} and get a full refund of ${formatMoney(b.totalAmount)}.</p></div>
            <button class="btn btn-danger" type="button" data-cancel>Cancel booking</button></div>`
        : html`<p class="small muted">${b.cancellationBlockReason}</p>`}
    </section>`;
  $('[data-cancel]')?.addEventListener('click', e => cancel(b, e.currentTarget));
  $('[data-calendar]')?.addEventListener('click', () => downloadCalendarFile({
    title: b.eventTitle, startsAt: b.eventStartsAt, endsAt: b.eventEndsAt, venueName: b.venueName, city: b.city,
    reference: b.reference, description: `${plural(b.quantity, 'ticket')}.` }));
}

/** Payment history table (card shown only as brand + last 4 digits). */
function paymentList(payments) {
  if (!payments || !payments.length) return '';
  return html`<p class="label" style="margin:12px 0 6px">Payment history</p>
    <table class="summary small">${payments.map(p => html`<tr>
      <td>${p.kind === 'REFUND' ? 'Refund' : 'Payment'} · ${p.methodLabel}<br><span class="muted">${formatDateTime(p.createdAt)}${p.failureReason ? ' · ' + p.failureReason : ''}</span></td>
      <td>${badge(p.status, p.status === 'FAILED' ? 'Declined' : p.kind === 'REFUND' ? 'Refunded' : 'Paid')}<br>${formatMoney(p.amount)}</td></tr>`)}</table>`;
}

async function cancel(b, button) {
  const held = b.status === 'PENDING_PAYMENT';
  const answer = await confirmDialog({
    title: held ? 'Release this hold?' : 'Cancel this booking?',
    body: held ? `${plural(b.quantity, 'ticket')} for ${b.eventTitle} will go back on sale. Nothing has been charged.`
      : `${plural(b.quantity, 'ticket')} for ${b.eventTitle} will be released and ${formatMoney(b.totalAmount)} refunded. This cannot be undone.`,
    confirmText: held ? 'Release hold' : 'Cancel booking', cancelText: 'Keep booking', danger: true,
    input: { label: 'Reason (optional)', max: 200 },
  });
  if (!answer) return;
  await withBusy(button, async () => {
    try {
      const res = await api(`/api/bookings/${b.reference}/cancel`, { method: 'POST', body: { reason: answer.reason || null } });
      toast(res.message);
      render(res.data);
    } catch (err) {
      toast(err.message, 'error');
      load();
    }
  }, 'Cancelling…');
}

load();
