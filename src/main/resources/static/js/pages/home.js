import { $, html, api, renderLayout, eventRow, poster, wireImages, formatDate, formatTime, formatMoney, formatPrice,
  availabilityText, emptyState, errorState, skeletonRows, toast, param, plural } from '../core.js?v=2026112505';

const user = await renderLayout({ active: 'home' });
if (param('loggedout')) toast('You have been logged out.', 'info');
$('#cta').hidden = !!user;

async function load() {
  $('#upcoming').innerHTML = skeletonRows(4);
  try {
    const { data } = await api('/api/home');
    $('#home-city').innerHTML = html`<option value="">Any city</option>${data.cities.map(c => html`<option>${c}</option>`)}`;
    $('#hero-stats').innerHTML = html`
      <div><strong>${data.upcomingCount}</strong>upcoming ${data.upcomingCount === 1 ? 'event' : 'events'}</div>
      <div><strong>${data.cities.length}</strong>${data.cities.length === 1 ? 'city' : 'cities'}</div>`;
    $('#home-categories').innerHTML = html`${data.categories.map(c => html`
      <a class="chip" href="/events.html?category=${c.id}">${c.name}${c.upcomingEvents ? html` <small>${c.upcomingEvents}</small>` : ''}</a>`)}`;

    if (data.featured.length) {
      const [lead, ...rest] = data.featured;
      $('#featured').innerHTML = html`
        ${featureCard(lead)}
        ${rest.length ? html`<div class="featured-side">${rest.map(featureCard)}</div>` : ''}`;
      $('#featured-section').hidden = false;
    }
    $('#upcoming').innerHTML = data.upcoming.length
      ? html`<div class="event-list">${data.upcoming.map(eventRow)}</div>`
      : emptyState('No upcoming events yet',
          'New events are added regularly. Check back soon' + (user?.role === 'ADMIN' ? ' — or add one from the admin area.' : '.'),
          user?.role === 'ADMIN' ? html`<a class="btn" href="/admin/event-form.html">Add an event</a>` : '');
    wireImages();
  } catch (err) {
    $('#upcoming').innerHTML = errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function featureCard(e) {
  return html`
    <a class="feature" href="/event.html?id=${e.id}&v=2026112505">
      ${poster(e)}
      <div class="feature-body">
        <span class="eyebrow" style="margin:0">${e.categoryName} · ${e.city}</span>
        <h3>${e.title}</h3>
        <span class="muted small">${formatDate(e.startsAt)} · ${formatTime(e.startsAt)} · ${e.venueName}</span>
        <span class="small"><strong>${formatPrice(e.ticketPrice)}</strong> · ${availabilityText(e)}</span>
      </div>
    </a>`;
}

load();
