import React, { forwardRef, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-border-box-1 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-border-box-1 .border {\n  position: absolute;\n  display: block;\n}\n.dv-border-box-1 .right-top {\n  right: 0px;\n  transform: rotateY(180deg);\n}\n.dv-border-box-1 .left-bottom {\n  bottom: 0px;\n  transform: rotateX(180deg);\n}\n.dv-border-box-1 .right-bottom {\n  right: 0px;\n  bottom: 0px;\n  transform: rotateX(180deg) rotateY(180deg);\n}\n.dv-border-box-1 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var border = ['left-top', 'right-top', 'left-bottom', 'right-bottom'];
var defaultColor = ['#4fd2dd', '#235fa7'];

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
    return classnames('dv-border-box-1', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { className: 'border', width: width, height: height },
      React.createElement('polygon', { fill: backgroundColor, points: '10, 27 10, ' + (height - 27) + ' 13, ' + (height - 24) + ' 13, ' + (height - 21) + ' 24, ' + (height - 11) + '\n        38, ' + (height - 11) + ' 41, ' + (height - 8) + ' 73, ' + (height - 8) + ' 75, ' + (height - 10) + ' 81, ' + (height - 10) + '\n        85, ' + (height - 6) + ' ' + (width - 85) + ', ' + (height - 6) + ' ' + (width - 81) + ', ' + (height - 10) + ' ' + (width - 75) + ', ' + (height - 10) + '\n        ' + (width - 73) + ', ' + (height - 8) + ' ' + (width - 41) + ', ' + (height - 8) + ' ' + (width - 38) + ', ' + (height - 11) + '\n        ' + (width - 24) + ', ' + (height - 11) + ' ' + (width - 13) + ', ' + (height - 21) + ' ' + (width - 13) + ', ' + (height - 24) + '\n        ' + (width - 10) + ', ' + (height - 27) + ' ' + (width - 10) + ', 27 ' + (width - 13) + ', 25 ' + (width - 13) + ', 21\n        ' + (width - 24) + ', 11 ' + (width - 38) + ', 11 ' + (width - 41) + ', 8 ' + (width - 73) + ', 8 ' + (width - 75) + ', 10\n        ' + (width - 81) + ', 10 ' + (width - 85) + ', 6 85, 6 81, 10 75, 10 73, 8 41, 8 38, 11 24, 11 13, 21 13, 24' })
    ),
    border.map(function (borderName) {
      return React.createElement(
        'svg',
        {
          width: '150px',
          height: '150px',
          key: borderName,
          className: borderName + ' border'
        },
        React.createElement(
          'polygon',
          {
            fill: mergedColor[0],
            points: '6,66 6,18 12,12 18,12 24,6 27,6 30,9 36,9 39,6 84,6 81,9 75,9 73.2,7 40.8,7 37.8,10.2 24,10.2 12,21 12,24 9,27 9,51 7.8,54 7.8,63'
          },
          React.createElement('animate', {
            attributeName: 'fill',
            values: mergedColor[0] + ';' + mergedColor[1] + ';' + mergedColor[0],
            dur: '0.5s',
            begin: '0s',
            repeatCount: 'indefinite'
          })
        ),
        React.createElement(
          'polygon',
          {
            fill: mergedColor[1],
            points: '27.599999999999998,4.8 38.4,4.8 35.4,7.8 30.599999999999998,7.8'
          },
          React.createElement('animate', {
            attributeName: 'fill',
            values: mergedColor[1] + ';' + mergedColor[0] + ';' + mergedColor[1],
            dur: '0.5s',
            begin: '0s',
            repeatCount: 'indefinite'
          })
        ),
        React.createElement(
          'polygon',
          {
            fill: mergedColor[0],
            points: '9,54 9,63 7.199999999999999,66 7.199999999999999,75 7.8,78 7.8,110 8.4,110 8.4,66 9.6,66 9.6,54'
          },
          React.createElement('animate', {
            attributeName: 'fill',
            values: mergedColor[0] + ';' + mergedColor[1] + ';transparent',
            dur: '1s',
            begin: '0s',
            repeatCount: 'indefinite'
          })
        )
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
