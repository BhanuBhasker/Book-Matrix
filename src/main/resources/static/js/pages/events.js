import { $, $$, html, api, qs, renderLayout, eventRow, emptyState, errorState, skeletonRows,
  renderPagination, debounce, wireImages, plural, formatDate, formatMoney } from '../core.js?v=2026112505';

await renderLayout({ active: 'events' });
const form = $('#filters');
const FIELDS = ['q', 'category', 'city', 'status', 'from', 'to', 'minPrice', 'maxPrice', 'sort'];
const STATUS_LABELS = { AVAILABLE: 'Tickets available', SOLD_OUT: 'Sold out', LIVE: 'Happening now',
  COMPLETED: 'Completed', CANCELLED: 'Cancelled', ALL: 'All statuses' };
const SORT_LABELS = { DATE_DESC: 'Latest first', PRICE_ASC: 'Cheapest first', PRICE_DESC: 'Priciest first', POPULAR: 'Most booked' };
let page = 1;
let requestId = 0;
let lastKey = null;    // skip identical searches (e.g. a 'change' fired when an input loses focus)

// filters live in the URL so results can be bookmarked / shared and the back button works
function readUrl() {
  const p = new URLSearchParams(location.search);
  FIELDS.forEach(name => { if (form.elements[name]) form.elements[name].value = p.get(name) || form.elements[name].value; });
  page = Math.max(1, Number(p.get('page')) || 1);
}

function currentParams() {
  const params = {};
  FIELDS.forEach(name => { params[name] = form.elements[name].value.trim(); });
  if (params.status === 'UPCOMING') delete params.status;
  if (params.sort === 'DATE_ASC') delete params.sort;
  return params;
}

// ------------------------------------------------------------- quick date shortcuts
const pad = n => String(n).padStart(2, '0');
const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

function presetRange(key) {
  const today = new Date();
  switch (key) {
    case 'today': return [iso(today), iso(today)];
    case 'weekend': {
      const day = today.getDay();                       // 0 = Sunday, 6 = Saturday
      const saturday = day === 0 ? addDays(today, -1) : addDays(today, 6 - day);
      const from = day === 0 || day === 6 ? today : saturday;
      return [iso(from), iso(addDays(saturday, 1))];
    }
    case 'week': return [iso(today), iso(addDays(today, 6))];
    case 'month': return [iso(today), iso(new Date(today.getFullYear(), today.getMonth() + 1, 0))];
    default: return ['', ''];
  }
}

function renderQuickDates() {
  const from = form.elements.from.value;
  const to = form.elements.to.value;
  const presets = [['', 'Any time'], ['today', 'Today'], ['weekend', 'This weekend'], ['week', 'Next 7 days'], ['month', 'This month']];
  $('#quick-dates').innerHTML = html`${presets.map(([key, label]) => {
    const [f, t] = presetRange(key);
    const active = f === from && t === to;
    return html`<button type="button" class="chip ${active ? 'active' : ''}" data-preset="${key}" aria-pressed="${active}">${label}</button>`;
  })}`;
  $$('[data-preset]').forEach(b => b.addEventListener('click', () => {
    const [f, t] = presetRange(b.dataset.preset);
    form.elements.from.value = f;
    form.elements.to.value = t;
    onChange();
  }));
}

// ------------------------------------------------------------- "what am I looking at" chips
function renderActiveFilters() {
  const f = form.elements;
  const chips = [];
  if (f.q.value.trim()) chips.push(['q', `“${f.q.value.trim()}”`]);
  if (f.category.value) chips.push(['category', f.category.selectedOptions[0]?.text || 'Category']);
  if (f.city.value) chips.push(['city', f.city.value]);
  if (f.from.value || f.to.value) {
    const label = f.from.value && f.to.value
      ? (f.from.value === f.to.value ? formatDate(f.from.value) : `${formatDate(f.from.value, { day: 'numeric', month: 'short' })} – ${formatDate(f.to.value, { day: 'numeric', month: 'short' })}`)
      : f.from.value ? `From ${formatDate(f.from.value)}` : `Until ${formatDate(f.to.value)}`;
    chips.push(['dates', label]);
  }
  if (f.minPrice.value || f.maxPrice.value) {
    const label = f.minPrice.value && f.maxPrice.value ? `${formatMoney(f.minPrice.value)} – ${formatMoney(f.maxPrice.value)}`
      : f.maxPrice.value ? `Up to ${formatMoney(f.maxPrice.value)}` : `From ${formatMoney(f.minPrice.value)}`;
    chips.push(['price', label]);
  }
  if (STATUS_LABELS[f.status.value]) chips.push(['status', STATUS_LABELS[f.status.value]]);
  if (SORT_LABELS[f.sort.value]) chips.push(['sort', SORT_LABELS[f.sort.value]]);

  $('#active-filters').innerHTML = chips.length ? html`
    ${chips.map(([key, label]) => html`<button type="button" class="filter-chip" data-remove="${key}" aria-label="Remove filter ${label}">${label}<span aria-hidden="true">×</span></button>`)}
    ${chips.length > 1 ? html`<button type="button" class="linklike small" data-clear-all>Clear all</button>` : ''}` : '';
  $$('[data-remove]').forEach(b => b.addEventListener('click', () => {
    const key = b.dataset.remove;
    if (key === 'dates') { f.from.value = ''; f.to.value = ''; }
    else if (key === 'price') { f.minPrice.value = ''; f.maxPrice.value = ''; }
    else if (key === 'status') f.status.value = 'UPCOMING';
    else if (key === 'sort') f.sort.value = 'DATE_ASC';
    else f[key].value = '';
    onChange();
  }));
  $('[data-clear-all]')?.addEventListener('click', () => form.reset());
}

// ------------------------------------------------------------- search
async function search(pushState = true, force = false) {
  const params = currentParams();
  const key = JSON.stringify({ ...params, page });
  if (key === lastKey && !force) return;
  lastKey = key;
  renderQuickDates();
  renderActiveFilters();
  if (pushState) history.replaceState(null, '', `/events.html${qs({ ...params, page: page > 1 ? page : '' })}`);
  const mine = ++requestId;      // ignore responses that arrive after a newer search started
  $('#results').innerHTML = skeletonRows(5);
  $('#result-count').textContent = 'Searching…';
  try {
    const { data } = await api(`/api/events${qs({ ...params, page, size: 10 })}`);
    if (mine !== requestId) return;
    $('#result-count').textContent = plural(data.totalItems, 'event') + (data.totalItems ? ' found' : '');
    const filtered = $('#active-filters').children.length > 0;
    $('#results').innerHTML = data.items.length
      ? html`<div class="event-list">${data.items.map(eventRow)}</div>`
      : emptyState(filtered ? 'Nothing matches these filters' : 'No events yet',
          filtered ? 'Try removing a filter above, choosing “Any time”, or searching for something broader.'
                   : 'New events are added regularly — check back soon.',
          filtered ? html`<button class="btn btn-secondary" type="button" data-clear>Clear all filters</button>` : '');
    $('[data-clear]')?.addEventListener('click', () => { form.reset(); });
    renderPagination($('#pagination'), data, p => { page = p; search(); window.scrollTo({ top: 0, behavior: 'smooth' }); });
    wireImages();
  } catch (err) {
    if (mine !== requestId) return;
    $('#result-count').textContent = '';
    $('#pagination').innerHTML = '';
    $('#results').innerHTML = err.status === 400
      ? emptyState('Check your filters', err.message)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', () => search(false, true));
  }
}

async function loadOptions() {
  try {
    const [cats, cities] = await Promise.all([api('/api/categories'), api('/api/cities')]);
    const p = new URLSearchParams(location.search);
    form.elements.category.innerHTML = html`<option value="">All categories</option>${cats.data.map(c => html`<option value="${c.id}">${c.name}</option>`)}`;
    form.elements.city.innerHTML = html`<option value="">Any city</option>${cities.data.map(c => html`<option>${c}</option>`)}`;
    form.elements.category.value = p.get('category') || '';
    form.elements.city.value = p.get('city') || '';
  } catch { /* options are optional; search still works */ }
}

const onChange = () => { page = 1; search(); };
form.addEventListener('change', onChange);
form.elements.q.addEventListener('input', debounce(onChange, 350));
form.addEventListener('submit', e => { e.preventDefault(); onChange(); });
form.addEventListener('reset', () => setTimeout(onChange, 0));
$('.filters-toggle').addEventListener('click', e => {
  const open = form.classList.toggle('open');
  e.currentTarget.setAttribute('aria-expanded', String(open));
  e.currentTarget.textContent = open ? 'Hide filters' : 'Filters';
});

readUrl();
await loadOptions();
readUrl();
search(false);
