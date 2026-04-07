'use strict';

function _interopDefault (ex) { return (ex && (typeof ex === 'object') && 'default' in ex) ? ex['default'] : ex; }

var React = require('react');
var React__default = _interopDefault(React);
var styleInject_es = require('../style-inject.es-55d78dad.js');
var index = require('../index-92c36402.js');
var _babelHelpers = require('../_babelHelpers-1c35d3ad.js');
var autoResize = require('../autoResize-4af58baf.js');
var index$1 = require('../index-4a59a5ac.js');
var index$2 = require('../index-8aeda36a.js');

var css = ".dv-border-box-8 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-8 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  left: 0px;\n  top: 0px;\n}\n.dv-border-box-8 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject_es.styleInject(css);

var defaultColor = ['#235fa7', '#4fd2dd'];

var BorderBox = React.forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$dur = _ref.dur,
      dur = _ref$dur === undefined ? 3 : _ref$dur,
      _ref$backgroundColor = _ref.backgroundColor,
      backgroundColor = _ref$backgroundColor === undefined ? 'transparent' : _ref$backgroundColor,
      _ref$reverse = _ref.reverse,
      reverse = _ref$reverse === undefined ? false : _ref$reverse;

  var _useAutoResize = autoResize.useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var _useState = React.useState(function () {
    var id = index.uuid();

    return {
      path: 'border-box-8-path-' + id,
      gradient: 'border-box-8-gradient-' + id,
      mask: 'border-box-8-mask-' + id
    };
  }),
      _useState2 = _babelHelpers.slicedToArray(_useState, 1),
      _useState2$ = _useState2[0],
      path = _useState2$.path,
      gradient = _useState2$.gradient,
      mask = _useState2$.mask;

  var pathD = React.useMemo(function () {
    return reverse ? 'M 2.5, 2.5 L 2.5, ' + (height - 2.5) + ' L ' + (width - 2.5) + ', ' + (height - 2.5) + ' L ' + (width - 2.5) + ', 2.5 L 2.5, 2.5' : 'M2.5, 2.5 L' + (width - 2.5) + ', 2.5 L' + (width - 2.5) + ', ' + (height - 2.5) + ' L2.5, ' + (height - 2.5) + ' L2.5, 2.5';
  }, [width, height, reverse]);

  var mergedColor = React.useMemo(function () {
    return index$2.util_2(index$2.util_1(defaultColor, true), color || []);
  }, [color]);

  var length = React.useMemo(function () {
    return (width + height - 5) * 2;
  }, [width, height]);

  var classNames = React.useMemo(function () {
    return index$1.classnames('dv-border-box-8', className);
  }, [className]);

  return React__default.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React__default.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React__default.createElement(
        'defs',
        null,
        React__default.createElement('path', { id: path, d: pathD, fill: 'transparent' }),
        React__default.createElement(
          'radialGradient',
          { id: gradient, cx: '50%', cy: '50%', r: '50%' },
          React__default.createElement('stop', { offset: '0%', stopColor: '#fff', stopOpacity: '1' }),
          React__default.createElement('stop', { offset: '100%', stopColor: '#fff', stopOpacity: '0' })
        ),
        React__default.createElement(
          'mask',
          { id: mask },
          React__default.createElement(
            'circle',
            { cx: '0', cy: '0', r: '150', fill: 'url(#' + gradient + ')' },
            React__default.createElement('animateMotion', { dur: dur + 's', path: pathD, rotate: 'auto', repeatCount: 'indefinite' })
          )
        )
      ),
      React__default.createElement('polygon', { fill: backgroundColor, points: '5, 5 ' + (width - 5) + ', 5 ' + (width - 5) + ' ' + (height - 5) + ' 5, ' + (height - 5) }),
      React__default.createElement('use', { stroke: mergedColor[0], strokeWidth: '1', href: '#' + path }),
      React__default.createElement(
        'use',
        {
          stroke: mergedColor[1],
          strokeWidth: '3',
          href: '#' + path,
          mask: 'url(#' + mask + ')'
        },
        React__default.createElement('animate', {
          attributeName: 'stroke-dasharray',
          from: '0, ' + length,
          to: length + ', 0',
          dur: dur + 's',
          repeatCount: 'indefinite'
        })
      )
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
  dur: styleInject_es.PropTypes.number,
  backgroundColor: styleInject_es.PropTypes.string,
  reverse: styleInject_es.PropTypes.bool
};

module.exports = BorderBox;
//# sourceMappingURL=index.js.map
