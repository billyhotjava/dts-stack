import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import { s as slicedToArray } from '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-decoration-8 {\n  display: flex;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['#3f96a5', '#3f96a5'];

var Decoration = forwardRef(function (_ref, ref) {
  var _ref$reverse = _ref.reverse,
      reverse = _ref$reverse === undefined ? false : _ref$reverse,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var xPos = function xPos(pos) {
    return !reverse ? pos : width - pos;
  };

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var _useMemo = useMemo(function () {
    return [xPos(0) + ', 0 ' + xPos(30) + ', ' + height / 2, xPos(20) + ', 0 ' + xPos(50) + ', ' + height / 2 + ' ' + xPos(width) + ', ' + height / 2, xPos(0) + ', ' + (height - 3) + ', ' + xPos(200) + ', ' + (height - 3)];
  }, [reverse, width, height]),
      _useMemo2 = slicedToArray(_useMemo, 3),
      pointsOne = _useMemo2[0],
      pointsTwo = _useMemo2[1],
      pointsThree = _useMemo2[2];

  var classNames = useMemo(function () {
    return classnames('dv-decoration-8', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { width: width, height: height },
      React.createElement('polyline', {
        stroke: mergedColor[0],
        strokeWidth: '2',
        fill: 'transparent',
        points: pointsOne
      }),
      React.createElement('polyline', {
        stroke: mergedColor[0],
        strokeWidth: '2',
        fill: 'transparent',
        points: pointsTwo
      }),
      React.createElement('polyline', {
        stroke: mergedColor[1],
        fill: 'transparent',
        strokeWidth: '3',
        points: pointsThree
      })
    )
  );
});

Decoration.propTypes = {
  reverse: PropTypes.bool,
  className: PropTypes.string,
  style: PropTypes.object,
  color: PropTypes.array

  // 指定 props 的默认值：
};Decoration.defaultProps = {
  reverse: false
};

export default Decoration;
//# sourceMappingURL=index.js.map
