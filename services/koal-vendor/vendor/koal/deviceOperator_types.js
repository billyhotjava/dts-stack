// Legacy compatibility shim: alias deviceOperator_types to devService_types
(function(){
  if (typeof window === 'undefined' || typeof document === 'undefined') return;
  var scripts = document.getElementsByTagName('script');
  var me = scripts[scripts.length - 1];
  var mySrc = (me && me.src) ? me.src : '';
  var base = '/vendor/koal/';
  var m = mySrc.match(/^(.*\/)(?:deviceOperator_types\.js)(?:\?.*)?$/);
  if (m && m[1]) base = m[1];
  var s = document.createElement('script');
  s.src = base + 'devService_types.js';
  document.head.appendChild(s);
})();

