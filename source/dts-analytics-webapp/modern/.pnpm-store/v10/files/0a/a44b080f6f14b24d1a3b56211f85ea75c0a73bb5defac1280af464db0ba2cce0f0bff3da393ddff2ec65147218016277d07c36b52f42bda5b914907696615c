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

var css = ".dv-border-box-3 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-3 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  top: 0px;\n  left: 0px;\n}\n.dv-border-box-3 .dv-border-svg-container > polyline {\n  fill: none;\n}\n.dv-border-box-3 .dv-bb3-line1 {\n  stroke-width: 3;\n}\n.dv-border-box-3 .dv-bb3-line2 {\n  stroke-width: 1;\n}\n.dv-border-box-3 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject_es.styleInject(css);

var defaultColor = ['#2862b7', '#2862b7'];

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
    return index$1.classnames('dv-border-box-3', className);
  }, [className]);

  return React__default.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React__default.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React__default.createElement('polygon', { fill: backgroundColor, points: '\n          23, 23 ' + (width - 24) + ', 23 ' + (width - 24) + ', ' + (height - 24) + ' 23, ' + (height - 24) + '\n        ' }),
      React__default.createElement('polyline', {
        className: 'dv-bb3-line1',
        stroke: mergedColor[0],
        points: '4, 4 ' + (width - 22) + ' ,4 ' + (width - 22) + ', ' + (height - 22) + ' 4, ' + (height - 22) + ' 4, 4'
      }),
      React__default.createElement('polyline', {
        className: 'dv-bb3-line2',
        stroke: mergedColor[1],
        points: '10, 10 ' + (width - 16) + ', 10 ' + (width - 16) + ', ' + (height - 16) + ' 10, ' + (height - 16) + ' 10, 10'
      }),
      React__default.createElement('polyline', {
        className: 'dv-bb3-line2',
        stroke: mergedColor[1],
        points: '16, 16 ' + (width - 10) + ', 16 ' + (width - 10) + ', ' + (height - 10) + ' 16, ' + (height - 10) + ' 16, 16'
      }),
      React__default.createElement('polyline', {
        className: 'dv-bb3-line2',
        stroke: mergedColor[1],
        points: '22, 22 ' + (width - 4) + ', 22 ' + (width - 4) + ', ' + (height - 4) + ' 22, ' + (height - 4) + ' 22, 22'
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
