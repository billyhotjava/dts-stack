// Legacy compatibility shim for older Koal SDK integrations
// Some environments attempt to load `/vendor/koal/deviceOperator.js` and expect
// a global `window.deviceOperatorClient`. Our bundled SDK provides
// `/vendor/koal/devService.js` with `window.devServiceClient`.
//
// This shim ensures the legacy path works by loading devService.js if needed
// and then aliasing `deviceOperatorClient` to `devServiceClient`.
(function () {
  function alias() {
    try {
      if (window.devServiceClient && !window.deviceOperatorClient) {
        window.deviceOperatorClient = window.devServiceClient;
      }
    } catch (_) {
      // ignore
    }
  }

  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  if (window.devServiceClient) {
    alias();
    return;
  }

  // Compute base url from the current script src so it works under any host
  var scripts = document.getElementsByTagName('script');
  var me = scripts[scripts.length - 1];
  var mySrc = (me && me.src) ? me.src : '';
  var base = '/vendor/koal/';
  var m = mySrc.match(/^(.*\/)(?:deviceOperator\.js)(?:\?.*)?$/);
  if (m && m[1]) {
    base = m[1];
  }

  var s = document.createElement('script');
  s.src = base + 'devService.js';
  s.onload = alias;
  s.onerror = function () {
    // Leave a console hint for troubleshooting if the legacy path fails
    if (typeof console !== 'undefined' && console.error) {
      console.error('[koal] Failed to load devService.js via legacy deviceOperator.js shim');
    }
  };
  document.head.appendChild(s);
})();

