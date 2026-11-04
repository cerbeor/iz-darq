// Headless karma config for CI / non-interactive runs: `ng test --karma-config=karma.ci.conf.js`
const base = require('./karma.conf.js');

module.exports = function (config) {
  base(config);
  config.set({
    browsers: ['ChromeHeadlessCI'],
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    reporters: ['progress'],
    browserNoActivityTimeout: 120000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 2,
    captureTimeout: 120000,
    autoWatch: false,
    singleRun: true,
  });
};
