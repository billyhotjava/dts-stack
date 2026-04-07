import React, { forwardRef, useRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import { u as uuid } from '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-decoration-10 {\n  width: 100%;\n  height: 100%;\n  display: flex;\n}\n";
styleInject(css);

var defaultColor = ['#00c2ff', 'rgba(0, 194, 255, 0.3)'];

var Decoration = forwardRef(function (_ref, ref) {
  var className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var _useRef$current = useRef({
    animationId1: 'd10ani1' + uuid(),
    animationId2: 'd10ani2' + uuid(),
    animationId3: 'd10ani3' + uuid(),
    animationId4: 'd10ani4' + uuid(),
    animationId5: 'd10ani5' + uuid(),
    animationId6: 'd10ani6' + uuid(),
    animationId7: 'd10ani7' + uuid()
  }).current,
      animationId1 = _useRef$current.animationId1,
      animationId2 = _useRef$current.animationId2,
      animationId3 = _useRef$current.animationId3,
      animationId4 = _useRef$current.animationId4,
      animationId5 = _useRef$current.animationId5,
      animationId6 = _useRef$current.animationId6,
      animationId7 = _useRef$current.animationId7;


  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = useMemo(function () {
    return classnames('dv-decoration-10', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { width: width, height: height },
      React.createElement('polyline', {
        stroke: mergedColor[1],
        strokeWidth: '2',
        points: '0, ' + height / 2 + ' ' + width + ', ' + height / 2
      }),
      React.createElement(
        'polyline',
        {
          stroke: mergedColor[0],
          strokeWidth: '2',
          points: '5, ' + height / 2 + ' ' + (width * 0.2 - 3) + ', ' + height / 2,
          strokeDasharray: '0, ' + width * 0.2,
          fill: 'freeze'
        },
        React.createElement('animate', {
          id: animationId2,
          attributeName: 'stroke-dasharray',
          values: '0, ' + width * 0.2 + ';' + width * 0.2 + ', 0;',
          dur: '3s',
          begin: animationId1 + '.end',
          fill: 'freeze'
        }),
        React.createElement('animate', {
          attributeName: 'stroke-dasharray',
          values: width * 0.2 + ', 0;0, ' + width * 0.2,
          dur: '0.01s',
          begin: animationId7 + '.end',
          fill: 'freeze'
        })
      ),
      React.createElement(
        'polyline',
        {
          stroke: mergedColor[0],
          strokeWidth: '2',
          points: width * 0.2 + 3 + ', ' + height / 2 + ' ' + (width * 0.8 - 3) + ', ' + height / 2,
          strokeDasharray: '0, ' + width * 0.6
        },
        React.createElement('animate', {
          id: animationId4,
          attributeName: 'stroke-dasharray',
          values: '0, ' + width * 0.6 + ';' + width * 0.6 + ', 0',
          dur: '3s',
          begin: animationId3 + '.end + 1s',
          fill: 'freeze'
        }),
        React.createElement('animate', {
          attributeName: 'stroke-dasharray',
          values: width * 0.6 + ', 0;0, ' + width * 0.6,
          dur: '0.01s',
          begin: animationId7 + '.end',
          fill: 'freeze'
        })
      ),
      React.createElement(
        'polyline',
        {
          stroke: mergedColor[0],
          strokeWidth: '2',
          points: width * 0.8 + 3 + ', ' + height / 2 + ' ' + (width - 5) + ', ' + height / 2,
          'strke-dasharray': '0, ' + width * 0.2
        },
        React.createElement('animate', {
          id: animationId6,
          attributeName: 'stroke-dasharray',
          values: '0, ' + width * 0.2 + ';' + width * 0.2 + ', 0',
          dur: '3s',
          begin: animationId5 + '.end + 1s',
          fill: 'freeze'
        }),
        React.createElement('animate', {
          attributeName: 'stroke-dasharray',
          values: width * 0.2 + ', 0;0, ' + width * 0.3,
          dur: '0.01s',
          begin: animationId7 + '.end',
          fill: 'freeze'
        })
      ),
      React.createElement(
        'circle',
        { cx: '2', cy: height / 2, r: '2', fill: mergedColor[1] },
        React.createElement('animate', {
          id: animationId1,
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[0],
          begin: '0s;' + animationId7 + '.end',
          dur: '0.3s',
          fill: 'freeze'
        })
      ),
      React.createElement(
        'circle',
        { cx: width * 0.2, cy: height / 2, r: '2', fill: mergedColor[1] },
        React.createElement('animate', {
          id: animationId3,
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[0],
          begin: animationId2 + '.end',
          dur: '0.3s',
          fill: 'freeze'
        }),
        React.createElement('animate', {
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[1],
          dur: '0.01s',
          begin: animationId7 + '.end',
          fill: 'freeze'
        })
      ),
      React.createElement(
        'circle',
        { cx: width * 0.8, cy: height / 2, r: '2', fill: mergedColor[1] },
        React.createElement('animate', {
          id: animationId5,
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[0],
          begin: animationId4 + '.end',
          dur: '0.3s',
          fill: 'freeze'
        }),
        React.createElement('animate', {
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[1],
          dur: '0.01s',
          begin: animationId7 + '.end',
          fill: 'freeze'
        })
      ),
      React.createElement(
        'circle',
        { cx: width - 2, cy: height / 2, r: '2', fill: mergedColor[1] },
        React.createElement('animate', {
          id: animationId7,
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[0],
          begin: animationId6 + '.end',
          dur: '0.3s',
          fill: 'freeze'
        }),
        React.createElement('animate', {
          attributeName: 'fill',
          values: mergedColor[1] + ';' + mergedColor[1],
          dur: '0.01s',
          begin: animationId7 + '.end',
          fill: 'freeze'
        })
      )
    )
  );
});

Decoration.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  color: PropTypes.array
};

export default Decoration;
//# sourceMappingURL=index.js.map
