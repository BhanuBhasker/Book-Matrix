import { $, api, renderLayout, handleForm, showErrors, enhancePasswords, passwordChecklist } from '../core.js?v=2026112505';

const user = await renderLayout({ active: 'login' });
if (user) location.replace('/');
enhancePasswords();
passwordChecklist($('#password'), $('#confirmPassword'));
$('#fullName').focus();

handleForm($('#register-form'), async (values, form) => {
  // quick client-side checks for instant feedback; the server checks everything again
  const fields = {};
  if (values.password.length < 8) fields.password = 'Use at least 8 characters.';
  else if (!/[A-Za-z]/.test(values.password) || !/\d/.test(values.password)) fields.password = 'Use at least one letter and one number.';
  if (values.password !== values.confirmPassword) fields.confirmPassword = 'Passwords do not match.';
  if (Object.keys(fields).length) {
    showErrors(form, { message: 'Please fix the highlighted fields.', fields });
    return;
  }
  await api('/api/auth/register', { method: 'POST', body: values, allow401: true });
  location.href = `/login.html?reason=registered&email=${encodeURIComponent(values.email.trim())}`;
});
