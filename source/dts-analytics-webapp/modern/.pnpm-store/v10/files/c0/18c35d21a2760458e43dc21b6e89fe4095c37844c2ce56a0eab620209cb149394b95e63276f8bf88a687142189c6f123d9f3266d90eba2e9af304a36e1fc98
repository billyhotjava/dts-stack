import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-13 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-13 .dv-border-svg-container {\n  position: absolute;\n  width: 100%;\n  height: 100%;\n  top: 0px;\n  left: 0px;\n}\n.dv-border-box-13 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['#6586ec', '#2cf7fe'];

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
    return classnames('dv-border-box-13', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React.createElement('path', {
        fill: backgroundColor,
        stroke: mergedColor[0],
        d: '\n            M 5 20 L 5 10 L 12 3  L 60 3 L 68 10\n            L ' + (width - 20) + ' 10 L ' + (width - 5) + ' 25\n            L ' + (width - 5) + ' ' + (height - 5) + ' L 20 ' + (height - 5) + '\n            L 5 ' + (height - 20) + ' L 5 20\n          '
      }),
      React.createElement('path', {
        fill: 'transparent',
        strokeWidth: '3',
        strokeLinecap: 'round',
        strokeDasharray: '10, 5',
        stroke: mergedColor[0],
        d: 'M 16 9 L 61 9'
      }),
      React.createElement('path', {
        fill: 'transparent',
        stroke: mergedColor[1],
        d: 'M 5 20 L 5 10 L 12 3  L 60 3 L 68 10'
      }),
      React.createElement('path', {
        fill: 'transparent',
        stroke: mergedColor[1],
        d: 'M ' + (width - 5) + ' ' + (height - 30) + ' L ' + (width - 5) + ' ' + (height - 5) + ' L ' + (width - 30) + ' ' + (height - 5)
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
