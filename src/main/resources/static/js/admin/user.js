import { $, html, api, param, qs, formatDateTime, formatMoney, badge, errorState, emptyState, confirmDialog,
  toast, withBusy, handleForm, renderPagination } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

const me = await initAdmin('users');
const id = Number(param('id'));
const root = $('#user-root');
let page = 1;

async function load() {
  try {
    const { data: u } = await api(`/api/admin/users/${id}`);
    render(u);
    loadBookings();
  } catch (err) {
    root.innerHTML = err.status === 404 || !id
      ? emptyState('User not found', 'This account does not exist.', html`<a class="btn" href="/admin/users.html">All users</a>`)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function render(u) {
  const self = me && me.id === u.id;
  root.innerHTML = html`
    <div class="page-title">
      <div><p class="eyebrow"><a href="/admin/users.html">Users</a> / #${u.id}</p><h1>${u.fullName}</h1>
        <div class="row" style="margin-top:8px">${badge(u.role, u.role === 'ADMIN' ? 'Admin' : 'Member')}${badge(u.status)}${self ? html`<span class="small muted">This is you</span>` : ''}</div></div>
    </div>
    <div class="grid-2">
      <section class="card">
        <h2>Account</h2>
        <dl class="kv"><dt>Email</dt><dd>${u.email}</dd><dt>Phone</dt><dd>${u.phone || '—'}</dd>
          <dt>Joined</dt><dd>${formatDateTime(u.createdAt)}</dd><dt>Last login</dt><dd>${formatDateTime(u.lastLoginAt)}</dd></dl>
        <div class="row" style="margin-top:16px">
          ${self ? html`<p class="small muted">You cannot change your own role or status.</p>` : html`
            ${u.status === 'ACTIVE'
              ? html`<button class="btn btn-danger" type="button" data-status="deactivate">Deactivate account</button>`
              : html`<button class="btn btn-secondary" type="button" data-status="activate">Activate account</button>`}
            ${u.role === 'USER'
              ? html`<button class="btn btn-secondary" type="button" data-role="ADMIN">Make administrator</button>`
              : html`<button class="btn btn-secondary" type="button" data-role="USER">Remove admin role</button>`}`}
        </div>
      </section>
      <section class="card">
        <h2>Edit details</h2>
        <form id="edit-form" novalidate>
          <div class="alert alert-error form-alert" role="alert" hidden></div>
          <div class="field"><label for="fullName">Full name</label><input class="input" id="fullName" name="fullName" maxlength="100" value="${u.fullName}" required></div>
          <div class="field"><label for="phone">Phone</label><input class="input" id="phone" name="phone" maxlength="20" value="${u.phone || ''}"></div>
          <button class="btn" type="submit">Save</button>
        </form>
      </section>
    </div>
    <section style="margin-top:24px">
      <h2 style="font-size:18px">Booking history</h2>
      <div id="bookings"></div>
      <nav class="pagination" id="pagination" aria-label="Pagination"></nav>
    </section>`;

  handleForm($('#edit-form'), async values => {
    const res = await api(`/api/admin/users/${id}`, { method: 'PATCH', body: values });
    toast(res.message);
    render(res.data);
    loadBookings();
  });
  $('[data-status]')?.addEventListener('click', async ev => {
    const button = ev.currentTarget;   // read before any await: currentTarget is null afterwards
    const action = button.dataset.status;
    const ok = action === 'activate' || await confirmDialog({
      title: 'Deactivate this account?', danger: true, confirmText: 'Deactivate',
      body: `${u.fullName} will be logged out and unable to log in. Their bookings stay as they are.`,
    });
    if (!ok) return;
    await withBusy(button, async () => {
      try {
        const res = await api(`/api/admin/users/${id}/${action}`, { method: 'POST' });
        toast(res.message);
        render(res.data); loadBookings();
      } catch (err) { toast(err.message, 'error'); }
    });
  });
  $('[data-role]')?.addEventListener('click', async ev => {
    const button = ev.currentTarget;   // read before any await: currentTarget is null afterwards
    const role = button.dataset.role;
    const ok = await confirmDialog({
      title: role === 'ADMIN' ? 'Make this user an administrator?' : 'Remove administrator role?',
      body: role === 'ADMIN' ? 'Administrators can manage events, users and bookings.' : 'They will become a normal member.',
      confirmText: 'Change role',
    });
    if (!ok) return;
    await withBusy(button, async () => {
      try {
        const res = await api(`/api/admin/users/${id}/role`, { method: 'PUT', body: { role } });
        toast(res.message);
        render(res.data); loadBookings();
      } catch (err) { toast(err.message, 'error'); }
    });
  });
}

async function loadBookings() {
  try {
    const { data } = await api(`/api/admin/users/${id}/bookings${qs({ page, size: 10 })}`);
    $('#bookings').innerHTML = data.items.length ? html`<div class="table-wrap"><table class="table">
      <thead><tr><th>Reference</th><th>Event</th><th>Booked</th><th class="num">Tickets</th><th class="num">Total</th><th>Status</th></tr></thead>
      <tbody>${data.items.map(b => html`<tr><td><a class="mono" href="/admin/booking.html?ref=${b.reference}">${b.reference}</a></td>
        <td><a href="/admin/event.html?id=${b.eventId}">${b.eventTitle}</a><div class="small muted">${formatDateTime(b.eventStartsAt)}</div></td>
        <td class="small">${formatDateTime(b.bookedAt)}</td><td class="num">${b.quantity}</td><td class="num">${formatMoney(b.totalAmount)}</td>
        <td>${badge(b.status)}</td></tr>`)}</tbody></table></div>`
      : html`<p class="muted small">No bookings yet.</p>`;
    renderPagination($('#pagination'), data, p => { page = p; loadBookings(); });
  } catch (err) {
    $('#bookings').innerHTML = errorState(err);
  }
}

load();
