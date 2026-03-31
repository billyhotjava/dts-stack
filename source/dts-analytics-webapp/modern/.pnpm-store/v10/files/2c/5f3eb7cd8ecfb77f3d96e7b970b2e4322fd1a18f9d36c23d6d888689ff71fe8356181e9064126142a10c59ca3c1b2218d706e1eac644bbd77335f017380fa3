'use strict';

function _interopDefault (ex) { return (ex && (typeof ex === 'object') && 'default' in ex) ? ex['default'] : ex; }

var React = require('react');
var React__default = _interopDefault(React);
var styleInject_es = require('../style-inject.es-55d78dad.js');
require('../index-92c36402.js');
require('../_babelHelpers-1c35d3ad.js');
var autoResize = require('../autoResize-4af58baf.js');
var index$1 = require('../index-4a59a5ac.js');
require('../index-8aeda36a.js');
require('../index-19aa5843.js');
require('../index-cd27b7f6.js');
require('../index-13c4b549.js');
var index$5 = require('../index-fcdce9c7.js');

var css = ".dv-charts-container {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-charts-container .charts-canvas-container {\n  width: 100%;\n  height: 100%;\n}\n";
styleInject_es.styleInject(css);

var Charts = React.forwardRef(function (_ref, ref) {
  var _ref$option = _ref.option,
      option = _ref$option === undefined ? {} : _ref$option,
      className = _ref.className,
      style = _ref.style;

  var _useAutoResize = autoResize.useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var chartRef = React.useRef(null);

  var chartInstanceofRef = React.useRef(null);

  React.useEffect(function () {
    chartInstanceofRef.current || (chartInstanceofRef.current = new index$5.Charts(chartRef.current));

    chartInstanceofRef.current.setOption(option || {}, true);
  }, [option]);

  React.useEffect(function () {
    chartInstanceofRef.current.resize();
  }, [width, height]);

  var classNames = React.useMemo(function () {
    return index$1.classnames('dv-charts-container', className);
  }, [className]);

  return React__default.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React__default.createElement('div', { className: 'charts-canvas-container', ref: chartRef })
  );
});

Charts.propTypes = {
  option: styleInject_es.PropTypes.object,
  className: styleInject_es.PropTypes.string,
  style: styleInject_es.PropTypes.object
};

module.exports = Charts;
//# sourceMappingURL=index.js.map
