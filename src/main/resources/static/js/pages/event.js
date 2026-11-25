import { $, html, api, param, renderLayout, poster, wireImages, formatDate, formatTime, formatMoney, formatPrice,
  formatDateTime, badge, errorState, emptyState, toast, withBusy, newRequestKey, plural, ApiError,
  relativeDay, downloadCalendarFile, directionsUrl, copyText } from '../core.js?v=2026112505';

const user = await renderLayout({ active: 'events' });
const root = $('#event-root');
const id = Number(param('id'));
let event = null;
let quantity = 1;
let requestKey = newRequestKey();   // one key per booking attempt: retries cannot double-book

async function load() {
  if (!Number.isInteger(id) || id < 1) {
    root.innerHTML = emptyState('Event not found', 'This link does not point to a valid event.', html`<a class="btn" href="/events.html">Browse events</a>`);
    return;
  }
  try {
    event = (await api(`/api/events/${id}`)).data;
    document.title = `${event.title} — BookMatrix`;
    render();
  } catch (err) {
    root.innerHTML = err.status === 404
      ? emptyState('Event not found', 'It may have been removed. Have a look at what else is on.', html`<a class="btn" href="/events.html">Browse events</a>`)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function render() {
  const e = event;
  const pct = Math.round(100 * e.ticketsSold / e.capacity);
  root.innerHTML = html`
    <nav class="small muted" style="padding-top:20px" aria-label="Breadcrumb"><a href="/events.html">Events</a> / ${e.categoryName}</nav>
    <div class="event-hero ${e.seatingType === 'RESERVED' && e.bookable && user ? 'seated' : ''}">
      <article>
        ${poster(e)}
        <div class="row" style="margin-top:20px">${badge(e.displayStatus, e.displayStatusLabel)}<span class="muted small">${e.categoryName}</span></div>
        <h1 style="margin-top:8px">${e.title}</h1>
        <dl class="facts">
          <div><dt>Date</dt><dd>${formatDate(e.startsAt)}${relativeDay(e.startsAt) && e.displayStatus !== 'COMPLETED' ? html` <span class="soon">${relativeDay(e.startsAt)}</span>` : ''}</dd></div>
          <div><dt>Time</dt><dd>${formatTime(e.startsAt)} – ${formatTime(e.endsAt)}</dd></div>
          <div><dt>Venue</dt><dd>${e.venueName}</dd></div>
          <div><dt>City</dt><dd>${e.city}</dd></div>
        </dl>
        ${e.venueAddress ? html`<p class="muted small">${e.venueAddress}</p>` : ''}
        <div class="action-row">
          ${e.displayStatus !== 'COMPLETED' && e.displayStatus !== 'CANCELLED' ? html`<button type="button" class="btn btn-secondary btn-sm" data-calendar>Add to calendar</button>` : ''}
          <a class="btn btn-secondary btn-sm" href="${directionsUrl(e.venueName, e.venueAddress, e.city)}" target="_blank" rel="noopener">Directions ↗</a>
          <button type="button" class="btn btn-secondary btn-sm" data-share>Copy link</button>
        </div>
        <h2 style="font-size:20px;margin-top:24px">About this event</h2>
        <p class="prose">${e.description}</p>
      </article>
      <aside class="panel book-panel" aria-labelledby="book-title" id="book-panel">
        <h2 id="book-title" class="visually-hidden">Book tickets</h2>
        <div class="price-big">${formatPrice(e.ticketPrice)}</div>
        <div class="muted small">per ticket</div>
        <div class="meter ${pct >= 100 ? 'full' : pct >= 85 ? 'warn' : ''}" role="progressbar" aria-label="Tickets sold" aria-valuemin="0" aria-valuemax="${e.capacity}" aria-valuenow="${e.ticketsSold}"><span style="width:${Math.min(100, pct)}%"></span></div>
        <div class="small spread"><span>${plural(e.availableTickets, 'ticket')} left</span><span class="muted">of ${e.capacity}</span></div>
        <div id="booking-area" style="margin-top:18px"></div>
      </aside>
    </div>
    ${e.bookable ? html`<div class="mobile-book-bar" id="mobile-book-bar">
        <div><strong>${formatPrice(e.ticketPrice)}</strong><span class="small muted"> · ${plural(e.availableTickets, 'ticket')} left</span></div>
        <button type="button" class="btn btn-accent" data-jump>Book tickets</button>
      </div>` : ''}`;
  wireImages(root);
  renderBookingArea();
  wireActions();
}

/** Calendar / share buttons and the mobile "Book tickets" bar. */
function wireActions() {
  const e = event;
  $('[data-calendar]')?.addEventListener('click', () => downloadCalendarFile(e));
  $('[data-share]')?.addEventListener('click', async () => {
    toast(await copyText(location.href) ? 'Link copied — paste it anywhere to share.' : 'Copy this link from the address bar.', 'info');
  });
  const bar = $('#mobile-book-bar');
  if (!bar) return;
  $('[data-jump]', bar).addEventListener('click', () => {
    $('#book-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
    setTimeout(() => ($('#qty') || $('#book-panel a.btn'))?.focus({ preventScroll: true }), 400);
  });
  // hide the bar while the real booking panel is on screen
  new IntersectionObserver(([entry]) => bar.classList.toggle('hidden', entry.isIntersecting), { threshold: 0 })
    .observe($('#book-panel'));
}

function renderBookingArea(message) {
  const area = $('#booking-area');
  const e = event;
  if (!e.bookable) {
    area.innerHTML = html`<div class="alert alert-info" role="status">${e.bookingBlockReason}</div>
      <a class="btn btn-secondary btn-block" href="/events.html">Find other events</a>`;
    return;
  }
  if (!user) {
    area.innerHTML = html`<p class="small muted">Log in to book tickets for this event.</p>
      <a class="btn btn-block" href="/login.html?next=${encodeURIComponent(location.pathname + location.search)}">Log in to book</a>
      <p class="small muted" style="margin-top:10px">No account? <a href="/register.html">Create one</a></p>`;
    return;
  }
  if (e.seatingType === 'RESERVED') {
    renderSeatPicker(message);
    return;
  }
  quantity = Math.min(quantity, e.maxPerBooking);
  area.innerHTML = html`
    ${message ? html`<div class="alert alert-error" role="alert">${message}</div>` : ''}
    ${steps(1)}
    <label class="label" for="qty">How many tickets?</label>
    <div class="row" style="margin-top:6px">
      <div class="stepper">
        <button type="button" data-step="-1" aria-label="One fewer ticket">−</button>
        <input id="qty" type="number" inputmode="numeric" min="1" max="${e.maxPerBooking}" value="${quantity}" aria-describedby="qty-hint">
        <button type="button" data-step="1" aria-label="One more ticket">+</button>
      </div>
      <span class="small muted" id="qty-hint">max ${e.maxPerBooking} per booking</span>
    </div>
    <table class="summary" aria-label="Price summary">
      <tr><td>${formatPrice(e.ticketPrice)} × <span data-q>${quantity}</span></td><td data-sub></td></tr>
      <tr class="total"><td>Total</td><td data-total></td></tr>
    </table>
    <button class="btn btn-accent btn-block btn-lg" type="button" data-review>Continue</button>`;
  const input = $('#qty');
  const sync = () => {
    $('[data-q]').textContent = quantity;
    const total = formatMoney(Number(e.ticketPrice) * quantity);
    $('[data-sub]').textContent = total;
    $('[data-total]').textContent = total;
    $('[data-step="-1"]').disabled = quantity <= 1;
    $('[data-step="1"]').disabled = quantity >= e.maxPerBooking;
  };
  area.querySelectorAll('[data-step]').forEach(b => b.addEventListener('click', () => {
    quantity = Math.max(1, Math.min(e.maxPerBooking, quantity + Number(b.dataset.step)));
    input.value = quantity;
    sync();
  }));
  input.addEventListener('change', () => {
    const n = Math.floor(Number(input.value));
    quantity = Number.isFinite(n) ? Math.max(1, Math.min(e.maxPerBooking, n)) : 1;
    input.value = quantity;
    sync();
  });
  $('[data-review]').addEventListener('click', review);
  sync();
}

/** Step indicator. Paid events go Tickets/Seats → Review → Payment; free ones end at Done. */
function steps(current) {
  const first = event.seatingType === 'RESERVED' ? 'Seats' : 'Tickets';
  const last = Number(event.ticketPrice) > 0 ? 'Payment' : 'Done';
  const names = [first, 'Review', last];
  return html`<ol class="steps" aria-label="Booking steps">${names.map((n, i) => html`
    <li class="${i + 1 < current ? 'done' : ''}" ${i + 1 === current ? html`aria-current="step"` : ''}>${n}</li>`)}</ol>`;
}

// ------------------------------------------------------------------ seat selection
let selectedSeats = [];

async function renderSeatPicker(message) {
  const e = event;
  const area = $('#booking-area');
  area.innerHTML = html`${steps(1)}<div class="skeleton tall"></div>`;
  let map;
  try {
    map = (await api(`/api/events/${id}/seats`)).data;
  } catch (err) {
    area.innerHTML = errorState(err);
    $('[data-retry]', area)?.addEventListener('click', () => renderSeatPicker(message));
    return;
  }
  const taken = new Set(map.taken);
  selectedSeats = selectedSeats.filter(s => !taken.has(s));
  const numbers = Array.from({ length: map.seatsPerRow }, (_, i) => i + 1);
  const aisleAfter = map.seatsPerRow >= 10 ? Math.ceil(map.seatsPerRow / 2) : 0;   // centre aisle on wider rows
  const free = map.available ?? (map.rowLabels.length * map.seatsPerRow - taken.size);
  area.innerHTML = html`
    ${message ? html`<div class="alert alert-error" role="alert">${message}</div>` : ''}
    ${steps(1)}
    <div class="spread">
      <p class="label" id="seat-label" style="margin:0">Choose your seats</p>
      <span class="small muted">${plural(free, 'seat')} free · up to ${e.maxPerBooking}</span>
    </div>
    <div class="seatmap-scroll">
      <div class="seatmap" role="group" aria-labelledby="seat-label" style="--seat:${map.seatsPerRow > 16 ? 20 : map.seatsPerRow > 12 ? 24 : 28}px">
        <div class="stage" aria-hidden="true">Stage</div>
        ${map.rowLabels.map(r => html`
          <div class="seat-row">
            <span class="row-label" aria-hidden="true">${r}</span>
            ${numbers.map(n => {
              const label = `${r}${n}`;
              const isTaken = taken.has(label);
              const isSelected = selectedSeats.includes(label);
              return html`<button type="button" class="seat ${isTaken ? 'taken' : ''} ${isSelected ? 'selected' : ''}"
                data-seat="${label}" aria-pressed="${isSelected}" ${isTaken ? 'disabled' : ''}
                aria-label="Row ${r}, seat ${n}${isTaken ? ', taken' : ''}" title="${label}${isTaken ? ' (taken)' : ''}">${n}</button>${n === aisleAfter ? html`<span class="aisle" aria-hidden="true"></span>` : ''}`;
            })}
            <span class="row-label" aria-hidden="true">${r}</span>
          </div>`)}
      </div>
    </div>
    <div class="seat-legend" aria-hidden="true">
      <span><i class="seat"></i>Available</span><span><i class="seat selected"></i>Selected</span><span><i class="seat taken"></i>Booked</span>
    </div>
    <div class="seat-picked" data-seat-summary aria-live="polite"></div>
    <table class="summary" aria-label="Price summary">
      <tr><td>${formatPrice(e.ticketPrice)} × <span data-q>0</span></td><td data-sub></td></tr>
      <tr class="total"><td>Total</td><td data-total></td></tr>
    </table>
    <button class="btn btn-accent btn-block btn-lg" type="button" data-review>Continue</button>`;
  const toggleSeat = label => {
    if (selectedSeats.includes(label)) {
      selectedSeats = selectedSeats.filter(s => s !== label);
    } else if (selectedSeats.length >= e.maxPerBooking) {
      toast(`You can choose up to ${e.maxPerBooking} seats per booking.`, 'info');
      return;
    } else {
      selectedSeats.push(label);
    }
    const btn = area.querySelector(`[data-seat="${label}"]`);
    btn.classList.toggle('selected', selectedSeats.includes(label));
    btn.setAttribute('aria-pressed', String(selectedSeats.includes(label)));
    sync();
  };
  const sync = () => {
    quantity = selectedSeats.length;
    $('[data-q]').textContent = quantity;
    const total = formatMoney(Number(e.ticketPrice) * quantity);
    $('[data-sub]').textContent = total;
    $('[data-total]').textContent = total;
    const box = $('[data-seat-summary]');
    box.innerHTML = quantity
      ? html`${sortSeats(selectedSeats).map(s => html`<span class="seat-pill">${s}<button type="button" data-unpick="${s}" aria-label="Remove seat ${s}">×</button></span>`)}`
      : 'Tap a seat on the map to select it.';
    box.querySelectorAll('[data-unpick]').forEach(b => b.addEventListener('click', () => toggleSeat(b.dataset.unpick)));
    const btn = $('[data-review]');
    btn.disabled = quantity === 0;
    btn.textContent = quantity ? `Continue with ${plural(quantity, 'seat')}` : 'Select seats to continue';
  };
  area.querySelectorAll('[data-seat]').forEach(btn => btn.addEventListener('click', () => toggleSeat(btn.dataset.seat)));
  const scroller = $('.seatmap-scroll', area);                 // start wide maps centred on the stage
  scroller.scrollLeft = (scroller.scrollWidth - scroller.clientWidth) / 2;
  $('[data-review]').addEventListener('click', review);
  sync();
}

function sortSeats(list) {
  return [...list].sort((a, b) => a[0].localeCompare(b[0]) || Number(a.slice(1)) - Number(b.slice(1)));
}

// ------------------------------------------------------------------ review + create the booking
function review() {
  const e = event;
  const area = $('#booking-area');
  const seated = e.seatingType === 'RESERVED';
  const paid = Number(e.ticketPrice) > 0;
  area.innerHTML = html`
    ${steps(2)}
    <p class="eyebrow">Review your booking</p>
    <p style="margin:0 0 4px"><strong>${e.title}</strong></p>
    <p class="small muted">${formatDateTime(e.startsAt)} · ${e.venueName}, ${e.city}</p>
    <table class="summary">
      <tr><td>Tickets</td><td>${quantity}</td></tr>
      ${seated ? html`<tr><td>Seats</td><td><div class="order-seats">${sortSeats(selectedSeats).map(x => html`<span>${x}</span>`)}</div></td></tr>` : ''}
      <tr><td>Price per ticket</td><td>${formatPrice(e.ticketPrice)}</td></tr>
      <tr class="total"><td>${paid ? 'Total to pay' : 'Total'}</td><td>${formatMoney(Number(e.ticketPrice) * quantity)}</td></tr>
    </table>
    <p class="small muted">${paid
      ? `Your ${seated ? 'seats' : 'tickets'} will be held for 10 minutes while you pay. You can cancel up to 2 hours before the event for a full refund.`
      : 'This event is free — your tickets are confirmed straight away.'}</p>
    <div class="row">
      <button class="btn btn-secondary btn-lg" type="button" data-back>Back</button>
      <button class="btn btn-accent btn-lg" type="button" data-confirm style="flex:1">${paid ? `Proceed to pay ${formatMoney(Number(e.ticketPrice) * quantity)}` : 'Confirm booking'}</button>
    </div>`;
  $('[data-back]').addEventListener('click', () => renderBookingArea());
  $('[data-confirm]').addEventListener('click', ev => confirmBooking(ev.currentTarget));
  $('[data-confirm]').focus();
}

async function confirmBooking(button) {
  await withBusy(button, async () => {
    $('[data-back]').disabled = true;
    try {
      const body = event.seatingType === 'RESERVED'
        ? { eventId: event.id, seats: sortSeats(selectedSeats), requestKey }
        : { eventId: event.id, quantity, requestKey };
      const res = await api('/api/bookings', { method: 'POST', body });
      const b = res.data.booking;
      requestKey = newRequestKey();
      if (b.status === 'PENDING_PAYMENT') {
        location.href = `/checkout.html?ref=${b.reference}`;      // held — go and pay
        return;
      }
      selectedSeats = [];
      $('#booking-area').innerHTML = html`
        <div class="success-box" role="status">
          ${steps(4)}
          <p class="eyebrow" style="color:var(--ok)">Booking confirmed</p>
          <p style="margin:0">${res.message}</p>
          <div class="ref" aria-label="Booking reference">${b.reference}</div>
          <table class="summary">
            <tr><td>Tickets</td><td>${b.quantity}</td></tr>
            ${b.seatLabels ? html`<tr><td>Seats</td><td>${b.seatLabels}</td></tr>` : ''}
            <tr class="total"><td>Paid</td><td>${formatMoney(b.totalAmount)}</td></tr>
          </table>
          <a class="btn btn-block" href="/booking.html?ref=${b.reference}">View booking</a>
          <button type="button" class="btn btn-secondary btn-block" style="margin-top:8px" data-calendar-booked>Add to calendar</button>
          <a class="btn btn-secondary btn-block" style="margin-top:8px" href="/bookings.html">All my bookings</a>
        </div>`;
      $('[data-calendar-booked]').addEventListener('click', () => downloadCalendarFile({ ...event, reference: b.reference }));
      $('#mobile-book-bar')?.remove();
      toast('Booking confirmed: ' + b.reference);
      refreshAvailability();
    } catch (err) {
      if (err instanceof ApiError && ['INSUFFICIENT_TICKETS', 'BOOKING_NOT_ALLOWED', 'SEATS_TAKEN', 'VALIDATION_FAILED'].includes(err.code)) {
        requestKey = newRequestKey();
        await refreshAvailability();
        renderBookingArea(err.message);
      } else {
        $('[data-back]').disabled = false;
        toast(err.message, 'error');
        throw err;
      }
    }
  }, 'Please wait…').catch(() => {});
}

async function refreshAvailability() {
  try {
    const fresh = (await api(`/api/events/${id}`)).data;
    event = fresh;
    const panel = $('#book-panel');
    const pct = Math.round(100 * fresh.ticketsSold / fresh.capacity);
    panel.querySelector('.meter span').style.width = `${Math.min(100, pct)}%`;
    panel.querySelector('.meter').setAttribute('aria-valuenow', fresh.ticketsSold);
    panel.querySelector('.spread span').textContent = plural(fresh.availableTickets, 'ticket') + ' left';
  } catch { /* the booking itself already succeeded or failed with a message */ }
}

load();
