import { $, html, api, formatDateTime, toast, withBusy, handleForm, errorState, showErrors } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('data');

async function loadBackups() {
  try {
    const { data } = await api('/api/admin/data/backups');
    $('#backups').innerHTML = data.length
      ? html`<p class="muted" style="margin:0 0 4px">Existing backups (newest first):</p><ul style="margin:0;padding-left:18px">${data.slice(0, 5).map(b => html`<li class="mono">${b}</li>`)}</ul>`
      : html`<p class="muted">No backups yet.</p>`;
  } catch (err) {
    $('#backups').textContent = err.message;
  }
}

async function loadActivity() {
  try {
    const { data } = await api('/api/admin/activity?limit=40');
    $('#activity').innerHTML = data.length ? html`<div class="table-wrap"><table class="table">
      <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Details</th></tr></thead>
      <tbody>${data.map(a => html`<tr><td class="small" style="white-space:nowrap">${formatDateTime(a.createdAt)}</td><td class="small">${a.actorName || 'system'}</td>
        <td><span class="mono" style="font-size:11px">${a.action}</span></td><td class="small">${a.details || ''}</td></tr>`)}</tbody></table></div>`
      : html`<p class="muted small">No activity yet.</p>`;
  } catch (err) {
    $('#activity').innerHTML = errorState(err);
  }
}

$('#backup-btn').addEventListener('click', ev => withBusy(ev.currentTarget, async () => {
  try {
    const res = await api('/api/admin/data/backup', { method: 'POST' });
    toast(`${res.message} ${res.data.files.length} files in ${res.data.folder}`);
    loadBackups();
    loadActivity();
  } catch (err) { toast(err.message, 'error'); }
}, 'Writing…'));

handleForm($('#import-form'), async (values, form) => {
  const file = form.elements.file.files[0];
  if (!file) { showErrors(form, { message: 'Choose a CSV file.', fields: { file: 'Choose a CSV file.' } }); return; }
  if (!file.name.toLowerCase().endsWith('.csv')) { showErrors(form, { message: 'Only .csv files can be imported.', fields: { file: 'Only .csv files can be imported.' } }); return; }
  if (file.size > 1024 * 1024) { showErrors(form, { message: 'The file is larger than 1 MB.', fields: { file: 'The file is larger than 1 MB.' } }); return; }
  const body = new FormData();
  body.append('file', file);
  const res = await api('/api/admin/data/import/events', { method: 'POST', form: body, timeout: 60000 });
  const r = res.data;
  $('#import-report').innerHTML = html`
    <div class="alert ${r.skipped ? 'alert-warn' : 'alert-ok'}" role="status">${res.message} (${r.rowsRead} rows read)</div>
    ${r.errors.length ? html`<div class="table-wrap"><table class="table"><thead><tr><th class="num">Line</th><th>Problem</th></tr></thead>
      <tbody>${r.errors.map(e => html`<tr><td class="num">${e.line || '—'}</td><td class="small">${e.message}</td></tr>`)}</tbody></table></div>` : ''}`;
  form.reset();
  toast(res.message);
  loadActivity();
});

loadBackups();
loadActivity();
