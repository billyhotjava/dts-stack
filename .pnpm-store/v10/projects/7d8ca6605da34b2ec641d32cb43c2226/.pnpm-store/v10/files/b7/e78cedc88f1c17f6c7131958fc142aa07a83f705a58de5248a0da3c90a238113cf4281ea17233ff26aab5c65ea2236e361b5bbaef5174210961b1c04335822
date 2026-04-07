import React, { forwardRef, useState, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import { u as uuid } from '../index-65ee9985.js';
import { s as slicedToArray } from '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-9 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-9 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  left: 0px;\n  top: 0px;\n}\n.dv-border-box-9 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['#11eefd', '#0078d2'];

var BorderBox = forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$backgroundColor = _ref.backgroundColor,
      backgroundColor = _ref$backgroundColor === undefined ? 'transparent' : _ref$backgroundColor;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var _useState = useState(function () {
    var id = uuid();

    return {
      gradientId: 'border-box-9-gradient-' + id,
      maskId: 'border-box-9-mask-' + id
    };
  }),
      _useState2 = slicedToArray(_useState, 1),
      _useState2$ = _useState2[0],
      gradientId = _useState2$.gradientId,
      maskId = _useState2$.maskId;

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = useMemo(function () {
    return classnames('dv-border-box-9', className);
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
        React.createElement(
          'linearGradient',
          { id: gradientId, x1: '0%', y1: '0%', x2: '100%', y2: '100%' },
          React.createElement('animate', {
            attributeName: 'x1',
            values: '0%;100%;0%',
            dur: '10s',
            begin: '0s',
            repeatCount: 'indefinite'
          }),
          React.createElement('animate', {
            attributeName: 'x2',
            values: '100%;0%;100%',
            dur: '10s',
            begin: '0s',
            repeatCount: 'indefinite'
          }),
          React.createElement(
            'stop',
            { offset: '0%', stopColor: mergedColor[0] },
            React.createElement('animate', {
              attributeName: 'stop-color',
              values: mergedColor[0] + ';' + mergedColor[1] + ';' + mergedColor[0],
              dur: '10s',
              begin: '0s',
              repeatCount: 'indefinite'
            })
          ),
          React.createElement(
            'stop',
            { offset: '100%', stopColor: mergedColor[1] },
            React.createElement('animate', {
              attributeName: 'stop-color',
              values: mergedColor[1] + ';' + mergedColor[0] + ';' + mergedColor[1],
              dur: '10s',
              begin: '0s',
              repeatCount: 'indefinite'
            })
          )
        ),
        React.createElement(
          'mask',
          { id: maskId },
          React.createElement('polyline', {
            stroke: '#fff',
            strokeWidth: '3',
            fill: 'transparent',
            points: '8, ' + height * 0.4 + ' 8, 3, ' + (width * 0.4 + 7) + ', 3'
          }),
          React.createElement('polyline', {
            fill: '#fff',
            points: '8, ' + height * 0.15 + ' 8, 3, ' + (width * 0.1 + 7) + ', 3\n                ' + width * 0.1 + ', 8 14, 8 14, ' + (height * 0.15 - 7) + '\n              '
          }),
          React.createElement('polyline', {
            stroke: '#fff',
            strokeWidth: '3',
            fill: 'transparent',
            points: width * 0.5 + ', 3 ' + (width - 3) + ', 3, ' + (width - 3) + ', ' + height * 0.25
          }),
          React.createElement('polyline', {
            fill: '#fff',
            points: '\n                ' + width * 0.52 + ', 3 ' + width * 0.58 + ', 3\n                ' + (width * 0.58 - 7) + ', 9 ' + (width * 0.52 + 7) + ', 9\n              '
          }),
          React.createElement('polyline', {
            fill: '#fff',
            points: '\n                ' + width * 0.9 + ', 3 ' + (width - 3) + ', 3 ' + (width - 3) + ', ' + height * 0.1 + '\n                ' + (width - 9) + ', ' + (height * 0.1 - 7) + ' ' + (width - 9) + ', 9 ' + (width * 0.9 + 7) + ', 9\n              '
          }),
          React.createElement('polyline', {
            stroke: '#fff',
            strokeWidth: '3',
            fill: 'transparent',
            points: '8, ' + height * 0.5 + ' 8, ' + (height - 3) + ' ' + (width * 0.3 + 7) + ', ' + (height - 3)
          }),
          React.createElement('polyline', {
            fill: '#fff',
            points: '\n                8, ' + height * 0.55 + ' 8, ' + height * 0.7 + '\n                2, ' + (height * 0.7 - 7) + ' 2, ' + (height * 0.55 + 7) + '\n              '
          }),
          React.createElement('polyline', {
            stroke: '#fff',
            strokeWidth: '3',
            fill: 'transparent',
            points: width * 0.35 + ', ' + (height - 3) + ' ' + (width - 3) + ', ' + (height - 3) + ' ' + (width - 3) + ', ' + height * 0.35
          }),
          React.createElement('polyline', {
            fill: '#fff',
            points: '\n                ' + width * 0.92 + ', ' + (height - 3) + ' ' + (width - 3) + ', ' + (height - 3) + ' ' + (width - 3) + ', ' + height * 0.8 + '\n                ' + (width - 9) + ', ' + (height * 0.8 + 7) + ' ' + (width - 9) + ', ' + (height - 9) + ' ' + (width * 0.92 + 7) + ', ' + (height - 9) + '\n              '
          })
        )
      ),
      React.createElement('polygon', { fill: backgroundColor, points: '\n          15, 9 ' + (width * 0.1 + 1) + ', 9 ' + (width * 0.1 + 4) + ', 6 ' + (width * 0.52 + 2) + ', 6\n          ' + (width * 0.52 + 6) + ', 10 ' + (width * 0.58 - 7) + ', 10 ' + (width * 0.58 - 2) + ', 6\n          ' + (width * 0.9 + 2) + ', 6 ' + (width * 0.9 + 6) + ', 10 ' + (width - 10) + ', 10 ' + (width - 10) + ', ' + (height * 0.1 - 6) + '\n          ' + (width - 6) + ', ' + (height * 0.1 - 1) + ' ' + (width - 6) + ', ' + (height * 0.8 + 1) + ' ' + (width - 10) + ', ' + (height * 0.8 + 6) + '\n          ' + (width - 10) + ', ' + (height - 10) + ' ' + (width * 0.92 + 7) + ', ' + (height - 10) + '  ' + (width * 0.92 + 2) + ', ' + (height - 6) + '\n          11, ' + (height - 6) + ' 11, ' + (height * 0.15 - 2) + ' 15, ' + (height * 0.15 - 7) + '\n        ' }),
      React.createElement('rect', {
        x: '0',
        y: '0',
        width: width,
        height: height,
        fill: 'url(#' + gradientId + ')',
        mask: 'url(#' + maskId + ')'
      })
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
  backgroundColor: PropTypes.string
};

export default BorderBox;
//# sourceMappingURL=index.js.map
