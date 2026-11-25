import { $, html, api, param, renderLayout, handleForm, setSession, toast, enhancePasswords } from '../core.js?v=2026112505';

const user = await renderLayout({ active: 'login' });
const next = safeNext(param('next'));
if (user) location.replace(next);

const notices = {
  expired: ['alert-warn', 'Your session expired. Please log in again.'],
  login: ['alert-info', 'Please log in to continue.'],
  registered: ['alert-ok', 'Account created. Log in with your new password.'],
  closed: ['alert-info', 'Your account has been closed.'],
};
const notice = notices[param('reason')];
if (notice) $('#login-notice').innerHTML = html`<div class="alert ${notice[0]}" role="status">${notice[1]}</div>`;
if (param('email')) $('#email').value = param('email');
enhancePasswords();
($('#email').value ? $('#password') : $('#email')).focus();

handleForm($('#login-form'), async values => {
  const res = await api('/api/auth/login', { method: 'POST', body: values, allow401: true });
  setSession(res.data);
  toast(res.message);
  location.href = next === '/' && res.data.user.role === 'ADMIN' ? '/admin/index.html' : next;
});

/** Only allow redirects to pages on this site (prevents open-redirect tricks). */
function safeNext(value) {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/';
}
