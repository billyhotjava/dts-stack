import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-decoration-4 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-decoration-4 .container {\n  display: flex;\n  overflow: hidden;\n  position: absolute;\n  flex: 1;\n}\n.dv-decoration-4 .normal {\n  animation: ani-height ease-in-out infinite;\n  left: 50%;\n  margin-left: -2px;\n}\n.dv-decoration-4 .reverse {\n  animation: ani-width ease-in-out infinite;\n  top: 50%;\n  margin-top: -2px;\n}\n@keyframes ani-height {\n  0% {\n    height: 0%;\n  }\n  70% {\n    height: 100%;\n  }\n  100% {\n    height: 100%;\n  }\n}\n@keyframes ani-width {\n  0% {\n    width: 0%;\n  }\n  70% {\n    width: 100%;\n  }\n  100% {\n    width: 100%;\n  }\n}\n";
styleInject(css);

var defaultColor = ['rgba(255, 255, 255, 0.3)', 'rgba(255, 255, 255, 0.3)'];

var Decoration = forwardRef(function (_ref, ref) {
  var _ref$reverse = _ref.reverse,
      reverse = _ref$reverse === undefined ? false : _ref$reverse,
      _ref$dur = _ref.dur,
      dur = _ref$dur === undefined ? 3 : _ref$dur,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = useMemo(function () {
    return classnames('dv-decoration-4', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'div',
      {
        className: 'container ' + (reverse ? 'reverse' : 'normal'),
        style: reverse ? { width: width + 'px', height: '5px', animationDuration: dur + 's' } : { width: '5px', height: height + 'px', animationDuration: dur + 's' }
      },
      React.createElement(
        'svg',
        { width: reverse ? width : 5, height: reverse ? 5 : height },
        React.createElement('polyline', {
          stroke: mergedColor[0],
          points: reverse ? '0, 2.5 ' + width + ', 2.5' : '2.5, 0 2.5, ' + height
        }),
        React.createElement('polyline', {
          className: 'bold-line',
          stroke: mergedColor[1],
          strokeWidth: '3',
          strokeDasharray: '20, 80',
          strokeDashoffset: '-30',
          points: reverse ? '0, 2.5 ' + width + ', 2.5' : '2.5, 0 2.5, ' + height
        })
      )
    )
  );
});

Decoration.propTypes = {
  dur: PropTypes.number,
  reverse: PropTypes.bool,
  className: PropTypes.string,
  style: PropTypes.object,
  color: PropTypes.array

  // 指定 props 的默认值：
};Decoration.defaultProps = {
  reverse: false,
  dur: 3
};

export default Decoration;
//# sourceMappingURL=index.js.map
