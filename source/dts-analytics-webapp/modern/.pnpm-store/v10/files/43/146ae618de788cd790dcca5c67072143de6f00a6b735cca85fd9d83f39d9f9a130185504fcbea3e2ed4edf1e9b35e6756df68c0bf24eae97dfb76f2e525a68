'use strict';

function _interopDefault (ex) { return (ex && (typeof ex === 'object') && 'default' in ex) ? ex['default'] : ex; }

var React = require('react');
var React__default = _interopDefault(React);
var styleInject_es = require('../style-inject.es-55d78dad.js');
var index = require('../index-92c36402.js');
require('../_babelHelpers-1c35d3ad.js');
var autoResize = require('../autoResize-4af58baf.js');
var index$1 = require('../index-4a59a5ac.js');
var index$2 = require('../index-8aeda36a.js');
var index$3 = require('../index-19aa5843.js');

var css = ".dv-decoration-12 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n  display: flex;\n}\n.dv-decoration-12 .decoration-content {\n  position: absolute;\n  top: 0px;\n  left: 0px;\n  width: 100%;\n  height: 100%;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n";
styleInject_es.styleInject(css);

var defaultColor = ['#2783ce', '#2cf7fe'];

var segment = 30;

var sectorAngle = Math.PI / 3;

var ringNum = 3;

var ringWidth = 1;

var BorderBox = React.forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$scanDur = _ref.scanDur,
      scanDur = _ref$scanDur === undefined ? 3 : _ref$scanDur,
      _ref$haloDur = _ref.haloDur,
      haloDur = _ref$haloDur === undefined ? 2 : _ref$haloDur;

  var _useAutoResize = autoResize.useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var x = width / 2;

  var y = height / 2;

  var mergedColor = React.useMemo(function () {
    return index$2.util_2(index$2.util_1(defaultColor, true), color || []);
  }, [color]);

  var pathD = React.useMemo(function () {
    var startAngle = -Math.PI / 2;
    var angleGap = sectorAngle / segment;
    var r = width / 4;
    var lastEndPoints = index$2.util_13(x, y, r, startAngle);

    return new Array(segment).fill('').map(function (_, i) {
      var endPoints = index$2.util_13(x, y, r, startAngle - (i + 1) * angleGap).map(function (_) {
        return _.toFixed(5);
      });
      var d = 'M' + lastEndPoints.join(',') + ' A' + r + ', ' + r + ' 0 0 0 ' + endPoints.join(',');
      lastEndPoints = endPoints;
      return d;
    });
  }, [x, y, width]);

  var pathColor = React.useMemo(function () {
    var color = mergedColor[0];
    var colorGap = 100 / (segment - 1);

    return new Array(segment).fill(color).map(function (_, i) {
      return index$3.lib_9(color, 100 - i * colorGap);
    });
  }, [mergedColor]);

  var circleR = React.useMemo(function () {
    var radiusGap = (width / 2 - ringWidth / 2) / ringNum;

    return new Array(ringNum).fill(0).map(function (_, i) {
      return radiusGap * (i + 1);
    });
  }, [width]);

  var splitLinePoints = React.useMemo(function () {
    var angleGap = Math.PI / 6;
    var r = width / 2;
    return new Array(6).fill('').map(function (_, i) {
      var startAngle = angleGap * (i + 1);
      var endAngle = startAngle + Math.PI;
      var startPoint = index$2.util_13(x, y, r, startAngle);
      var endPoint = index$2.util_13(x, y, r, endAngle);
      return startPoint.join(',') + ' ' + endPoint.join(',');
    });
  }, [x, y, width]);

  var arcD = React.useMemo(function () {
    var angleGap = Math.PI / 6;
    var r = width / 2 - 1;

    return new Array(4).fill('').map(function (_, i) {
      var startAngle = angleGap * (3 * i + 1);
      var endAngle = startAngle + angleGap;
      var startPoint = index$2.util_13(x, y, r, startAngle);
      var endPoint = index$2.util_13(x, y, r, endAngle);
      return 'M' + startPoint.join(',') + ' A' + x + ', ' + y + ' 0 0 1 ' + endPoint.join(',');
    });
  }, [x, y, width]);

  var idRef = React.useRef({
    gId: 'decoration-12-g-' + index.uuid(),
    gradientId: 'decoration-12-gradient-' + index.uuid()
  });

  var classNames = React.useMemo(function () {
    return index$1.classnames('dv-decoration-12', className);
  }, [className]);

  return React__default.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React__default.createElement(
      'svg',
      { width: width, height: height },
      React__default.createElement(
        'defs',
        null,
        React__default.createElement(
          'g',
          { id: idRef.current.gId },
          pathD.map(function (d, i) {
            return React__default.createElement('path', {
              stroke: pathColor[i],
              strokeWidth: width / 2,
              fill: 'transparent',
              key: d,
              d: d
            });
          })
        ),
        React__default.createElement(
          'radialGradient',
          {
            id: idRef.current.gradientId,
            cx: '50%', cy: '50%', r: '50%'
          },
          React__default.createElement('stop', { offset: '0%', stopColor: 'transparent', stopOpacity: '1' }),
          React__default.createElement('stop', { offset: '100%', stopColor: index$3.lib_9(mergedColor[1] || defaultColor[1], 30), stopOpacity: '1' })
        )
      ),
      circleR.map(function (r) {
        return React__default.createElement('circle', {
          key: r,
          r: r,
          cx: x,
          cy: y,
          stroke: mergedColor[1],
          strokeWidth: 0.5,
          fill: 'transparent'
        });
      }),
      React__default.createElement(
        'circle',
        {
          r: '1',
          cx: x,
          cy: y,
          stroke: 'transparent',
          fill: 'url(#' + idRef.current.gradientId + ')'
        },
        React__default.createElement('animate', {
          attributeName: 'r',
          values: '1;' + width / 2,
          dur: haloDur + 's',
          repeatCount: 'indefinite'
        }),
        React__default.createElement('animate', {
          attributeName: 'opacity',
          values: '1;0',
          dur: haloDur + 's',
          repeatCount: 'indefinite'
        })
      ),
      React__default.createElement('circle', {
        r: '2',
        cx: x,
        cy: y,
        fill: mergedColor[1]
      }),
       React__default.createElement(
        'g',
        null,
        splitLinePoints.map(function (p) {
          return React__default.createElement('polyline', {
            key: p,
            points: p,
            stroke: mergedColor[1],
            strokeWidth: 0.5,
            opacity: '0.5'
          });
        })
      ),
      arcD.map(function (d) {
        return React__default.createElement('path', {
          key: d,
          d: d,
          stroke: mergedColor[1],
          strokeWidth: '2',
          fill: 'transparent'
        });
      }),
      React__default.createElement(
        'use',
        { href: '#' + idRef.current.gId },
        React__default.createElement('animateTransform', {
          attributeName: 'transform',
          type: 'rotate',
          values: '0, ' + x + ' ' + y + ';360, ' + x + ' ' + y,
          dur: scanDur + 's',
          repeatCount: 'indefinite'
        })
      )
    ),
    React__default.createElement(
      'div',
      { className: 'decoration-content' },
      children
    )
  );
});

BorderBox.propTypes = {
  children: styleInject_es.PropTypes.node,
  className: styleInject_es.PropTypes.string,
  style: styleInject_es.PropTypes.object,
  color: styleInject_es.PropTypes.array,
  scanDur: styleInject_es.PropTypes.number,
  haloDur: styleInject_es.PropTypes.number
};

module.exports = BorderBox;
//# sourceMappingURL=index.js.map
