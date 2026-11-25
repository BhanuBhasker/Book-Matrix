import { $, html, api, renderLayout, handleForm, showErrors, badge, formatDateTime, confirmDialog, toast, setSession,
  enhancePasswords, passwordChecklist } from '../core.js?v=2026112505';

await renderLayout({ active: 'profile', requireLogin: true });
enhancePasswords();
passwordChecklist($('#newPassword'), $('#confirmPassword'));

async function load() {
  const { data } = await api('/api/me');
  $('#account-info').innerHTML = html`
    <dt>Email</dt><dd>${data.email}</dd>
    <dt>Role</dt><dd>${badge(data.role, data.role === 'ADMIN' ? 'Administrator' : 'Member')}</dd>
    <dt>Status</dt><dd>${badge(data.status)}</dd>
    <dt>Member since</dt><dd>${formatDateTime(data.createdAt)}</dd>
    <dt>Last login</dt><dd>${formatDateTime(data.lastLoginAt)}</dd>`;
  $('#fullName').value = data.fullName;
  $('#phone').value = data.phone || '';
}

handleForm($('#profile-form'), async values => {
  const res = await api('/api/me', { method: 'PATCH', body: { fullName: values.fullName, phone: values.phone } });
  toast(res.message);
  const nameEl = document.querySelector('.nav-user');
  if (nameEl) nameEl.textContent = res.data.fullName;
});

handleForm($('#password-form'), async (values, form) => {
  if (values.newPassword !== values.confirmPassword) {
    showErrors(form, { message: 'Passwords do not match.', fields: { confirmPassword: 'Passwords do not match.' } });
    return;
  }
  const res = await api('/api/me/password', { method: 'PUT', body: values });
  form.reset();
  toast(res.message);
});

handleForm($('#close-form'), async (values, form) => {
  const ok = await confirmDialog({
    title: 'Close your account?',
    body: 'You will be logged out. Upcoming bookings will be cancelled and refunded. This cannot be undone.',
    confirmText: 'Close account', danger: true, requireText: 'DELETE',
  });
  if (!ok) return;
  const res = await api('/api/me', { method: 'DELETE', body: { password: values.password, confirmation: 'DELETE' } });
  setSession(null);
  toast(res.message, 'info');
  location.href = '/login.html?reason=closed';
});

load().catch(err => toast(err.message, 'error'));
