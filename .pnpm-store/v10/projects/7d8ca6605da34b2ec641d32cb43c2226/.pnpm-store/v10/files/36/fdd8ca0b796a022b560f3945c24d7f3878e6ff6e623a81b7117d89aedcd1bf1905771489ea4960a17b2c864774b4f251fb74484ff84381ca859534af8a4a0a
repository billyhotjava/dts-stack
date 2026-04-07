import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-6 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-6 .dv-border-svg-container {\n  position: absolute;\n  top: 0px;\n  left: 0px;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-6 .dv-border-svg-container > polyline {\n  fill: none;\n  stroke-width: 1;\n}\n.dv-border-box-6 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['rgba(255, 255, 255, 0.35)', 'gray'];

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

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = useMemo(function () {
    return classnames('dv-border-box-6', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React.createElement('polygon', { fill: backgroundColor, points: '\n          9, 7 ' + (width - 9) + ', 7 ' + (width - 9) + ', ' + (height - 7) + ' 9, ' + (height - 7) + '\n        ' }),
      React.createElement('circle', { fill: mergedColor[1], cx: '5', cy: '5', r: '2' }),
      React.createElement('circle', { fill: mergedColor[1], cx: width - 5, cy: '5', r: '2' }),
      React.createElement('circle', { fill: mergedColor[1], cx: width - 5, cy: height - 5, r: '2' }),
      React.createElement('circle', { fill: mergedColor[1], cx: '5', cy: height - 5, r: '2' }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '10, 4 ' + (width - 10) + ', 4' }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '10, ' + (height - 4) + ' ' + (width - 10) + ', ' + (height - 4) }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '5, 70 5, ' + (height - 70) }),
      React.createElement('polyline', { stroke: mergedColor[0], points: width - 5 + ', 70 ' + (width - 5) + ', ' + (height - 70) }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '3, 10, 3, 50' }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '7, 30 7, 80' }),
      React.createElement('polyline', { stroke: mergedColor[0], points: width - 3 + ', 10 ' + (width - 3) + ', 50' }),
      React.createElement('polyline', { stroke: mergedColor[0], points: width - 7 + ', 30 ' + (width - 7) + ', 80' }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '3, ' + (height - 10) + ' 3, ' + (height - 50) }),
      React.createElement('polyline', { stroke: mergedColor[0], points: '7, ' + (height - 30) + ' 7, ' + (height - 80) }),
      React.createElement('polyline', {
        stroke: mergedColor[0],
        points: width - 3 + ', ' + (height - 10) + ' ' + (width - 3) + ', ' + (height - 50)
      }),
      React.createElement('polyline', {
        stroke: mergedColor[0],
        points: width - 7 + ', ' + (height - 30) + ' ' + (width - 7) + ', ' + (height - 80)
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
