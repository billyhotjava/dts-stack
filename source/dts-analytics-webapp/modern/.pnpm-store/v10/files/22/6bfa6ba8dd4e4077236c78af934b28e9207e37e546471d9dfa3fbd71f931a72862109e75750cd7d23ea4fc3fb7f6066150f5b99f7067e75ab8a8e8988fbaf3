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

var css = ".dv-border-box-13 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-13 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  top: 0px;\n  left: 0px;\n}\n.dv-border-box-13 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject_es.styleInject(css);

var defaultColor = ['#6586ec', '#2cf7fe'];

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
    return index$1.classnames('dv-border-box-13', className);
  }, [className]);

  return React__default.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React__default.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React__default.createElement('path', {
        fill: backgroundColor,
        stroke: mergedColor[0],
        d: '\n            M 5 20 L 5 10 L 12 3  L 60 3 L 68 10\n            L ' + (width - 20) + ' 10 L ' + (width - 5) + ' 25\n            L ' + (width - 5) + ' ' + (height - 5) + ' L 20 ' + (height - 5) + '\n            L 5 ' + (height - 20) + ' L 5 20\n          '
      }),
      React__default.createElement('path', {
        fill: 'transparent',
        strokeWidth: '3',
        strokeLinecap: 'round',
        strokeDasharray: '10, 5',
        stroke: mergedColor[0],
        d: 'M 16 9 L 61 9'
      }),
      React__default.createElement('path', {
        fill: 'transparent',
        stroke: mergedColor[1],
        d: 'M 5 20 L 5 10 L 12 3  L 60 3 L 68 10'
      }),
      React__default.createElement('path', {
        fill: 'transparent',
        stroke: mergedColor[1],
        d: 'M ' + (width - 5) + ' ' + (height - 30) + ' L ' + (width - 5) + ' ' + (height - 5) + ' L ' + (width - 30) + ' ' + (height - 5)
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
