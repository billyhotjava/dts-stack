import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-3 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-3 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  top: 0px;\n  left: 0px;\n}\n.dv-border-box-3 .dv-border-svg-container > polyline {\n  fill: none;\n}\n.dv-border-box-3 .dv-bb3-line1 {\n  stroke-width: 3;\n}\n.dv-border-box-3 .dv-bb3-line2 {\n  stroke-width: 1;\n}\n.dv-border-box-3 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['#2862b7', '#2862b7'];

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
    return classnames('dv-border-box-3', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React.createElement('polygon', { fill: backgroundColor, points: '\n          23, 23 ' + (width - 24) + ', 23 ' + (width - 24) + ', ' + (height - 24) + ' 23, ' + (height - 24) + '\n        ' }),
      React.createElement('polyline', {
        className: 'dv-bb3-line1',
        stroke: mergedColor[0],
        points: '4, 4 ' + (width - 22) + ' ,4 ' + (width - 22) + ', ' + (height - 22) + ' 4, ' + (height - 22) + ' 4, 4'
      }),
      React.createElement('polyline', {
        className: 'dv-bb3-line2',
        stroke: mergedColor[1],
        points: '10, 10 ' + (width - 16) + ', 10 ' + (width - 16) + ', ' + (height - 16) + ' 10, ' + (height - 16) + ' 10, 10'
      }),
      React.createElement('polyline', {
        className: 'dv-bb3-line2',
        stroke: mergedColor[1],
        points: '16, 16 ' + (width - 10) + ', 16 ' + (width - 10) + ', ' + (height - 10) + ' 16, ' + (height - 10) + ' 16, 16'
      }),
      React.createElement('polyline', {
        className: 'dv-bb3-line2',
        stroke: mergedColor[1],
        points: '22, 22 ' + (width - 4) + ', 22 ' + (width - 4) + ', ' + (height - 4) + ' 22, ' + (height - 4) + ' 22, 22'
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
