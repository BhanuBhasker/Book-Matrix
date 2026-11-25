import { $, html, api, qs, formatDate, formatTime, formatMoney, formatPrice, badge, errorState, renderPagination, debounce, skeletonRows } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('events');
const form = $('#toolbar');
let page = 1;
let requestId = 0;
let lastKey = null;

async function loadCategories() {
  try {
    const { data } = await api('/api/categories');
    form.elements.category.innerHTML = html`<option value="">All categories</option>${data.map(c => html`<option value="${c.id}">${c.name}</option>`)}`;
  } catch { /* filter stays usable without categories */ }
}

async function load(force = false) {
  const key = JSON.stringify({ ...Object.fromEntries(new FormData(form).entries()), page });
  if (key === lastKey && !force) return;
  lastKey = key;
  const mine = ++requestId;
  const params = Object.fromEntries(new FormData(form).entries());
  $('#table').innerHTML = skeletonRows(4);
  try {
    const { data } = await api(`/api/admin/events${qs({ ...params, page, size: 15, sort: 'DATE_DESC' })}`);
    if (mine !== requestId) return;
    $('#table').innerHTML = html`<div class="table-wrap"><table class="table">
      <thead><tr><th>Event</th><th>Date</th><th>Status</th><th class="num">Price</th><th class="num">Sold / capacity</th><th></th></tr></thead>
      <tbody>${data.items.length ? data.items.map(e => html`<tr>
        <td><a href="/admin/event.html?id=${e.id}">${e.title}</a>${e.featured ? html` <span class="badge badge-accent">Featured</span>` : ''}
          <div class="small muted">${e.categoryName} · ${e.venueName}, ${e.city}</div></td>
        <td class="small">${formatDate(e.startsAt)}<br><span class="muted">${formatTime(e.startsAt)}</span></td>
        <td>${badge(e.displayStatus, e.displayStatusLabel)}</td>
        <td class="num">${formatPrice(e.ticketPrice)}</td>
        <td class="num">${e.ticketsSold} / ${e.capacity}</td>
        <td class="actions"><a class="btn btn-secondary btn-sm" href="/admin/event-form.html?id=${e.id}">Edit</a></td></tr>`)
        : html`<tr><td colspan="6" class="table-empty">No events match these filters.</td></tr>`}</tbody></table></div>`;
    renderPagination($('#pagination'), data, p => { page = p; load(); });
  } catch (err) {
    $('#table').innerHTML = errorState(err);
    $('[data-retry]')?.addEventListener('click', () => load(true));
  }
}

form.addEventListener('change', () => { page = 1; load(); });
form.elements.q.addEventListener('input', debounce(() => { page = 1; load(); }, 350));
form.addEventListener('submit', e => { e.preventDefault(); page = 1; load(); });
await loadCategories();
load();
