import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import { _ as _extends } from '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-7 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-7 .dv-border-svg-container {\n  position: absolute;\n  top: 0px;\n  left: 0px;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-7 .dv-border-svg-container > polyline {\n  fill: none;\n  stroke-linecap: round;\n}\n.dv-border-box-7 .dv-bb7-line-width-2 {\n  stroke-width: 2;\n}\n.dv-border-box-7 .dv-bb7-line-width-5 {\n  stroke-width: 5;\n}\n.dv-border-box-7 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var defaultColor = ['rgba(128,128,128,0.3)', 'rgba(128,128,128,0.5)'];

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
    return classnames('dv-border-box-7', className);
  }, [className]);

  var styles = useMemo(function () {
    return _extends({
      boxShadow: 'inset 0 0 40px ' + mergedColor[0],
      border: '1px solid ' + mergedColor[0],
      backgroundColor: backgroundColor
    }, style);
  }, [style, mergedColor, backgroundColor]);

  return React.createElement(
    'div',
    { className: classNames, style: styles, ref: domRef },
    React.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React.createElement('polyline', { className: 'dv-bb7-line-width-2', stroke: mergedColor[0], points: '0, 25 0, 0 25, 0' }),
      React.createElement('polyline', {
        className: 'dv-bb7-line-width-2',
        stroke: mergedColor[0],
        points: width - 25 + ', 0 ' + width + ', 0 ' + width + ', 25'
      }),
      React.createElement('polyline', {
        className: 'dv-bb7-line-width-2',
        stroke: mergedColor[0],
        points: width - 25 + ', ' + height + ' ' + width + ', ' + height + ' ' + width + ', ' + (height - 25)
      }),
      React.createElement('polyline', {
        className: 'dv-bb7-line-width-2',
        stroke: mergedColor[0],
        points: '0, ' + (height - 25) + ' 0, ' + height + ' 25, ' + height
      }),
      React.createElement('polyline', { className: 'dv-bb7-line-width-5', stroke: mergedColor[1], points: '0, 10 0, 0 10, 0' }),
      React.createElement('polyline', {
        className: 'dv-bb7-line-width-5',
        stroke: mergedColor[1],
        points: width - 10 + ', 0 ' + width + ', 0 ' + width + ', 10'
      }),
      React.createElement('polyline', {
        className: 'dv-bb7-line-width-5',
        stroke: mergedColor[1],
        points: width - 10 + ', ' + height + ' ' + width + ', ' + height + ' ' + width + ', ' + (height - 10)
      }),
      React.createElement('polyline', {
        className: 'dv-bb7-line-width-5',
        stroke: mergedColor[1],
        points: '0, ' + (height - 10) + ' 0, ' + height + ' 10, ' + height
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
