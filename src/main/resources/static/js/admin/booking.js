import { $, html, api, param, formatDateTime, formatMoney, badge, errorState, emptyState, confirmDialog, toast, withBusy, plural } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('bookings');
const ref = (param('ref') || '').trim().toUpperCase();
const root = $('#booking-root');

async function load() {
  if (!/^BM[A-Z0-9]{8}$/.test(ref)) {
    root.innerHTML = emptyState('Booking not found', 'Check the reference.', html`<a class="btn" href="/admin/bookings.html">All bookings</a>`);
    return;
  }
  try {
    const { data } = await api(`/api/admin/bookings/${ref}`);
    render(data);
  } catch (err) {
    root.innerHTML = err.status === 404
      ? emptyState('Booking not found', 'Check the reference.', html`<a class="btn" href="/admin/bookings.html">All bookings</a>`)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function render(b) {
  const cancelled = b.status === 'CANCELLED';
  const canCancel = !cancelled && b.status !== 'EXPIRED' && !b.eventPast;
  root.innerHTML = html`
    <div class="page-title"><div><p class="eyebrow"><a href="/admin/bookings.html">Bookings</a></p>
      <h1 class="mono" style="font-size:26px">${b.reference}</h1><div class="row" style="margin-top:8px">${badge(b.status, b.status === 'PENDING_PAYMENT' ? 'Awaiting payment' : null)}</div></div>
      <a class="btn btn-secondary" href="/api/bookings/${b.reference}/receipt">Download receipt</a></div>
    <div class="grid-2">
      <section class="card"><h2>Booking</h2><dl class="kv">
        <dt>Customer</dt><dd><a href="/admin/user.html?id=${b.userId}">${b.userName}</a><div class="small muted">${b.userEmail}</div></dd>
        <dt>Event</dt><dd><a href="/admin/event.html?id=${b.eventId}">${b.eventTitle}</a><div class="small muted">${formatDateTime(b.eventStartsAt)} · ${b.venueName}, ${b.city}</div></dd>
        <dt>Tickets</dt><dd>${b.quantity} × ${formatMoney(b.unitPrice)}</dd>
        ${b.seatLabels ? html`<dt>Seats</dt><dd><strong>${b.seatLabels}</strong></dd>` : ''}
        <dt>Total</dt><dd><strong>${formatMoney(b.totalAmount)}</strong></dd>
        <dt>Booked</dt><dd>${formatDateTime(b.bookedAt)}</dd>
        ${b.paidAt ? html`<dt>Paid</dt><dd>${formatDateTime(b.paidAt)}</dd>` : b.status === 'PENDING_PAYMENT' ? html`<dt>Hold until</dt><dd>${formatDateTime(b.holdExpiresAt)}</dd>` : ''}</dl>
        ${b.payments && b.payments.length ? html`<h3 style="margin:16px 0 8px;font-size:15px">Payment history</h3>
          <table class="table"><tbody>${b.payments.map(p => html`<tr><td>${p.kind === 'REFUND' ? 'Refund' : 'Charge'} · ${p.methodLabel}
            <div class="small muted">${formatDateTime(p.createdAt)} · <span class="mono">${p.gatewayRef}</span>${p.failureReason ? ' · ' + p.failureReason : ''}</div></td>
            <td class="num">${formatMoney(p.amount)}<div>${badge(p.status)}</div></td></tr>`)}</tbody></table>` : ''}</section>
      <section class="card"><h2>Cancellation</h2>
        ${cancelled ? html`<dl class="kv"><dt>Cancelled</dt><dd>${formatDateTime(b.cancelledAt)}</dd>
            <dt>By</dt><dd>${b.cancelledByName || '—'}</dd><dt>Reason</dt><dd>${b.cancellationReason || '—'}</dd>
            <dt>Refund</dt><dd>${formatMoney(b.totalAmount)}</dd></dl>`
          : b.status === 'EXPIRED' ? html`<p class="small muted">This hold expired before payment; its tickets were released automatically.</p>`
          : canCancel ? html`<p class="small muted">Cancelling releases ${plural(b.quantity, 'ticket')} back to inventory${b.paidAt ? ` and refunds ${formatMoney(b.totalAmount)}` : ' (nothing was charged yet)'}. The same transaction rules apply as for customer cancellations.</p>
            <button class="btn btn-danger" type="button" data-cancel>Cancel booking…</button>`
          : html`<p class="small muted">The event has finished, so this booking can no longer be cancelled.</p>`}
      </section>
    </div>`;
  $('[data-cancel]')?.addEventListener('click', async ev => {
    const button = ev.currentTarget;   // read before any await: currentTarget is null afterwards
    const answer = await confirmDialog({ title: 'Cancel this booking?', danger: true, confirmText: 'Cancel booking', cancelText: 'Keep',
      body: `${b.userName}'s ${plural(b.quantity, 'ticket')} for ${b.eventTitle} will be released and refunded.`,
      input: { label: 'Reason (shown to the customer)', max: 200 } });
    if (!answer) return;
    await withBusy(button, async () => {
      try {
        const res = await api(`/api/admin/bookings/${b.reference}/cancel`, { method: 'POST', body: { reason: answer.reason || null } });
        toast(res.message);
        render(res.data);
      } catch (err) { toast(err.message, 'error'); load(); }
    });
  });
}

load();
