import React, { forwardRef, useState, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import { u as uuid } from '../index-65ee9985.js';
import { s as slicedToArray } from '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-8 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-8 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  left: 0px;\n  top: 0px;\n}\n.dv-border-box-8 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['#235fa7', '#4fd2dd'];

var BorderBox = forwardRef(function (_ref, ref) {
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

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var _useState = useState(function () {
    var id = uuid();

    return {
      path: 'border-box-8-path-' + id,
      gradient: 'border-box-8-gradient-' + id,
      mask: 'border-box-8-mask-' + id
    };
  }),
      _useState2 = slicedToArray(_useState, 1),
      _useState2$ = _useState2[0],
      path = _useState2$.path,
      gradient = _useState2$.gradient,
      mask = _useState2$.mask;

  var pathD = useMemo(function () {
    return reverse ? 'M 2.5, 2.5 L 2.5, ' + (height - 2.5) + ' L ' + (width - 2.5) + ', ' + (height - 2.5) + ' L ' + (width - 2.5) + ', 2.5 L 2.5, 2.5' : 'M2.5, 2.5 L' + (width - 2.5) + ', 2.5 L' + (width - 2.5) + ', ' + (height - 2.5) + ' L2.5, ' + (height - 2.5) + ' L2.5, 2.5';
  }, [width, height, reverse]);

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var length = useMemo(function () {
    return (width + height - 5) * 2;
  }, [width, height]);

  var classNames = useMemo(function () {
    return classnames('dv-border-box-8', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React.createElement(
        'defs',
        null,
        React.createElement('path', { id: path, d: pathD, fill: 'transparent' }),
        React.createElement(
          'radialGradient',
          { id: gradient, cx: '50%', cy: '50%', r: '50%' },
          React.createElement('stop', { offset: '0%', stopColor: '#fff', stopOpacity: '1' }),
          React.createElement('stop', { offset: '100%', stopColor: '#fff', stopOpacity: '0' })
        ),
        React.createElement(
          'mask',
          { id: mask },
          React.createElement(
            'circle',
            { cx: '0', cy: '0', r: '150', fill: 'url(#' + gradient + ')' },
            React.createElement('animateMotion', { dur: dur + 's', path: pathD, rotate: 'auto', repeatCount: 'indefinite' })
          )
        )
      ),
      React.createElement('polygon', { fill: backgroundColor, points: '5, 5 ' + (width - 5) + ', 5 ' + (width - 5) + ' ' + (height - 5) + ' 5, ' + (height - 5) }),
      React.createElement('use', { stroke: mergedColor[0], strokeWidth: '1', href: '#' + path }),
      React.createElement(
        'use',
        {
          stroke: mergedColor[1],
          strokeWidth: '3',
          href: '#' + path,
          mask: 'url(#' + mask + ')'
        },
        React.createElement('animate', {
          attributeName: 'stroke-dasharray',
          from: '0, ' + length,
          to: length + ', 0',
          dur: dur + 's',
          repeatCount: 'indefinite'
        })
      )
    ),
    React.createElement(
      'div',
      { className: 'border-box-content' },
      children
    )
  );
});

BorderBox.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object,
  color: PropTypes.array,
  dur: PropTypes.number,
  backgroundColor: PropTypes.string,
  reverse: PropTypes.bool
};

export default BorderBox;
//# sourceMappingURL=index.js.map
