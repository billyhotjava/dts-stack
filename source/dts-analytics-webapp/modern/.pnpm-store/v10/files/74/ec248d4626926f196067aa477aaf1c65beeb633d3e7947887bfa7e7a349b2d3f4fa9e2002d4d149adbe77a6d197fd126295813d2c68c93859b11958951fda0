'use strict';

function _interopDefault (ex) { return (ex && (typeof ex === 'object') && 'default' in ex) ? ex['default'] : ex; }

var React = require('react');
var React__default = _interopDefault(React);
var styleInject_es = require('../style-inject.es-55d78dad.js');
require('../index-92c36402.js');
var _babelHelpers = require('../_babelHelpers-1c35d3ad.js');
var autoResize = require('../autoResize-4af58baf.js');
var index$1 = require('../index-4a59a5ac.js');
var index$2 = require('../index-8aeda36a.js');

var css = ".dv-border-box-10 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n  border-radius: 6px;\n}\n.dv-border-box-10 .dv-border-svg-container {\n  position: absolute;\n  display: block;\n}\n.dv-border-box-10 .right-top {\n  right: 0px;\n  transform: rotateY(180deg);\n}\n.dv-border-box-10 .left-bottom {\n  bottom: 0px;\n  transform: rotateX(180deg);\n}\n.dv-border-box-10 .right-bottom {\n  right: 0px;\n  bottom: 0px;\n  transform: rotateX(180deg) rotateY(180deg);\n}\n.dv-border-box-10 .border-box-content {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n";
styleInject_es.styleInject(css);

var border = ['left-top', 'right-top', 'left-bottom', 'right-bottom'];
var defaultColor = ['#1d48c4', '#d3e1f8'];

var BorderBox = React.forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$backgroundColor = _ref.backgroundColor,
      backgroundColor = _ref$backgroundColor === undefined ? 'transparent' : _ref$backgroundColor;

  var _useAutoResize = autoResize.useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var mergedColor = React.useMemo(function () {
    return index$2.util_2(index$2.util_1(defaultColor, true), color || []);
  }, [color]);

  var classNames = React.useMemo(function () {
    return index$1.classnames('dv-border-box-10', className);
  }, [className]);

  var styles = React.useMemo(function () {
    return _babelHelpers._extends({
      boxShadow: 'inset 0 0 25px 3px ' + mergedColor[0]
    }, style);
  }, [style, mergedColor]);

  return React__default.createElement(
    'div',
    { className: classNames, style: styles, ref: domRef },
    React__default.createElement(
      'svg',
      { className: 'dv-border-svg-container', width: width, height: height },
      React__default.createElement('polygon', { fill: backgroundColor, points: '\n          4, 0 ' + (width - 4) + ', 0 ' + width + ', 4 ' + width + ', ' + (height - 4) + ' ' + (width - 4) + ', ' + height + '\n          4, ' + height + ' 0, ' + (height - 4) + ' 0, 4\n        ' })
    ),
    border.map(function (borderName) {
      return React__default.createElement(
        'svg',
        {
          width: '150px',
          height: '150px',
          key: borderName,
          className: borderName + ' dv-border-svg-container'
        },
        React__default.createElement('polygon', {
          fill: mergedColor[1],
          points: '40, 0 5, 0 0, 5 0, 16 3, 19 3, 7 7, 3 35, 3'
        })
      );
    }),
    React__default.createElement(
      'div',
      { className: 'border-box-content' },
      children
    )
  );
});

BorderBox.propTypes = {
  children: styleInject_es.PropTypes.node,
  className: styleInject_es.PropTypes.string,
  style: styleInject_es.PropTypes.object,
  color: styleInject_es.PropTypes.array,
  backgroundColor: styleInject_es.PropTypes.string
};

module.exports = BorderBox;
//# sourceMappingURL=index.js.map
