import { $, html, api, formatMoney, formatDateTime, badge, errorState, emptyState } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('dashboard');

const n = v => Number(v || 0).toLocaleString('en-IN');

async function load() {
  try {
    const { data: d } = await api('/api/admin/dashboard');
    const c = d.counts;
    $('#generated').textContent = 'Updated ' + formatDateTime(d.generatedAt);
    const maxCat = Math.max(1, ...Object.values(d.ticketsByCategory));
    $('#dashboard').innerHTML = html`
      ${d.inconsistentEvents > 0 ? html`<div class="alert alert-error">${d.inconsistentEvents} event(s) have inventory that does not match their bookings. <a href="/admin/inventory.html">Review inventory</a></div>` : ''}
      <div class="metrics" aria-label="Key figures">
        ${metric('Revenue (confirmed)', formatMoney(d.revenue), `${formatMoney(d.refunded)} refunded`, true)}
        ${metric('Tickets sold', n(c.tickets_sold), `${n(c.available_inventory)} still available`)}
        ${metric('Bookings', n(c.total_bookings), `${n(c.confirmed_bookings)} confirmed · ${n(c.cancelled_bookings)} cancelled`)}
        ${metric('Avg. booking value', formatMoney(d.averageBookingValue), `${n(c.bookings_last_7_days)} bookings in last 7 days`)}
        ${metric('Payments', n(c.successful_payments), `${n(c.failed_payments)} declined · ${n(c.refunds)} refunds`)}
        ${metric('Awaiting payment', n(c.pending_bookings), `${n(c.held_tickets)} tickets on hold · ${n(c.expired_bookings)} expired`)}
        ${metric('Events', n(c.total_events), `${n(c.archived_events)} archived`)}
        ${metric('Upcoming', n(c.upcoming_events), `${n(c.sold_out_events)} sold out · ${n(c.live_events)} live`)}
        ${metric('Completed', n(c.completed_events), '')}
        ${metric('Cancelled events', n(c.cancelled_events), '')}
        ${metric('Users', n(c.total_users), `${n(c.active_users)} active · ${n(c.admins)} admin`)}
      </div>
      <div class="grid-2" style="margin-top:24px">
        <section class="card">
          <h2>Best-selling events</h2>
          ${d.topEvents.length ? html`<table class="table"><thead><tr><th>Event</th><th class="num">Sold</th><th class="num">Revenue</th></tr></thead><tbody>
            ${d.topEvents.map(t => html`<tr><td><a href="/admin/event.html?id=${t.eventId}">${t.title}</a></td>
              <td class="num">${n(t.ticketsSold)} / ${n(t.capacity)}</td><td class="num">${formatMoney(t.revenue)}</td></tr>`)}
            </tbody></table>` : html`<p class="muted small">No tickets sold yet.</p>`}
        </section>
        <section class="card">
          <h2>Tickets sold by category</h2>
          ${Object.keys(d.ticketsByCategory).length ? html`<div class="bars">${Object.entries(d.ticketsByCategory).map(([cat, v]) => html`
            <div class="bar-row"><span>${cat}</span><span class="bar-track" aria-hidden="true"><span class="bar-fill" style="display:block;width:${Math.round(100 * v / maxCat)}%"></span></span><span class="num">${n(v)}</span></div>`)}</div>`
            : html`<p class="muted small">No events yet.</p>`}
        </section>
      </div>
      <div class="grid-2" style="margin-top:24px">
        <section class="card">
          <div class="spread"><h2>Recent bookings</h2><a class="small" href="/admin/bookings.html">All bookings →</a></div>
          ${d.recentBookings.length ? html`<table class="table"><tbody>${d.recentBookings.map(b => html`<tr>
              <td><a class="mono" href="/admin/booking.html?ref=${b.reference}">${b.reference}</a><div class="small muted">${b.userName}</div></td>
              <td>${b.eventTitle}<div class="small muted">${formatDateTime(b.bookedAt)}</div></td>
              <td class="num">${formatMoney(b.totalAmount)}<div>${badge(b.status)}</div></td></tr>`)}</tbody></table>`
            : html`<p class="muted small">No bookings yet.</p>`}
        </section>
        <section class="card">
          <div class="spread"><h2>Recent activity</h2><a class="small" href="/admin/data.html">Full log →</a></div>
          ${d.recentActivity.length ? html`<table class="table"><tbody>${d.recentActivity.map(a => html`<tr>
              <td><span class="mono" style="font-size:11px">${a.action}</span><div class="small muted">${a.details || ''}</div></td>
              <td class="small muted">${a.actorName || 'system'}<br>${formatDateTime(a.createdAt)}</td></tr>`)}</tbody></table>`
            : html`<p class="muted small">Nothing yet.</p>`}
        </section>
      </div>
      ${c.total_events === 0 ? html`<div style="margin-top:24px">${emptyState('No events yet', 'Create your first event, or import the sample CSV from the Import & export page.',
          html`<div class="row" style="justify-content:center"><a class="btn" href="/admin/event-form.html">New event</a><a class="btn btn-secondary" href="/admin/data.html">Import CSV</a></div>`)}</div>` : ''}`;
  } catch (err) {
    $('#dashboard').innerHTML = errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function metric(label, value, sub, emph = false) {
  return html`<div class="metric ${emph ? 'emph' : ''}"><div class="label">${label}</div><div class="value">${value}</div>${sub ? html`<div class="sub">${sub}</div>` : ''}</div>`;
}

load();
