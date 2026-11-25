import { $, $$, html, api, param, renderLayout, formatMoney, formatDateTime, emptyState, errorState, toast,
  confirmDialog, clearErrors, showErrors, downloadCalendarFile, plural, ApiError } from '../core.js?v=2026112505';

await renderLayout({ active: 'bookings', requireLogin: true });
const root = $('#checkout-root');
const ref = (param('ref') || '').trim().toUpperCase();
let booking = null;
let timer = null;
let method = 'CARD';

// Test details for the DEMO gateway (shown on the page; no real money moves)
const TEST_CARDS = [
  ['4242 4242 4242 4242', 'Visa — payment succeeds'],
  ['5555 5555 5555 4444', 'Mastercard — payment succeeds'],
  ['4000 0000 0000 0002', 'Visa — declined by bank'],
  ['4000 0000 0000 9995', 'Visa — insufficient funds'],
];
const DEMO_OTP = '123456';   // the simulated bank always sends this code

const LOCK_ICON = html`<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>`;

async function load() {
  if (!/^BM[A-Z0-9]{8}$/.test(ref)) {
    root.innerHTML = emptyState('Booking not found', 'This checkout link is not valid.', html`<a class="btn" href="/bookings.html">My bookings</a>`);
    return;
  }
  try {
    booking = (await api(`/api/bookings/${ref}`)).data;
    render();
  } catch (err) {
    root.innerHTML = err.status === 404
      ? emptyState('Booking not found', 'Open it from your bookings list.', html`<a class="btn" href="/bookings.html">My bookings</a>`)
      : errorState(err);
    $('[data-retry]')?.addEventListener('click', load);
  }
}

function dateTile(iso) {
  const d = new Date(iso);
  return html`<div class="date-stub" aria-hidden="true">
    <div class="m">${d.toLocaleDateString('en-IN', { month: 'short' })}</div>
    <div class="d">${d.getDate()}</div>
    <div class="w">${d.toLocaleDateString('en-IN', { weekday: 'short' })}</div></div>`;
}

function seatChips(labels) {
  return html`<div class="order-seats">${String(labels).split(',').map(s => html`<span>${s.trim()}</span>`)}</div>`;
}

function summary(b) {
  return html`
    <aside class="panel checkout-summary" aria-labelledby="sum-h">
      <h2 id="sum-h" style="font-size:16px">Order summary</h2>
      <div class="order-event">
        ${dateTile(b.eventStartsAt)}
        <div>
          <p><strong>${b.eventTitle}</strong></p>
          <p class="small muted">${formatDateTime(b.eventStartsAt)}</p>
          <p class="small muted">${b.venueName}, ${b.city}</p>
        </div>
      </div>
      <table class="summary">
        <tr><td>Tickets</td><td>${b.quantity}</td></tr>
        ${b.seatLabels ? html`<tr><td>Seats</td><td>${seatChips(b.seatLabels)}</td></tr>` : ''}
        <tr><td>Price per ticket</td><td>${formatMoney(b.unitPrice)}</td></tr>
        <tr><td>Booking fee</td><td>${formatMoney(0)}</td></tr>
        <tr class="total"><td>Total</td><td>${formatMoney(b.totalAmount)}</td></tr>
      </table>
      <p class="small muted mono" style="margin:0">Booking ref. ${b.reference}</p>
    </aside>`;
}

function render() {
  const b = booking;
  clearInterval(timer);
  if (b.status === 'CONFIRMED') {
    renderPaid(b, null, 'This booking is already paid and confirmed.');
    return;
  }
  if (b.status !== 'PENDING_PAYMENT' || !b.payable) {
    renderExpired(b, b.status === 'CANCELLED' ? 'This booking was cancelled.' : null);
    return;
  }
  root.innerHTML = html`
    <div class="checkout-head">
      <div>
        <ol class="steps" aria-label="Booking steps" style="max-width:420px;margin-bottom:14px">
          <li class="done">${b.seatLabels ? 'Seats' : 'Tickets'}</li><li class="done">Review</li><li aria-current="step">Payment</li></ol>
        <h1>Complete your payment</h1>
      </div>
      <p class="hold-timer" id="hold-timer" role="timer" aria-live="off"></p>
    </div>
    <div class="checkout-grid">
      <section class="panel" aria-labelledby="pay-h">
        <div class="demo-banner"><strong>Demo mode</strong> This is a simulated payment gateway. No real money is charged.</div>
        <h2 id="pay-h" style="font-size:16px">Choose a payment method</h2>
        <div class="pay-methods" role="radiogroup" aria-label="Payment method">
          <button type="button" class="pay-method" role="radio" data-method="CARD" aria-checked="true">
            <span class="pm-icon">CARD</span><span><strong>Credit / debit card</strong><small>Visa, Mastercard, RuPay, Amex</small></span></button>
          <button type="button" class="pay-method" role="radio" data-method="UPI" aria-checked="false">
            <span class="pm-icon">UPI</span><span><strong>UPI</strong><small>Pay with any UPI app</small></span></button>
        </div>
        <form id="pay-form" novalidate autocomplete="on">
          <div class="alert alert-error form-alert" role="alert" hidden></div>
          <div data-for="CARD">
            <div class="card-preview" id="card-preview" aria-hidden="true">
              <div class="cp-top"><span class="cp-chip"></span><span class="cp-brand" data-cp-brand>CARD</span></div>
              <div class="cp-number" data-cp-number>•••• •••• •••• ••••</div>
              <div class="cp-bottom">
                <div class="cp-name"><small>Card holder</small><span data-cp-name>YOUR NAME</span></div>
                <div><small>Expires</small><span data-cp-exp>MM/YY</span></div>
              </div>
            </div>
            <div class="field"><label for="cardNumber">Card number</label>
              <div class="input-icon"><input class="input mono" id="cardNumber" name="cardNumber" inputmode="numeric" autocomplete="cc-number" maxlength="23" placeholder="1234 5678 9012 3456" required>
              <span class="card-brand" id="card-brand"></span></div></div>
            <div class="field"><label for="cardName">Name on card</label>
              <input class="input" id="cardName" name="cardName" autocomplete="cc-name" maxlength="60" placeholder="As printed on the card" required></div>
            <div class="form-grid">
              <div class="field"><label for="expiry">Expiry date</label>
                <input class="input mono" id="expiry" name="expiry" inputmode="numeric" autocomplete="cc-exp" maxlength="5" placeholder="MM/YY" required></div>
              <div class="field"><label for="cvv">CVV</label>
                <input class="input mono" id="cvv" name="cvv" type="password" inputmode="numeric" autocomplete="cc-csc" maxlength="4" placeholder="•••" required></div>
            </div>
          </div>
          <div data-for="UPI" hidden>
            <div class="field"><label for="upiId">UPI ID</label>
              <input class="input" id="upiId" name="upiId" autocomplete="off" maxlength="62" placeholder="yourname@okbank" disabled required>
              <span class="hint">We'll send a payment request to this UPI ID. Approve it in your UPI app.</span></div>
          </div>
          <button class="btn btn-accent btn-block btn-lg" type="submit" id="pay-btn">Pay ${formatMoney(b.totalAmount)}</button>
        </form>
        <div class="secure-note">${LOCK_ICON}<span>Payments are processed by the BookMatrix demo gateway. We never store your full card number or CVV — only the card type and last 4 digits.</span></div>
        <details class="test-cards">
          <summary>Test cards for this demo</summary>
          <p class="small muted" style="margin:8px 0 0">Click a number to fill the form. Any future expiry and any 3-digit CVV work. Bank OTP: <span class="mono">${DEMO_OTP}</span></p>
          <ul>${TEST_CARDS.map(([n, label]) => html`<li><button type="button" class="linklike" data-fill="${n}">${n}</button> <span class="muted small">${label}</span></li>`)}
            <li class="small"><span><strong>UPI:</strong> any ID like <span class="mono">name@bank</span> succeeds; <span class="mono">fail@upi</span> is declined.</span></li></ul>
        </details>
        <p style="margin:16px 0 0"><button type="button" class="linklike small" data-release>Cancel and release ${b.seatLabels ? 'these seats' : 'these tickets'}</button></p>
      </section>
      ${summary(b)}
    </div>`;

  startTimer(new Date(b.holdExpiresAt));
  const form = $('#pay-form');
  $$('[data-method]').forEach(btn => btn.addEventListener('click', () => setMethod(btn.dataset.method)));
  $$('[data-fill]').forEach(btn => btn.addEventListener('click', () => {
    setMethod('CARD');
    form.elements.cardNumber.value = btn.dataset.fill;
    form.elements.cardName.value ||= 'Test Person';
    const d = new Date();
    form.elements.expiry.value ||= `12/${String((d.getFullYear() + 2) % 100).padStart(2, '0')}`;
    form.elements.cvv.value ||= '123';
    updatePreview();
    $('#pay-btn').focus();
  }));
  form.elements.cardNumber.addEventListener('input', e => {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 19);
    e.target.value = digits.replace(/(\d{4})(?=\d)/g, '$1 ');
    updatePreview();
  });
  form.elements.expiry.addEventListener('input', e => {
    const d = e.target.value.replace(/\D/g, '').slice(0, 4);
    e.target.value = d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
    updatePreview();
  });
  form.elements.cardName.addEventListener('input', updatePreview);
  form.elements.cvv.addEventListener('input', e => { e.target.value = e.target.value.replace(/\D/g, '').slice(0, 4); });
  form.addEventListener('submit', submit);
  $('[data-release]').addEventListener('click', release);
}

function setMethod(m) {
  method = m;
  $$('[data-method]').forEach(b => b.setAttribute('aria-checked', String(b.dataset.method === m)));
  $$('[data-for]').forEach(box => {
    const show = box.dataset.for === m;
    box.hidden = !show;
    box.querySelectorAll('input').forEach(i => { i.disabled = !show; });   // hidden inputs are skipped by validation
  });
  clearErrors($('#pay-form'));
}

function brandOf(digits) {
  return digits.startsWith('4') ? 'Visa' : /^(5[1-5]|2[2-7])/.test(digits) ? 'Mastercard' : /^3[47]/.test(digits) ? 'Amex'
    : /^(60|65|81|82|508)/.test(digits) ? 'RuPay' : '';
}

/** Keeps the card picture in sync with what the user types. */
function updatePreview() {
  const form = $('#pay-form');
  const digits = form.elements.cardNumber.value.replace(/\D/g, '');
  const brand = brandOf(digits);
  $('#card-brand').textContent = brand;
  const preview = $('#card-preview');
  preview.className = `card-preview ${brand.toLowerCase()}`;
  $('[data-cp-brand]').textContent = brand || 'CARD';
  const padded = (digits + '•'.repeat(Math.max(0, 16 - digits.length))).slice(0, Math.max(16, digits.length));
  $('[data-cp-number]').textContent = padded.replace(/(.{4})(?=.)/g, '$1 ');
  $('[data-cp-name]').textContent = form.elements.cardName.value.trim() || 'YOUR NAME';
  $('[data-cp-exp]').textContent = form.elements.expiry.value || 'MM/YY';
}

function startTimer(expiresAt) {
  const el = $('#hold-timer');
  const tick = () => {
    const ms = expiresAt - Date.now();
    if (ms <= 0) {
      clearInterval(timer);
      closePayDialog();
      renderExpired(booking, null);
      return;
    }
    const m = Math.floor(ms / 60000);
    const s = Math.floor((ms % 60000) / 1000);
    el.textContent = `${booking.seatLabels ? 'Seats' : 'Tickets'} held for ${m}:${String(s).padStart(2, '0')}`;
    el.classList.toggle('urgent', ms < 60000);
  };
  tick();
  timer = setInterval(tick, 1000);
}

// ------------------------------------------------------------------ client-side checks (the server checks again)
function luhn(digits) {
  let sum = 0;
  let dbl = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let d = Number(digits[i]);
    if (dbl) { d *= 2; if (d > 9) d -= 9; }
    sum += d;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}

function checkCard(v) {
  const errors = {};
  const digits = (v.cardNumber || '').replace(/\D/g, '');
  if (!digits) errors.cardNumber = 'Enter the card number.';
  else if (digits.length < 12 || !luhn(digits)) errors.cardNumber = 'This card number is not valid. Check the digits.';
  if (!(v.cardName || '').trim()) errors.cardName = 'Enter the name on the card.';
  const m = /^(\d{2})\/(\d{2})$/.exec(v.expiry || '');
  if (!m || Number(m[1]) < 1 || Number(m[1]) > 12) errors.expiry = 'Use the format MM/YY.';
  else {
    const now = new Date();
    const y = 2000 + Number(m[2]);
    if (y < now.getFullYear() || (y === now.getFullYear() && Number(m[1]) < now.getMonth() + 1)) errors.expiry = 'This card has expired.';
  }
  const amex = brandOf(digits) === 'Amex';
  if (!(amex ? /^\d{4}$/ : /^\d{3}$/).test(v.cvv || '')) errors.cvv = amex ? 'Enter the 4-digit code.' : 'Enter the 3-digit code on the back.';
  return errors;
}

// ------------------------------------------------------------------ pay
async function submit(e) {
  e.preventDefault();
  const form = e.currentTarget;
  clearErrors(form);
  const v = Object.fromEntries(new FormData(form).entries());
  let errors = {};
  if (method === 'CARD') errors = checkCard(v);
  else if (!/^[A-Za-z0-9._-]{2,40}@[A-Za-z]{2,20}$/.test((v.upiId || '').trim())) errors.upiId = 'Enter a UPI ID like name@bank.';
  if (Object.keys(errors).length) {
    showErrors(form, { message: 'Please check the highlighted fields.', fields: errors });
    return;
  }
  const body = method === 'CARD'
    ? { method, cardNumber: v.cardNumber, cardName: v.cardName, expiry: v.expiry, cvv: v.cvv }
    : { method, upiId: v.upiId.trim() };
  const payBtn = $('#pay-btn');
  payBtn.disabled = true;
  try {
    const approved = method === 'CARD' ? await askOtp(v.cardNumber) : await waitForUpi(body.upiId);
    if (!approved) return;                       // user closed the bank window
    showProcessing();
    const [res] = await Promise.all([
      api(`/api/bookings/${ref}/pay`, { method: 'POST', body, timeout: 30000 }),
      new Promise(r => setTimeout(r, 1200)),     // a short, realistic "talking to the bank" pause
    ]);
    closePayDialog();
    clearInterval(timer);
    booking = res.data.booking;
    renderPaid(booking, res.data.payment, res.message);
    toast('Payment successful');
  } catch (err) {
    closePayDialog();
    if (err instanceof ApiError && err.code === 'PAYMENT_DECLINED') {
      showErrors(form, { message: err.message, fields: {} });
      $('.form-alert', form).scrollIntoView({ block: 'center', behavior: 'smooth' });
    } else if (err instanceof ApiError && (err.code === 'BOOKING_NOT_ALLOWED' || err.code === 'ALREADY_PAID')) {
      await load();
      toast(err.message, 'info');
    } else {
      showErrors(form, err);
    }
  } finally {
    if (document.body.contains(payBtn)) payBtn.disabled = false;
  }
}

// ------------------------------------------------------------------ simulated bank windows
let payDialog = null;

function openPayDialog(inner) {
  closePayDialog();
  payDialog = document.createElement('dialog');
  payDialog.className = 'dialog pay-dialog';
  payDialog.setAttribute('aria-labelledby', 'pd-title');
  payDialog.innerHTML = inner;
  document.body.append(payDialog);
  payDialog.showModal();
  return payDialog;
}

function closePayDialog() {
  if (payDialog) {
    payDialog.close();
    payDialog.remove();
    payDialog = null;
  }
}

/** 3-D Secure style step: the "bank" asks for a one-time password. Resolves true when verified. */
function askOtp(cardNumber) {
  const last4 = cardNumber.replace(/\D/g, '').slice(-4);
  return new Promise(resolve => {
    const d = openPayDialog(html`
      <form method="dialog" class="dialog-body" novalidate>
        <div class="gw-name"><i></i>Secure verification · Demo bank</div>
        <h2 id="pd-title">Enter the OTP</h2>
        <p class="amount">${formatMoney(booking.totalAmount)}</p>
        <p class="small muted">A one-time password was sent to the mobile number linked to your card ending ${last4}.</p>
        <div class="field" style="margin:16px 0 6px">
          <label class="visually-hidden" for="otp">One-time password</label>
          <input class="input otp-input" id="otp" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="••••••">
          <span class="field-error" data-otp-error></span>
        </div>
        <p class="small muted">Demo OTP: <strong class="mono">${DEMO_OTP}</strong></p>
        <div class="dialog-actions" style="padding:8px 0 4px">
          <button type="button" class="btn btn-secondary" data-cancel>Cancel</button>
          <button type="submit" class="btn btn-accent" data-verify>Verify &amp; pay</button>
        </div>
      </form>`);
    const input = $('#otp', d);
    input.addEventListener('input', () => {
      input.value = input.value.replace(/\D/g, '').slice(0, 6);
      $('[data-otp-error]', d).textContent = '';
    });
    input.focus();
    let done = false;
    const finish = ok => { if (!done) { done = true; resolve(ok); } };
    $('[data-cancel]', d).addEventListener('click', () => { closePayDialog(); finish(false); });
    d.addEventListener('cancel', () => { finish(false); setTimeout(closePayDialog); });
    $('form', d).addEventListener('submit', ev => {
      ev.preventDefault();
      if (input.value !== DEMO_OTP) {
        $('[data-otp-error]', d).textContent = input.value.length < 6 ? 'Enter the 6-digit code.' : 'Incorrect OTP. Please try again.';
        input.select();
        return;
      }
      finish(true);
    });
  });
}

/** UPI collect request: the user "approves" it in their app. */
function waitForUpi(upiId) {
  return new Promise(resolve => {
    const d = openPayDialog(html`
      <div class="dialog-body">
        <div class="gw-name"><i></i>UPI · Demo gateway</div>
        <h2 id="pd-title">Approve the payment request</h2>
        <p class="amount">${formatMoney(booking.totalAmount)}</p>
        <p class="small muted">A request was sent to <strong class="mono">${upiId}</strong>. Open your UPI app and approve it.</p>
        <div class="big-spinner" aria-hidden="true"></div>
        <div class="dialog-actions" style="padding:8px 0 4px">
          <button type="button" class="btn btn-secondary" data-cancel>Cancel</button>
          <button type="button" class="btn btn-accent" data-approve>I've approved it</button>
        </div>
      </div>`);
    let done = false;
    const finish = ok => { if (!done) { done = true; resolve(ok); } };
    $('[data-cancel]', d).addEventListener('click', () => { closePayDialog(); finish(false); });
    $('[data-approve]', d).addEventListener('click', () => finish(true));
    d.addEventListener('cancel', () => { finish(false); setTimeout(closePayDialog); });
  });
}

function showProcessing() {
  const d = openPayDialog(html`
    <div class="dialog-body" role="status">
      <div class="gw-name"><i></i>BookMatrix demo gateway</div>
      <div class="big-spinner" aria-hidden="true"></div>
      <h2 id="pd-title">Processing your payment…</h2>
      <p class="small muted">Please don't close this window or press back.</p>
    </div>`);
  d.addEventListener('cancel', ev => ev.preventDefault());   // can't be dismissed mid-payment
}

// ------------------------------------------------------------------ release / results
async function release() {
  const ok = await confirmDialog({
    title: 'Release this hold?', confirmText: 'Release', cancelText: 'Keep my booking', danger: true,
    body: `${booking.seatLabels ? 'Seats ' + booking.seatLabels : plural(booking.quantity, 'ticket')} will go back on sale. Nothing has been charged.`,
  });
  if (!ok) return;
  try {
    await api(`/api/bookings/${ref}/cancel`, { method: 'POST', body: { reason: 'Released at checkout' } });
    clearInterval(timer);
    toast('Hold released. Nothing was charged.', 'info');
    location.href = `/event.html?id=${booking.eventId}&v=2026112505`;
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderPaid(b, payment, message) {
  root.innerHTML = html`
    <div class="checkout-grid" style="margin-top:32px">
      <section class="ticket" role="status">
        <div class="ticket-head">
          <span class="tick" aria-hidden="true">✓</span>
          <div><h1>Booking confirmed</h1><p>${message}</p></div>
        </div>
        <div class="ticket-body">
          <p class="eyebrow" style="margin:0">${formatDateTime(b.eventStartsAt)}</p>
          <h2 style="font-size:22px;margin:4px 0 2px">${b.eventTitle}</h2>
          <p class="muted" style="margin:0">${b.venueName}, ${b.city}</p>
          <dl class="ticket-grid">
            <div><dt>Tickets</dt><dd>${b.quantity}</dd></div>
            ${b.seatLabels ? html`<div><dt>Seats</dt><dd>${b.seatLabels}</dd></div>` : ''}
            <div><dt>Amount paid</dt><dd>${formatMoney(b.totalAmount)}</dd></div>
            ${payment ? html`<div><dt>Paid with</dt><dd>${payment.methodLabel}</dd></div>` : ''}
          </dl>
          ${payment ? html`<p class="small muted" style="margin:0">Transaction ID <span class="mono">${payment.gatewayRef}</span></p>` : ''}
        </div>
        <div class="ticket-cut" aria-hidden="true"></div>
        <div class="ticket-foot">
          <div><p class="small muted" style="margin:0">Booking reference</p><div class="ref" aria-label="Booking reference">${b.reference}</div></div>
          <div class="row">
            <a class="btn btn-accent" href="/booking.html?ref=${b.reference}">View booking</a>
            <a class="btn btn-secondary" href="/api/bookings/${b.reference}/receipt" download>Receipt</a>
            <button type="button" class="btn btn-secondary" data-calendar>Add to calendar</button>
          </div>
        </div>
      </section>
      ${summary(b)}
    </div>`;
  $('[data-calendar]').addEventListener('click', () => downloadCalendarFile({ title: b.eventTitle, startsAt: b.eventStartsAt,
    endsAt: b.eventEndsAt, venueName: b.venueName, city: b.city, reference: b.reference }));
}

function renderExpired(b, reason) {
  root.innerHTML = html`<div style="margin-top:32px">${emptyState(
    reason ? 'This booking is closed' : 'Your hold has expired',
    reason || `You didn't complete the payment within 10 minutes, so the ${b.seatLabels ? 'seats were' : 'tickets were'} released for others. Nothing was charged.`,
    html`<a class="btn" href="/event.html?id=${b.eventId}&v=2026112505">Book again</a> <a class="btn btn-secondary" href="/bookings.html">My bookings</a>`)}</div>`;
}

load();
