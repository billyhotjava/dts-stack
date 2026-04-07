import React, { forwardRef, useMemo, useRef } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import { u as uuid } from '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1, c as util_13 } from '../index-989b4d2a.js';
import { l as lib_9 } from '../index-bf9f069c.js';

var css = ".dv-decoration-12 {\n  position: relative;\n  width: 100%;\n  height: 100%;\n  display: flex;\n}\n.dv-decoration-12 .decoration-content {\n  position: absolute;\n  top: 0px;\n  left: 0px;\n  width: 100%;\n  height: 100%;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n";
styleInject(css);

var defaultColor = ['#2783ce', '#2cf7fe'];

var segment = 30;

var sectorAngle = Math.PI / 3;

var ringNum = 3;

var ringWidth = 1;

var BorderBox = forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style,
      _ref$color = _ref.color,
      color = _ref$color === undefined ? [] : _ref$color,
      _ref$scanDur = _ref.scanDur,
      scanDur = _ref$scanDur === undefined ? 3 : _ref$scanDur,
      _ref$haloDur = _ref.haloDur,
      haloDur = _ref$haloDur === undefined ? 2 : _ref$haloDur;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var x = width / 2;

  var y = height / 2;

  var mergedColor = useMemo(function () {
    return util_2(util_1(defaultColor, true), color || []);
  }, [color]);

  var pathD = useMemo(function () {
    var startAngle = -Math.PI / 2;
    var angleGap = sectorAngle / segment;
    var r = width / 4;
    var lastEndPoints = util_13(x, y, r, startAngle);

    return new Array(segment).fill('').map(function (_, i) {
      var endPoints = util_13(x, y, r, startAngle - (i + 1) * angleGap).map(function (_) {
        return _.toFixed(5);
      });
      var d = 'M' + lastEndPoints.join(',') + ' A' + r + ', ' + r + ' 0 0 0 ' + endPoints.join(',');
      lastEndPoints = endPoints;
      return d;
    });
  }, [x, y, width]);

  var pathColor = useMemo(function () {
    var color = mergedColor[0];
    var colorGap = 100 / (segment - 1);

    return new Array(segment).fill(color).map(function (_, i) {
      return lib_9(color, 100 - i * colorGap);
    });
  }, [mergedColor]);

  var circleR = useMemo(function () {
    var radiusGap = (width / 2 - ringWidth / 2) / ringNum;

    return new Array(ringNum).fill(0).map(function (_, i) {
      return radiusGap * (i + 1);
    });
  }, [width]);

  var splitLinePoints = useMemo(function () {
    var angleGap = Math.PI / 6;
    var r = width / 2;
    return new Array(6).fill('').map(function (_, i) {
      var startAngle = angleGap * (i + 1);
      var endAngle = startAngle + Math.PI;
      var startPoint = util_13(x, y, r, startAngle);
      var endPoint = util_13(x, y, r, endAngle);
      return startPoint.join(',') + ' ' + endPoint.join(',');
    });
  }, [x, y, width]);

  var arcD = useMemo(function () {
    var angleGap = Math.PI / 6;
    var r = width / 2 - 1;

    return new Array(4).fill('').map(function (_, i) {
      var startAngle = angleGap * (3 * i + 1);
      var endAngle = startAngle + angleGap;
      var startPoint = util_13(x, y, r, startAngle);
      var endPoint = util_13(x, y, r, endAngle);
      return 'M' + startPoint.join(',') + ' A' + x + ', ' + y + ' 0 0 1 ' + endPoint.join(',');
    });
  }, [x, y, width]);

  var idRef = useRef({
    gId: 'decoration-12-g-' + uuid(),
    gradientId: 'decoration-12-gradient-' + uuid()
  });

  var classNames = useMemo(function () {
    return classnames('dv-decoration-12', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement(
      'svg',
      { width: width, height: height },
      React.createElement(
        'defs',
        null,
        React.createElement(
          'g',
          { id: idRef.current.gId },
          pathD.map(function (d, i) {
            return React.createElement('path', {
              stroke: pathColor[i],
              strokeWidth: width / 2,
              fill: 'transparent',
              key: d,
              d: d
            });
          })
        ),
        React.createElement(
          'radialGradient',
          {
            id: idRef.current.gradientId,
            cx: '50%', cy: '50%', r: '50%'
          },
          React.createElement('stop', { offset: '0%', stopColor: 'transparent', stopOpacity: '1' }),
          React.createElement('stop', { offset: '100%', stopColor: lib_9(mergedColor[1] || defaultColor[1], 30), stopOpacity: '1' })
        )
      ),
      circleR.map(function (r) {
        return React.createElement('circle', {
          key: r,
          r: r,
          cx: x,
          cy: y,
          stroke: mergedColor[1],
          strokeWidth: 0.5,
          fill: 'transparent'
        });
      }),
      React.createElement(
        'circle',
        {
          r: '1',
          cx: x,
          cy: y,
          stroke: 'transparent',
          fill: 'url(#' + idRef.current.gradientId + ')'
        },
        React.createElement('animate', {
          attributeName: 'r',
          values: '1;' + width / 2,
          dur: haloDur + 's',
          repeatCount: 'indefinite'
        }),
        React.createElement('animate', {
          attributeName: 'opacity',
          values: '1;0',
          dur: haloDur + 's',
          repeatCount: 'indefinite'
        })
      ),
      React.createElement('circle', {
        r: '2',
        cx: x,
        cy: y,
        fill: mergedColor[1]
      }),
       React.createElement(
        'g',
        null,
        splitLinePoints.map(function (p) {
          return React.createElement('polyline', {
            key: p,
            points: p,
            stroke: mergedColor[1],
            strokeWidth: 0.5,
            opacity: '0.5'
          });
        })
      ),
      arcD.map(function (d) {
        return React.createElement('path', {
          key: d,
          d: d,
          stroke: mergedColor[1],
          strokeWidth: '2',
          fill: 'transparent'
        });
      }),
      React.createElement(
        'use',
        { href: '#' + idRef.current.gId },
        React.createElement('animateTransform', {
          attributeName: 'transform',
          type: 'rotate',
          values: '0, ' + x + ' ' + y + ';360, ' + x + ' ' + y,
          dur: scanDur + 's',
          repeatCount: 'indefinite'
        })
      )
    ),
    React.createElement(
      'div',
      { className: 'decoration-content' },
      children
    )
  );
});

BorderBox.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object,
  color: PropTypes.array,
  scanDur: PropTypes.number,
  haloDur: PropTypes.number
};

export default BorderBox;
//# sourceMappingURL=index.js.map
