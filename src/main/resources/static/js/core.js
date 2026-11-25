// =====================================================================
// BookMatrix frontend core: API client, session, safe HTML templating,
// layout (header/footer), toasts, dialogs, formatters, shared renderers.
// Plain ES modules, no framework.
// =====================================================================

// ------------------------------------------------------------- safe HTML
class SafeHtml {
  constructor(value) { this.value = value; }
  toString() { return this.value; }
}

export function escapeHtml(text) {
  return String(text)
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function toHtml(value) {
  if (value === null || value === undefined || value === false) return '';
  if (value instanceof SafeHtml) return value.value;
  if (Array.isArray(value)) return value.map(toHtml).join('');
  return escapeHtml(value);
}

/** Tagged template: every ${value} is HTML-escaped unless it is itself html`...` output. Prevents XSS. */
export function html(strings, ...values) {
  let out = '';
  strings.forEach((s, i) => { out += s + (i < values.length ? toHtml(values[i]) : ''); });
  return new SafeHtml(out);
}

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

// ------------------------------------------------------------- API client
export class ApiError extends Error {
  constructor(status, code, message, fields = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields || {};
  }
}

let session = null;          // { user, csrfToken }

// True while the browser is leaving this page. Requests the browser cancels because of the
// navigation (Safari reports them as network errors) must not show errors or redirect to login.
let leaving = false;
addEventListener('pagehide', () => { leaving = true; });
addEventListener('pageshow', () => { leaving = false; });       // back/forward cache restores the page
const pause = ms => new Promise(r => setTimeout(r, ms));
let sessionPromise = null;

export async function loadSession(force = false) {
  if (session && !force) return session;
  if (!sessionPromise || force) {
    sessionPromise = rawFetch('/api/auth/session').then(res => {
      session = res.data;
      return session;
    }).finally(() => { sessionPromise = null; });
  }
  return sessionPromise;
}

export function currentUser() { return session?.user ?? null; }
export function setSession(data) { session = data; }

const UNSAFE = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

async function rawFetch(path, { method = 'GET', body, form, timeout = 15000, headers = {} } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const init = { method, headers: { Accept: 'application/json', ...headers }, credentials: 'same-origin', signal: controller.signal };
  if (form) {
    init.body = form;
  } else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(path, init);
  } catch (err) {
    clearTimeout(timer);
    await pause(150);                       // let a pending navigation fire 'pagehide' first
    if (leaving) return new Promise(() => {});   // page is going away: stay silent
    if (err.name === 'AbortError') {
      throw new ApiError(0, 'TIMEOUT', 'The server is taking too long to respond. Please try again.');
    }
    throw new ApiError(0, 'NETWORK', 'Cannot reach BookMatrix. Check your connection and try again.');
  }
  clearTimeout(timer);
  let json = null;
  try {
    json = await response.json();
  } catch {
    throw new ApiError(response.status, 'BAD_RESPONSE', response.ok
      ? 'The server sent an unexpected response.'
      : `The server returned an error (${response.status}). Please try again.`);
  }
  if (!response.ok || !json.success) {
    const e = json?.error || {};
    throw new ApiError(response.status, e.code || 'ERROR', e.message || 'Something went wrong.', e.fields);
  }
  return json;
}

/**
 * Calls the BookMatrix API. Adds the CSRF header to state-changing requests,
 * refreshes the token once if it expired, and sends the user to the login page
 * when the session has expired (unless allow401 is set).
 */
export async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const send = async () => {
    const headers = {};
    if (UNSAFE.has(method)) {
      const s = await loadSession();
      headers['X-CSRF-Token'] = s.csrfToken;
    }
    return rawFetch(path, { ...options, method, headers });
  };
  try {
    return await send();
  } catch (err) {
    if (err instanceof ApiError && err.code === 'CSRF_INVALID') {
      await loadSession(true);
      return send();
    }
    if (err instanceof ApiError && err.status === 401 && !options.allow401) {
      const next = encodeURIComponent(location.pathname + location.search);
      const reason = err.code === 'SESSION_EXPIRED' ? 'expired' : 'login';
      location.href = `/login.html?next=${next}&reason=${reason}`;
      return new Promise(() => {});   // stop the caller while the browser navigates
    }
    throw err;
  }
}

export function qs(params) {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, v);
  });
  const s = p.toString();
  return s ? `?${s}` : '';
}

export function param(name) {
  return new URLSearchParams(location.search).get(name);
}

// ------------------------------------------------------------- formatting
const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
/** Amounts of money (totals, revenue): always a currency value, ₹0.00 included. */
export function formatMoney(value) {
  const n = Number(value);
  return Number.isNaN(n) ? '—' : money.format(n);
}
/** Ticket prices: a price of 0 reads better as "Free". */
export function formatPrice(value) {
  return Number(value) === 0 ? 'Free' : formatMoney(value);
}
export function parseDate(value) { return value ? new Date(value) : null; }
export function formatDate(value, opts = { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }) {
  const d = parseDate(value);
  return d ? d.toLocaleDateString('en-IN', opts) : '—';
}
export function formatTime(value) {
  const d = parseDate(value);
  return d ? d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '';
}
export function formatDateTime(value) {
  return value ? `${formatDate(value)}, ${formatTime(value)}` : '—';
}
export function plural(n, word, pluralWord = word + 's') {
  return `${n} ${n === 1 ? word : pluralWord}`;
}
export function toLocalInput(value) {   // "2026-12-01T19:30:00" -> "2026-12-01T19:30" for <input type=datetime-local>
  return value ? String(value).slice(0, 16) : '';
}

// ------------------------------------------------------------- toasts
function toastHost() {
  let host = $('.toasts');
  if (!host) {
    host = document.createElement('div');
    host.className = 'toasts';
    host.setAttribute('role', 'status');
    host.setAttribute('aria-live', 'polite');
    document.body.appendChild(host);
  }
  return host;
}
export function toast(message, type = 'success', ms = 4200) {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = message;
  el.title = 'Click to dismiss';
  el.addEventListener('click', () => el.remove());
  toastHost().appendChild(el);
  setTimeout(() => el.remove(), type === 'error' ? ms + 3000 : ms);
}

// ------------------------------------------------------------- dialogs
/**
 * Accessible confirmation dialog (native <dialog>: focus trap, Esc to close).
 * Resolves to false when cancelled, or { reason } / true when confirmed.
 */
export function confirmDialog({ title, body, confirmText = 'Confirm', cancelText = 'Cancel', danger = false,
                                input = null, requireText = null }) {
  return new Promise(resolve => {
    const dialog = document.createElement('dialog');
    dialog.className = 'dialog';
    dialog.setAttribute('aria-labelledby', 'dlg-title');
    dialog.innerHTML = html`
      <form method="dialog">
        <div class="dialog-body">
          <h2 id="dlg-title">${title}</h2>
          <div>${body}</div>
          ${input ? html`<div class="field" style="margin-top:12px">
              <label for="dlg-input">${input.label}</label>
              <textarea id="dlg-input" class="input" maxlength="${input.max || 255}" style="min-height:70px" placeholder="${input.placeholder || ''}"></textarea>
            </div>` : ''}
          ${requireText ? html`<div class="field" style="margin-top:12px">
              <label for="dlg-confirm">Type <strong>${requireText}</strong> to confirm</label>
              <input id="dlg-confirm" class="input" autocomplete="off">
            </div>` : ''}
        </div>
        <div class="dialog-actions">
          <button type="button" class="btn btn-secondary" value="cancel" data-cancel>${cancelText}</button>
          <button type="submit" class="btn ${danger ? 'btn-danger solid' : ''}" value="ok" data-ok ${requireText ? 'disabled' : ''}>${confirmText}</button>
        </div>
      </form>`;
    document.body.appendChild(dialog);
    const ok = $('[data-ok]', dialog);
    if (requireText) {
      $('#dlg-confirm', dialog).addEventListener('input', e => { ok.disabled = e.target.value !== requireText; });
    }
    let result = false;
    $('[data-cancel]', dialog).addEventListener('click', () => dialog.close());
    dialog.querySelector('form').addEventListener('submit', () => {
      result = input ? { reason: $('#dlg-input', dialog).value.trim() } : true;
    });
    dialog.addEventListener('close', () => { dialog.remove(); resolve(result); });
    dialog.showModal();
    (input ? $('#dlg-input', dialog) : requireText ? $('#dlg-confirm', dialog) : $('[data-cancel]', dialog)).focus();
  });
}

// ------------------------------------------------------------- forms
export function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

export function clearErrors(form) {
  $$('.field.invalid', form).forEach(f => f.classList.remove('invalid'));
  $$('.field-error', form).forEach(e => { e.textContent = ''; });
  $$('[aria-invalid]', form).forEach(i => i.removeAttribute('aria-invalid'));
  const alert = $('.form-alert', form);
  if (alert) { alert.hidden = true; alert.textContent = ''; }
}

/** Shows server-side (or client-side) field errors next to the matching inputs. */
export function showErrors(form, error) {
  const fields = error.fields || {};
  let firstInvalid = null;
  Object.entries(fields).forEach(([name, message]) => {
    const input = form.elements[name];
    const field = input?.closest?.('.field');
    if (field) {
      field.classList.add('invalid');
      input.setAttribute('aria-invalid', 'true');
      let el = $('.field-error', field);
      if (!el) { el = document.createElement('div'); el.className = 'field-error'; field.appendChild(el); }
      el.textContent = message;
      firstInvalid ??= input;
    }
  });
  const alert = $('.form-alert', form);
  if (alert && (!Object.keys(fields).length || !firstInvalid)) {
    alert.textContent = error.message;
    alert.hidden = false;
  } else if (alert && Object.keys(fields).length > 1) {
    alert.textContent = error.message;
    alert.hidden = false;
  }
  firstInvalid?.focus();
}

/** Disables a button and shows a spinner while an async action runs (prevents double submits). */
export async function withBusy(button, task, busyText) {
  if (button.dataset.busy === '1') return undefined;
  button.dataset.busy = '1';
  const original = button.innerHTML;
  button.disabled = true;
  button.innerHTML = html`<span class="spinner" aria-hidden="true"></span>${busyText || button.textContent}`;
  try {
    return await task();
  } finally {
    button.dataset.busy = '';
    button.disabled = false;
    button.innerHTML = original;
  }
}

/** Wires a form: client-side required checks, then the async submit handler, with errors rendered inline. */
export function handleForm(form, onSubmit) {
  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);
    const missing = {};
    $$('[required]', form).forEach(input => {
      if (!String(input.value).trim()) missing[input.name] = 'This field is required.';
    });
    if (Object.keys(missing).length) {
      showErrors(form, { message: 'Please fill in the highlighted fields.', fields: missing });
      return;
    }
    const button = $('button[type="submit"]', form);
    await withBusy(button, async () => {
      try {
        await onSubmit(formData(form), form);
      } catch (err) {
        showErrors(form, err);
        if (!(err instanceof ApiError) || err.status >= 500 || err.status === 0) {
          toast(err.message, 'error');
        }
      }
    });
  });
}

// ------------------------------------------------------------- layout
function navLink(href, label, current) {
  return html`<a href="${href}" ${current ? html`aria-current="page"` : ''}>${label}</a>`;
}

export async function renderLayout({ active = '', requireLogin = false } = {}) {
  let s = { user: null };
  let sessionError = null;
  try {
    s = await loadSession();
  } catch (err) {
    sessionError = err;
    toast(err.message, 'error', 8000);
  }
  const user = s.user;
  if (requireLogin && !user && sessionError) {
    // We could not ask the server who is logged in. Do not assume "logged out":
    // offer a retry instead of throwing the user to the login page.
    const main = $('#main');
    if (main) {
      main.innerHTML = html`<div class="container" style="padding:48px 0">${errorState(sessionError)}</div>`;
      $('[data-retry]', main)?.addEventListener('click', () => location.reload());
    }
    return new Promise(() => {});
  }
  if (requireLogin && !user) {
    location.href = `/login.html?next=${encodeURIComponent(location.pathname + location.search)}`;
    return new Promise(() => {});
  }
  const header = $('#site-header');
  if (header) {
    header.className = 'site-header';
    header.innerHTML = html`
      <div class="container">
        <a class="brand" href="/" aria-label="BookMatrix home"><span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i><i></i></span>BookMatrix</a>
        <button class="nav-toggle" type="button" aria-expanded="false" aria-controls="main-nav">Menu</button>
        <nav class="nav" id="main-nav" aria-label="Main">
          ${navLink('/', 'Home', active === 'home')}
          ${navLink('/events.html', 'Events', active === 'events')}
          ${user ? html`
            ${navLink('/bookings.html', 'My bookings', active === 'bookings')}
            ${navLink('/profile.html', 'Profile', active === 'profile')}
            ${user.role === 'ADMIN' ? navLink('/admin/index.html', 'Admin', active === 'admin') : ''}
            <button type="button" class="linklike" data-logout>Log out</button>
            <span class="nav-user" title="${user.email}" data-initials="${initials(user.fullName)}">${user.fullName}</span>`
          : html`
            ${navLink('/login.html', 'Log in', active === 'login')}
            <a class="nav-cta" href="/register.html">Create account</a>`}
        </nav>
      </div>`;
    const toggle = $('.nav-toggle', header);
    toggle.addEventListener('click', () => {
      const nav = $('#main-nav');
      const open = nav.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
    });
    $('[data-logout]', header)?.addEventListener('click', logout);
  }
  const footer = $('#site-footer');
  if (footer) {
    footer.className = 'site-footer';
    footer.innerHTML = html`
      <div class="container">
        <span>© ${new Date().getFullYear()} BookMatrix · Event discovery and ticketing</span>
        <span><a href="/events.html">Browse events</a> · ${user ? html`<a href="/bookings.html">My bookings</a>` : html`<a href="/register.html">Create an account</a>`}</span>
      </div>`;
  }
  if (param('denied') === 'admin') toast('That area is for administrators only.', 'error');
  return user;
}

/** "Asha Rao Kumar" -> "AR" for the avatar circle in the header. */
function initials(name) {
  return String(name || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase();
}

export async function logout() {
  try {
    const res = await api('/api/auth/logout', { method: 'POST', allow401: true });
    setSession(res.data);
    location.href = '/?loggedout=1';
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ------------------------------------------------------------- shared renderers
const STATUS_BADGE = {
  UPCOMING: 'badge-ok', SOLD_OUT: 'badge-bad', LIVE: 'badge-accent', COMPLETED: 'badge-muted',
  CANCELLED: 'badge-bad', ARCHIVED: 'badge-muted', CONFIRMED: 'badge-ok', ACTIVE: 'badge-ok', DEACTIVATED: 'badge-bad',
  ADMIN: 'badge-accent', USER: 'badge-muted', PENDING_PAYMENT: 'badge-warn', EXPIRED: 'badge-muted',
  SUCCEEDED: 'badge-ok', FAILED: 'badge-bad', REFUND: 'badge-accent', CHARGE: 'badge-muted', RESERVED: 'badge-muted', GENERAL: 'badge-muted',
};

/** Human label + badge for a booking's status (also covers "attended" past bookings). */
export function bookingBadge(b) {
  if (b.status === 'PENDING_PAYMENT') return badge('PENDING_PAYMENT', b.payable ? 'Awaiting payment' : 'Expired');
  if (b.status === 'EXPIRED') return badge('EXPIRED', 'Expired');
  if (b.status === 'CANCELLED') return badge('CANCELLED', 'Cancelled');
  return b.eventPast ? badge('COMPLETED', 'Attended') : badge('CONFIRMED', 'Confirmed');
}
export function badge(status, label) {
  return html`<span class="badge ${STATUS_BADGE[status] || ''}">${label || String(status).replace('_', ' ').toLowerCase()}</span>`;
}

function tone(text) {
  let h = 0;
  for (const ch of String(text)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h % 6;
}

/** Event image, or a typographic poster when there is no image (or it fails to load). */
export function poster(event) {
  const fallback = html`<div class="poster-fallback tone-${tone(event.categoryName + event.title)}">
      <span class="pf-cat">${event.categoryName} · ${event.city}</span>
      <span class="pf-title">${event.title}</span>
    </div>`;
  const src = event.imageUrl || CATEGORY_POSTERS[event.categoryName];
  if (!src) return html`<div class="poster" aria-hidden="true">${fallback}</div>`;
  return html`<div class="poster">${fallback}<img src="${src}" alt="${event.title}" loading="lazy" data-poster-img></div>`;
}

/** Artwork used when an event has no image of its own. */
const CATEGORY_POSTERS = {
  Music: '/images/events/photos/cat-music.jpg', Comedy: '/images/events/photos/cat-comedy.jpg',
  Theatre: '/images/events/photos/cat-theatre.jpg', Conference: '/images/events/photos/cat-conference.jpg',
  Exhibition: '/images/events/photos/cat-exhibition.jpg', Sports: '/images/events/photos/cat-sports.jpg',
  Workshop: '/images/events/photos/cat-workshop.jpg', Festival: '/images/events/photos/cat-festival.jpg',
};

/** Removes broken images so the typographic poster underneath shows instead. */
export function wireImages(root = document) {
  $$('img[data-poster-img]', root).forEach(img => {
    const hide = () => img.remove();
    if (img.complete && img.naturalWidth === 0) hide();
    img.addEventListener('error', hide, { once: true });
  });
}

export function availabilityText(e) {
  if (e.displayStatus === 'SOLD_OUT') return 'Sold out';
  if (e.displayStatus !== 'UPCOMING') return e.displayStatusLabel;
  if (e.availableTickets <= Math.max(10, e.capacity * 0.1)) return `Only ${e.availableTickets} left`;
  return `${e.availableTickets} available`;
}

export function eventRow(e) {
  const d = parseDate(e.startsAt);
  const low = e.displayStatus === 'UPCOMING' && e.availableTickets <= Math.max(10, e.capacity * 0.1);
  return html`
    <a class="event-row" href="/event.html?id=${e.id}&v=2026112505">
      <div class="date-stub" aria-hidden="true">
        <div class="m">${d.toLocaleDateString('en-IN', { month: 'short' })}</div>
        <div class="d">${d.getDate()}</div>
        <div class="w">${d.toLocaleDateString('en-IN', { weekday: 'short' })}</div>
      </div>
      <div class="row-thumb" aria-hidden="true">${poster(e)}</div>
      <div>
        <p class="event-title">${e.title}${soonLabel(e)}</p>
        <div class="event-meta">
          <span>${formatDate(e.startsAt)} · ${formatTime(e.startsAt)}</span>
          <span>${e.venueName}, ${e.city}</span>
          <span>${e.categoryName}</span>
          ${e.seatingType === 'RESERVED' && e.displayStatus === 'UPCOMING' ? html`<span class="seat-tag">Pick your seats</span>` : ''}
        </div>
      </div>
      <div class="event-side">
        <span class="price">${formatPrice(e.ticketPrice)}</span>
        ${e.displayStatus === 'UPCOMING'
          ? html`<span class="avail ${low ? 'low' : ''}">${availabilityText(e)}</span>`
          : badge(e.displayStatus, e.displayStatusLabel)}
      </div>
    </a>`;
}

/** Small "Tonight" / "Tomorrow" / "In 3 days" pill for events in the next week. */
function soonLabel(e) {
  if (e.displayStatus !== 'UPCOMING' && e.displayStatus !== 'SOLD_OUT') return '';
  const label = relativeDay(e.startsAt);
  return label ? html` <span class="soon">${label}</span>` : '';
}

export function emptyState(title, text, action = '') {
  return html`<div class="empty"><h3>${title}</h3><p>${text}</p>${action}</div>`;
}

export function errorState(err, retryLabel = 'Try again') {
  return html`<div class="empty" role="alert"><h3>Something went wrong</h3><p>${err.message}</p>
    <button class="btn btn-secondary" type="button" data-retry>${retryLabel}</button></div>`;
}

export function skeletonRows(n = 4) {
  return html`<div aria-busy="true" aria-label="Loading">${Array.from({ length: n }, () => html`<div class="skeleton tall"></div>`)}</div>`;
}

/** Previous / next pagination. onPage(n) is called with the new page number. */
export function renderPagination(container, page, onPage) {
  if (!container) return;
  if (!page || page.totalPages <= 1) { container.innerHTML = ''; return; }
  container.innerHTML = html`
    <button class="btn btn-secondary btn-sm" type="button" data-p="${page.page - 1}" ${page.page <= 1 ? 'disabled' : ''}>← Previous</button>
    <span class="info">Page ${page.page} of ${page.totalPages} · ${page.totalItems} results</span>
    <button class="btn btn-secondary btn-sm" type="button" data-p="${page.page + 1}" ${page.page >= page.totalPages ? 'disabled' : ''}>Next →</button>`;
  $$('[data-p]', container).forEach(b => b.addEventListener('click', () => onPage(Number(b.dataset.p))));
}

/** Returns a debounced version of fn (used for search-as-you-type). */
export function debounce(fn, ms = 300) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

/** Random idempotency key for one booking attempt (same key = same booking on retry). */
export function newRequestKey() {
  return (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);
}

// ------------------------------------------------------------- friendly extras

/** "Today", "Tonight", "Tomorrow", "In 3 days" for dates within the next week, otherwise null. */
export function relativeDay(value, maxDays = 7) {
  const d = parseDate(value);
  if (!d) return null;
  const startOf = x => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const days = Math.round((startOf(d) - startOf(new Date())) / 864e5);
  if (days < 0 || days > maxDays) return null;
  if (days === 0) return d.getHours() >= 17 ? 'Tonight' : 'Today';
  if (days === 1) return 'Tomorrow';
  return `In ${days} days`;
}

/** Adds a Show/Hide button to every password field inside root. */
export function enhancePasswords(root = document) {
  $$('input[type="password"]', root).forEach(input => {
    if (input.dataset.enhanced) return;
    input.dataset.enhanced = '1';
    const wrap = document.createElement('div');
    wrap.className = 'password-wrap';
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'password-toggle';
    btn.textContent = 'Show';
    btn.setAttribute('aria-label', 'Show password');
    btn.setAttribute('aria-pressed', 'false');
    btn.addEventListener('click', () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      btn.textContent = show ? 'Hide' : 'Show';
      btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
      btn.setAttribute('aria-pressed', String(show));
      input.focus();
    });
    wrap.appendChild(btn);
  });
}

/** Live checklist under a "new password" field (and its confirm field, if given). */
export function passwordChecklist(passwordInput, confirmInput) {
  const list = document.createElement('ul');
  list.className = 'pw-checklist';
  const rules = [
    ['len', 'At least 8 characters', v => v.length >= 8],
    ['mix', 'At least one letter and one number', v => /[A-Za-z]/.test(v) && /\d/.test(v)],
  ];
  if (confirmInput) rules.push(['match', 'Both passwords match', v => v.length > 0 && v === confirmInput.value]);
  list.innerHTML = html`${rules.map(([key, text]) => html`<li data-rule="${key}">${text}</li>`)}`;
  const field = passwordInput.closest('.field');
  const hint = field.querySelector('.hint');
  if (hint) hint.hidden = true;
  (confirmInput ? confirmInput.closest('.field') : field).after(list);
  const update = () => rules.forEach(([key, , test]) => {
    $(`[data-rule="${key}"]`, list).classList.toggle('ok', test(passwordInput.value));
  });
  passwordInput.addEventListener('input', update);
  if (confirmInput) confirmInput.addEventListener('input', update);
  update();
}

/** Downloads an .ics file so the event can be added to Google, Apple or Outlook calendars. */
export function downloadCalendarFile({ title, startsAt, endsAt, venueName, city, venueAddress, description, reference }) {
  const stamp = v => String(v).replaceAll('-', '').replaceAll(':', '').slice(0, 15).padEnd(15, '0');
  const esc = v => String(v || '').replaceAll('\\', '\\\\').replaceAll(',', '\\,').replaceAll(';', '\\;').replace(/\r?\n/g, '\\n');
  const now = new Date().toISOString().replaceAll('-', '').replaceAll(':', '').slice(0, 15) + 'Z';
  const lines = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//BookMatrix//EN', 'CALSCALE:GREGORIAN', 'BEGIN:VEVENT',
    `UID:${reference || stamp(startsAt)}-${Date.now()}@bookmatrix`,
    `DTSTAMP:${now}`, `DTSTART:${stamp(startsAt)}`, `DTEND:${stamp(endsAt || startsAt)}`,
    `SUMMARY:${esc(title)}`,
    `LOCATION:${esc([venueName, venueAddress, city].filter(Boolean).join(', '))}`,
    `DESCRIPTION:${esc((reference ? `Booking reference ${reference}. ` : '') + (description || ''))}`,
    'END:VEVENT', 'END:VCALENDAR'];
  const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar;charset=utf-8' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = (title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'event') + '.ics';
  document.body.appendChild(link);
  link.click();
  setTimeout(() => { URL.revokeObjectURL(link.href); link.remove(); }, 0);
}

/** Google Maps search link for a venue. */
export function directionsUrl(venueName, venueAddress, city) {
  const q = [venueName, venueAddress, city].filter(Boolean).join(', ');
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}`;
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
