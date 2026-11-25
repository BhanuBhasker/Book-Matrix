// Same browser tests, run in WebKit (the engine behind Safari): npx playwright test -c playwright.webkit.config.js
const base = require('./playwright.config.js');
module.exports = {
  ...base,
  reporter: [['list']],
  use: { ...base.use, channel: undefined, browserName: 'webkit' },
};
