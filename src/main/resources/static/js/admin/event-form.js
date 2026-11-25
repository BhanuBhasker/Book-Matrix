import { $, html, api, param, handleForm, toLocalInput, toast, errorState } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('events');
const id = Number(param('id')) || null;
const form = $('#event-form');

/** Shows the row/seat inputs for reserved seating and keeps capacity = rows x seats. */
function syncSeating() {
  const reserved = form.elements.seatingType.value === 'RESERVED';
  form.querySelectorAll('[data-layout]').forEach(el => { el.hidden = !reserved; });
  const cap = form.elements.capacity;
  cap.readOnly = reserved;
  if (reserved) {
    const rows = Number(form.elements.seatRows.value) || 0;
    const per = Number(form.elements.seatsPerRow.value) || 0;
    cap.value = rows * per || '';
    $('#layout-hint').textContent = rows && per
      ? `Seat map: rows A–${String.fromCharCode(64 + Math.min(rows, 26))}, seats 1–${per} → ${rows * per} seats.` : '';
  }
}
['seatingType', 'seatRows', 'seatsPerRow'].forEach(n => form.elements[n].addEventListener('input', syncSeating));

async function init() {
  const { data: categories } = await api('/api/categories');
  form.elements.categoryId.innerHTML = html`<option value="">Choose…</option>${categories.map(c => html`<option value="${c.id}">${c.name}</option>`)}`;
  if (!id) {
    const start = new Date(Date.now() + 14 * 864e5);
    start.setHours(19, 0, 0, 0);
    const end = new Date(start.getTime() + 3 * 36e5);
    form.elements.startsAt.value = localValue(start);
    form.elements.endsAt.value = localValue(end);
    form.elements.startsAt.min = localValue(new Date());
    syncSeating();
    return;
  }
  const { data: e } = await api(`/api/admin/events/${id}`);
  document.title = `Edit ${e.title} — BookMatrix Admin`;
  $('#form-title').textContent = 'Edit event';
  $('#save-btn').textContent = 'Save changes';
  $('#cancel-link').href = `/admin/event.html?id=${id}`;
  form.elements.title.value = e.title;
  form.elements.description.value = e.description;
  form.elements.categoryId.value = e.categoryId;
  form.elements.imageUrl.value = e.imageUrl || '';
  form.elements.venueName.value = e.venueName;
  form.elements.city.value = e.city;
  form.elements.venueAddress.value = e.venueAddress || '';
  form.elements.startsAt.value = toLocalInput(e.startsAt);
  form.elements.endsAt.value = toLocalInput(e.endsAt);
  form.elements.ticketPrice.value = e.ticketPrice;
  form.elements.capacity.value = e.capacity;
  form.elements.capacity.min = Math.max(1, e.ticketsSold);
  form.elements.featured.checked = e.featured;
  form.elements.seatingType.value = e.seatingType || 'GENERAL';
  if (e.seatingType === 'RESERVED') {
    form.elements.seatRows.value = e.seatRows;
    form.elements.seatsPerRow.value = e.seatsPerRow;
  }
  syncSeating();
  if (e.ticketsSold > 0) {
    ['seatingType', 'seatRows', 'seatsPerRow'].forEach(n => { form.elements[n].disabled = true; });
    $('#layout-hint').textContent = 'The seating layout is locked because tickets have been sold or held.';
    $('#layout-hint').hidden = false;
  }
  $('#capacity-hint').textContent = `${e.ticketsSold} already sold — capacity cannot go below this.`;
  if (['CANCELLED', 'COMPLETED'].includes(e.displayStatus)) {
    form.insertAdjacentHTML('afterbegin', String(html`<div class="alert alert-warn">This event is ${e.displayStatusLabel.toLowerCase()} and can no longer be edited.</div>`));
    [...form.elements].forEach(el => { el.disabled = true; });
  }
}

function localValue(d) {
  const pad = x => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

handleForm(form, async values => {
  const body = {
    title: values.title, description: values.description,
    categoryId: values.categoryId ? Number(values.categoryId) : null,
    venueName: values.venueName, city: values.city, venueAddress: values.venueAddress,
    startsAt: values.startsAt, endsAt: values.endsAt,
    ticketPrice: values.ticketPrice === '' ? null : Number(values.ticketPrice),
    capacity: values.capacity === '' ? null : Number(values.capacity),
    imageUrl: values.imageUrl, featured: form.elements.featured.checked,
    seatingType: form.elements.seatingType.value,
    seatRows: form.elements.seatRows.value === '' ? null : Number(form.elements.seatRows.value),
    seatsPerRow: form.elements.seatsPerRow.value === '' ? null : Number(form.elements.seatsPerRow.value),
  };
  if (body.seatingType !== 'RESERVED') { body.seatRows = null; body.seatsPerRow = null; }
  const res = id
    ? await api(`/api/admin/events/${id}`, { method: 'PUT', body })
    : await api('/api/admin/events', { method: 'POST', body });
  toast(res.message);
  location.href = `/admin/event.html?id=${res.data.id}&saved=1`;
});

init().catch(err => {
  form.outerHTML = String(errorState(err));
  document.querySelector('[data-retry]')?.addEventListener('click', () => location.reload());
});
