import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import { _ as _extends } from '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-10 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n  border-radius: 6px;\n}\n.dv-border-box-10 .dv-border-svg-container {\n  position: absolute;\n  display: block;\n}\n.dv-border-box-10 .right-top {\n  right: 0px;\n  transform: rotateY(180deg);\n}\n.dv-border-box-10 .left-bottom {\n  bottom: 0px;\n  transform: rotateX(180deg);\n}\n.dv-border-box-10 .right-bottom {\n  right: 0px;\n  bottom: 0px;\n  transform: rotateX(180deg) rotateY(180deg);\n}\n.dv-border-box-10 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var border = ['left-top', 'right-top', 'left-bottom', 'right-bottom'];
var defaultColor = ['#1d48c4', '#d3e1f8'];

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
    return classnames('dv-border-box-10', className);
  }, [className]);

  var styles = useMemo(function () {
    return _extends({
      boxShadow: 'inset 0 0 25px 3px ' + mergedColor[0]
    }, style);
  }, [style, mergedColor]);

  return React.createElement(
    'div',
    { className: classNames, style: styles, ref: domRef },
    React.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React.createElement('polygon', { fill: backgroundColor, points: '\n          4, 0 ' + (width - 4) + ', 0 ' + width + ', 4 ' + width + ', ' + (height - 4) + ' ' + (width - 4) + ', ' + height + '\n          4, ' + height + ' 0, ' + (height - 4) + ' 0, 4\n        ' })
    ),
    border.map(function (borderName) {
      return React.createElement(
        'svg',
        {
          width: '150px',
          height: '150px',
          key: borderName,
          className: borderName + ' dv-border-svg-container'
        },
        React.createElement('polygon', {
          fill: mergedColor[1],
          points: '40, 0 5, 0 0, 5 0, 16 3, 19 3, 7 7, 3 35, 3'
        })
      );
    }),
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
