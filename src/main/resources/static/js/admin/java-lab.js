import { $, $$, html, api, toast, errorState } from '../core.js?v=2026112505';
import { initAdmin } from './shell.js?v=2026112505';

await initAdmin('lab');

async function init() {
  try {
    const { data } = await api('/api/admin/demos');
    $('#demo-list').innerHTML = html`${data.map(d => html`
      <button type="button" data-demo="${d.name}" aria-pressed="false" title="${d.topics}">${d.title}<small>${d.name}</small></button>`)}`;
    $$('[data-demo]').forEach(b => b.addEventListener('click', () => run(b, data.find(d => d.name === b.dataset.demo))));
  } catch (err) {
    $('#demo-list').innerHTML = errorState(err);
  }
}

async function run(button, demo) {
  if (button.dataset.busy === '1') return;
  $$('[data-demo]').forEach(b => { b.setAttribute('aria-pressed', String(b === button)); b.disabled = true; });
  button.dataset.busy = '1';
  $('#demo-title').textContent = demo.title;
  $('#demo-meta').textContent = 'Running…';
  $('#console').textContent = `$ mvn -q exec:java -Dexec.args="${demo.name}"\n\n`;
  try {
    const { data } = await api(`/api/admin/demos/${demo.name}/run`, { method: 'POST', timeout: 45000 });
    $('#console').textContent += `Topics: ${demo.topics}\n${data.output}`;
    $('#demo-meta').textContent = `${data.success ? 'Finished' : 'Failed'} in ${data.millis} ms`;
  } catch (err) {
    $('#demo-meta').textContent = 'Failed';
    toast(err.message, 'error');
  } finally {
    button.dataset.busy = '';
    $$('[data-demo]').forEach(b => { b.disabled = false; });
  }
}

init();
