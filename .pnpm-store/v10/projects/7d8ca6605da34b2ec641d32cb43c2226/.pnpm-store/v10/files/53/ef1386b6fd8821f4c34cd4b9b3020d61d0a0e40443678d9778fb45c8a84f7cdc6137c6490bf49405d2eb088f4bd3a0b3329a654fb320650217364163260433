'use strict';

function _interopDefault (ex) { return (ex && (typeof ex === 'object') && 'default' in ex) ? ex['default'] : ex; }

var React = require('react');
var React__default = _interopDefault(React);
var index = require('./index-92c36402.js');
var _babelHelpers = require('./_babelHelpers-1c35d3ad.js');

function useAutoResize(ref) {
  var _useState = React.useState({ width: 0, height: 0 }),
      _useState2 = _babelHelpers.slicedToArray(_useState, 2),
      state = _useState2[0],
      setState = _useState2[1];

  var domRef = React.useRef(null);

  var setWH = React.useCallback(function () {
    var _ref = domRef.current || { clientWidth: 0, clientHeight: 0 },
        clientWidth = _ref.clientWidth,
        clientHeight = _ref.clientHeight;

    setState({ width: clientWidth, height: clientHeight });

    if (!domRef.current) {
      console.warn('DataV: Failed to get dom node, component rendering may be abnormal!');
    } else if (!clientWidth || !clientHeight) {
      console.warn('DataV: Component width or height is 0px, rendering abnormality may occur!');
    }
  }, []);

  React.useImperativeHandle(ref, function () {
    return { setWH: setWH };
  }, []);

  React.useEffect(function () {
    var debounceSetWHFun = index.debounce(setWH, 100);

    debounceSetWHFun();

    var domObserver = index.observerDomResize(domRef.current, debounceSetWHFun);

    window.addEventListener('resize', debounceSetWHFun);

    return function () {
      window.removeEventListener('resize', debounceSetWHFun);

      if (!domObserver) {
        return;
      }

      domObserver.disconnect();
      domObserver.takeRecords();
    };
  }, []);

  return _babelHelpers._extends({}, state, { domRef: domRef, setWH: setWH });
}

exports.useAutoResize = useAutoResize;
//# sourceMappingURL=autoResize-4af58baf.js.map
