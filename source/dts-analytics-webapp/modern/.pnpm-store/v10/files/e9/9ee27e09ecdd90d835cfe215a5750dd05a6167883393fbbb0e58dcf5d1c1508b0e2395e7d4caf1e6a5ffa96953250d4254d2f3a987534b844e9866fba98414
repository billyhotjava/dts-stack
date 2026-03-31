'use strict';

function _interopDefault (ex) { return (ex && (typeof ex === 'object') && 'default' in ex) ? ex['default'] : ex; }

var React = require('react');
var React__default = _interopDefault(React);
var styleInject_es = require('../style-inject.es-55d78dad.js');
require('../index-92c36402.js');
require('../_babelHelpers-1c35d3ad.js');
var autoResize = require('../autoResize-4af58baf.js');
var index$1 = require('../index-4a59a5ac.js');
var index$2 = require('../index-8aeda36a.js');

var css = ".dv-border-box-6 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-6 .dv-border-svg-container {\n  position: absolute;\n  top: 0px;\n  left: 0px;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-6 .dv-border-svg-container > polyline {\n  fill: none;\n  stroke-width: 1;\n}\n.dv-border-box-6 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject_es.styleInject(css);

var defaultColor = ['rgba(255, 255, 255, 0.35)', 'gray'];

var BorderBox = React.forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$backgroundColor = _ref.backgroundColor,
      backgroundColor = _ref$backgroundColor === undefined ? 'transparent' : _ref$backgroundColor;

  var _useAutoResize = autoResize.useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var mergedColor = React.useMemo(function () {
    return index$2.util_2(index$2.util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = React.useMemo(function () {
    return index$1.classnames('dv-border-box-6', className);
  }, [className]);

  return React__default.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React__default.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React__default.createElement('polygon', { fill: backgroundColor, points: '\n          9, 7 ' + (width - 9) + ', 7 ' + (width - 9) + ', ' + (height - 7) + ' 9, ' + (height - 7) + '\n        ' }),
      React__default.createElement('circle', { fill: mergedColor[1], cx: '5', cy: '5', r: '2' }),
      React__default.createElement('circle', { fill: mergedColor[1], cx: width - 5, cy: '5', r: '2' }),
      React__default.createElement('circle', { fill: mergedColor[1], cx: width - 5, cy: height - 5, r: '2' }),
      React__default.createElement('circle', { fill: mergedColor[1], cx: '5', cy: height - 5, r: '2' }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '10, 4 ' + (width - 10) + ', 4' }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '10, ' + (height - 4) + ' ' + (width - 10) + ', ' + (height - 4) }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '5, 70 5, ' + (height - 70) }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: width - 5 + ', 70 ' + (width - 5) + ', ' + (height - 70) }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '3, 10, 3, 50' }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '7, 30 7, 80' }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: width - 3 + ', 10 ' + (width - 3) + ', 50' }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: width - 7 + ', 30 ' + (width - 7) + ', 80' }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '3, ' + (height - 10) + ' 3, ' + (height - 50) }),
      React__default.createElement('polyline', { stroke: mergedColor[0], points: '7, ' + (height - 30) + ' 7, ' + (height - 80) }),
      React__default.createElement('polyline', {
        stroke: mergedColor[0],
        points: width - 3 + ', ' + (height - 10) + ' ' + (width - 3) + ', ' + (height - 50)
      }),
      React__default.createElement('polyline', {
        stroke: mergedColor[0],
        points: width - 7 + ', ' + (height - 30) + ' ' + (width - 7) + ', ' + (height - 80)
      })
    ),
    React__default.createElement(
      'div',
      { className: 'border-box-content' },
      children
    )
  );
});

BorderBox.propTypes = {
  children: styleInject_es.PropTypes.node,
  className: styleInject_es.PropTypes.string,
  style: styleInject_es.PropTypes.object,
  color: styleInject_es.PropTypes.array,
  backgroundColor: styleInject_es.PropTypes.string
};

module.exports = BorderBox;
//# sourceMappingURL=index.js.map
