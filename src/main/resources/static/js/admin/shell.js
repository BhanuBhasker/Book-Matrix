import { $, html, renderLayout } from '../core.js?v=2026112505';

const LINKS = [
  ['dashboard', '/admin/index.html', 'Dashboard'],
  ['events', '/admin/events.html', 'Events'],
  ['bookings', '/admin/bookings.html', 'Bookings'],
  ['users', '/admin/users.html', 'Users'],
  ['payments', '/admin/payments.html', 'Payments'],
  ['inventory', '/admin/inventory.html', 'Inventory'],
  ['data', '/admin/data.html', 'Import & export'],
  ['lab', '/admin/java-lab.html', 'Java Lab'],
];

/** Renders the header + admin sidebar. The server already blocked non-admins (PageAccessInterceptor). */
export async function initAdmin(active) {
  const user = await renderLayout({ active: 'admin', requireLogin: true });
  if (user && user.role !== 'ADMIN') {
    location.replace('/?denied=admin');
    return new Promise(() => {});
  }
  $('#admin-side').innerHTML = html`
    <p class="eyebrow">Administration</p>
    <nav>${LINKS.map(([key, href, label]) => html`<a href="${href}" ${key === active ? html`aria-current="page"` : ''}>${label}</a>`)}</nav>`;
  return user;
}
