import React, { forwardRef, useRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import { u as uuid } from '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';
import { l as lib_9 } from '../index-bf9f069c.js';

var css = ".dv-border-box-12 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-12 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  top: 0px;\n  left: 0px;\n}\n.dv-border-box-12 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['#2e6099', '#7ce7fd'];

var BorderBox = forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$backgroundColor = _ref.backgroundColor,
      backgroundColor = _ref$backgroundColor === undefined ? 'transparent' : _ref$backgroundColor;

  var filterId = useRef('border-box-12-filterId-' + uuid()).current;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = useMemo(function () {
    return classnames('dv-border-box-12', className);
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
          'filter',
          { id: filterId, height: '150%', width: '150%', x: '-25%', y: '-25%' },
          React.createElement('feMorphology', { operator: 'dilate', radius: '1', 'in': 'SourceAlpha', result: 'thicken' }),
          React.createElement('feGaussianBlur', { 'in': 'thicken', stdDeviation: '2', result: 'blurred' }),
          React.createElement(
            'feFlood',
            { floodColor: lib_9(mergedColor[1] || defaultColor[1], 70), result: 'glowColor' },
            React.createElement('animate', {
              attributeName: 'flood-color',
              values: '\n                ' + lib_9(mergedColor[1] || defaultColor[1], 70) + ';\n                ' + lib_9(mergedColor[1] || defaultColor[1], 30) + ';\n                ' + lib_9(mergedColor[1] || defaultColor[1], 70) + ';\n              ',
              dur: '3s',
              begin: '0s',
              repeatCount: 'indefinite'
            })
          ),
          React.createElement('feComposite', { 'in': 'glowColor', in2: 'blurred', operator: 'in', result: 'softGlowColored' }),
          React.createElement(
            'feMerge',
            null,
            React.createElement('feMergeNode', { 'in': 'softGlowColored' }),
            React.createElement('feMergeNode', { 'in': 'SourceGraphic' })
          )
        )
      ),
      width && height && React.createElement('path', {
        fill: backgroundColor,
        strokeWidth: '2',
        stroke: mergedColor[0],
        d: '\n            M15 5 L ' + (width - 15) + ' 5 Q ' + (width - 5) + ' 5, ' + (width - 5) + ' 15\n            L ' + (width - 5) + ' ' + (height - 15) + ' Q ' + (width - 5) + ' ' + (height - 5) + ', ' + (width - 15) + ' ' + (height - 5) + '\n            L 15, ' + (height - 5) + ' Q 5 ' + (height - 5) + ' 5 ' + (height - 15) + ' L 5 15\n            Q 5 5 15 5\n          '
      }),
      React.createElement('path', {
        strokeWidth: '2',
        fill: 'transparent',
        strokeLinecap: 'round',
        filter: 'url(#' + filterId + ')',
        stroke: mergedColor[1],
        d: 'M 20 5 L 15 5 Q 5 5 5 15 L 5 20'
      }),
      React.createElement('path', {
        strokeWidth: '2',
        fill: 'transparent',
        strokeLinecap: 'round',
        filter: 'url(#' + filterId + ')',
        stroke: mergedColor[1],
        d: 'M ' + (width - 20) + ' 5 L ' + (width - 15) + ' 5 Q ' + (width - 5) + ' 5 ' + (width - 5) + ' 15 L ' + (width - 5) + ' 20'
      }),
      React.createElement('path', {
        strokeWidth: '2',
        fill: 'transparent',
        strokeLinecap: 'round',
        filter: 'url(#' + filterId + ')',
        stroke: mergedColor[1],
        d: '\n          M ' + (width - 20) + ' ' + (height - 5) + ' L ' + (width - 15) + ' ' + (height - 5) + '\n          Q ' + (width - 5) + ' ' + (height - 5) + ' ' + (width - 5) + ' ' + (height - 15) + '\n          L ' + (width - 5) + ' ' + (height - 20) + '\n        '
      }),
      React.createElement('path', {
        strokeWidth: '2',
        fill: 'transparent',
        strokeLinecap: 'round',
        filter: 'url(#' + filterId + ')',
        stroke: mergedColor[1],
        d: '\n          M 20 ' + (height - 5) + ' L 15 ' + (height - 5) + '\n          Q 5 ' + (height - 5) + ' 5 ' + (height - 15) + '\n          L 5 ' + (height - 20) + '\n        '
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
