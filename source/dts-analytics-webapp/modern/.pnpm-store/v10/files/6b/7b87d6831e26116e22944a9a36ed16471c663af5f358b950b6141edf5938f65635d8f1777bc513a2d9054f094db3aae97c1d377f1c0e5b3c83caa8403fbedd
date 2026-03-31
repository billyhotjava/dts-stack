import React, { forwardRef, useRef, useMemo, useState, useCallback, useEffect } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import { u as uuid, r as randomExtend, g as getPointDistance } from '../index-65ee9985.js';
import { s as slicedToArray, t as toConsumableArray, _ as _extends } from '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import { u as util_2, a as util_1 } from '../index-989b4d2a.js';

var css = ".dv-flyline-chart-enhanced {\n  display: flex;\n  flex-direction: column;\n  background-size: 100% 100%;\n}\n.dv-flyline-chart-enhanced text {\n  text-anchor: middle;\n  dominant-baseline: middle;\n}\n";
styleInject(css);

/**
* @description Type Declaration
*
* interface Halo {
*    show?: boolean
*    duration?: [number, number]
*    color?: string
*    radius?: number
* }
*
* interface Text {
*    show?: boolean
*    offset?: [number, number]
*    color?: string
*    fontSize?: number
* }
*
* interface Icon {
*    show?: boolean
*    src?: string
*    width?: number
*    height?: number
* }
*
* interface Point {
*    name: string
*    coordinate: [number, number]
*    halo?: Halo
*    text?: Text
*    icon?: Icon
* }
*
* interface Line {
*    width?: number
*    color?: string
*    orbitColor?: string
*    duration?: [number, number]
*    radius?: string
* }
*
* interface Flyline extends Line {
*    source: string
*    target: string
* }
*
* interface FlylineWithPath extends Flyline {
*    d: string
*    path: [[number, number], [number, number], [number, number]]
*    key: string
* }
*/
var defaultConfig = {
  /**
   * @description Flyline chart points
   * @type {Point[]}
   * @default points = []
   */
  points: [],
  /**
   * @description Lines
   * @type {Flyline[]}
   * @default lines = []
   */
  lines: [],
  /**
   * @description Global halo configuration
   * @type {Halo}
   */
  halo: {
    /**
     * @description Whether to show halo
     * @type {Boolean}
     * @default show = false
     */
    show: false,
    /**
     * @description Halo animation duration (1s = 10)
     * @type {[number, number]}
     */
    duration: [20, 30],
    /**
     * @description Halo color
     * @type {String}
     * @default color = '#fb7293'
     */
    color: '#fb7293',
    /**
     * @description Halo radius
     * @type {Number}
     * @default radius = 120
     */
    radius: 120
  },
  /**
   * @description Global text configuration
   * @type {Text}
   */
  text: {
    /**
     * @description Whether to show text
     * @type {Boolean}
     * @default show = false
     */
    show: false,
    /**
     * @description Text offset
     * @type {[number, number]}
     * @default offset = [0, 15]
     */
    offset: [0, 15],
    /**
     * @description Text color
     * @type {String}
     * @default color = '#ffdb5c'
     */
    color: '#ffdb5c',
    /**
     * @description Text font size
     * @type {Number}
     * @default fontSize = 12
     */
    fontSize: 12
  },
  /**
   * @description Global icon configuration
   * @type {Icon}
   */
  icon: {
    /**
     * @description Whether to show icon
     * @type {Boolean}
     * @default show = false
     */
    show: false,
    /**
     * @description Icon src
     * @type {String}
     * @default src = ''
     */
    src: '',
    /**
     * @description Icon width
     * @type {Number}
     * @default width = 15
     */
    width: 15,
    /**
     * @description Icon height
     * @type {Number}
     * @default width = 15
     */
    height: 15
  },
  /**
   * @description Global line configuration
   * @type {Line}
   */
  line: {
    /**
     * @description Line width
     * @type {Number}
     * @default width = 1
     */
    width: 1,
    /**
     * @description Flyline color
     * @type {String}
     * @default color = '#ffde93'
     */
    color: '#ffde93',
    /**
     * @description Orbit color
     * @type {String}
     * @default orbitColor = 'rgba(103, 224, 227, .2)'
     */
    orbitColor: 'rgba(103, 224, 227, .2)',
    /**
     * @description Flyline animation duration
     * @type {[number, number]}
     * @default duration = [20, 30]
     */
    duration: [20, 30],
    /**
     * @description Flyline radius
     * @type {Number}
     * @default radius = 100
     */
    radius: 100
  },
  /**
   * @description Back ground image url
   * @type {String}
   * @default bgImgSrc = ''
   */
  bgImgSrc: '',
  /**
   * @description K value
   * @type {Number}
   * @default k = -0.5
   * @example k = -1 ~ 1
   */
  k: -0.5,
  /**
   * @description Flyline curvature
   * @type {Number}
   * @default curvature = 5
   */
  curvature: 5,
  /**
   * @description Relative points position
   * @type {Boolean}
   * @default relative = true
   */
  relative: true
};

function getKLinePointByx(k, _ref, x) {
  var _ref2 = slicedToArray(_ref, 2),
      lx = _ref2[0],
      ly = _ref2[1];

  var y = ly - k * lx + k * x;

  return [x, y];
}

var FlyLineChartEnhanced = forwardRef(function (_ref3, ref) {
  var _ref3$config = _ref3.config,
      config = _ref3$config === undefined ? {} : _ref3$config,
      _ref3$dev = _ref3.dev,
      dev = _ref3$dev === undefined ? false : _ref3$dev,
      className = _ref3.className,
      style = _ref3.style;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var _useRef$current = useRef({
    unique: Math.random(),
    flylineGradientId: 'flyline-gradient-id-' + uuid(),
    haloGradientId: 'halo-gradient-id-' + uuid()
  }).current,
      unique = _useRef$current.unique,
      flylineGradientId = _useRef$current.flylineGradientId,
      haloGradientId = _useRef$current.haloGradientId;

  var _useMemo = useMemo(calcData, [config, width, height]),
      mergedConfig = _useMemo.mergedConfig,
      flylinePoints = _useMemo.flylinePoints,
      flylines = _useMemo.flylines;

  var _useState = useState([]),
      _useState2 = slicedToArray(_useState, 2),
      flylineLengths = _useState2[0],
      setFlylineLengths = _useState2[1];

  var pathDomRef = useRef([]);

  function calcData() {
    var mergedConfig = getMergedConfig();

    var flylinePoints = getFlylinePoints(mergedConfig);

    var flylines = getLinePaths(mergedConfig);

    return { mergedConfig: mergedConfig, flylinePoints: flylinePoints, flylines: flylines };
  }

  function getMergedConfig() {
    var mergedConfig = util_2(util_1(defaultConfig, true), config || {});
    var points = mergedConfig.points,
        lines = mergedConfig.lines,
        halo = mergedConfig.halo,
        text = mergedConfig.text,
        icon = mergedConfig.icon,
        line = mergedConfig.line;


    mergedConfig.points = points.map(function (item) {
      item.halo = util_2(util_1(halo, true), item.halo || {});
      item.text = util_2(util_1(text, true), item.text || {});
      item.icon = util_2(util_1(icon, true), item.icon || {});
      return item;
    });

    mergedConfig.lines = lines.map(function (item) {
      return util_2(util_1(line, true), item);
    });

    return mergedConfig;
  }

  function getFlylinePoints(mergedConfig) {
    var relative = mergedConfig.relative,
        points = mergedConfig.points;


    return points.map(function (item, i) {
      var _item$coordinate = slicedToArray(item.coordinate, 2),
          x = _item$coordinate[0],
          y = _item$coordinate[1],
          halo = item.halo,
          icon = item.icon,
          text = item.text;

      if (relative) item.coordinate = [x * width, y * height];

      item.halo.time = randomExtend.apply(undefined, toConsumableArray(halo.duration)) / 10;

      var iw = icon.width,
          ih = icon.height;

      item.icon.x = item.coordinate[0] - iw / 2;
      item.icon.y = item.coordinate[1] - ih / 2;

      var _text$offset = slicedToArray(text.offset, 2),
          ox = _text$offset[0],
          oy = _text$offset[1];

      item.text.x = item.coordinate[0] + ox;
      item.text.y = item.coordinate[1] + oy;
      item.key = '' + item.coordinate.toString() + i;

      return item;
    });
  }

  function getLinePaths(mergedConfig) {
    var points = mergedConfig.points,
        lines = mergedConfig.lines;


    return lines.map(function (item) {
      var source = item.source,
          target = item.target,
          duration = item.duration;

      var sourcePoint = points.find(function (_ref4) {
        var name = _ref4.name;
        return name === source;
      }).coordinate;
      var targetPoint = points.find(function (_ref5) {
        var name = _ref5.name;
        return name === target;
      }).coordinate;
      var path = getPath(sourcePoint, targetPoint, mergedConfig).map(function (item) {
        return item.map(function (v) {
          return parseFloat(v.toFixed(10));
        });
      });
      var d = 'M' + path[0].toString() + ' Q' + path[1].toString() + ' ' + path[2].toString();
      var key = 'path' + path.toString();
      var time = randomExtend.apply(undefined, toConsumableArray(duration)) / 10;

      return _extends({}, item, { path: path, key: key, d: d, time: time });
    });
  }

  function getPath(start, end, mergedConfig) {
    var controlPoint = getControlPoint(start, end, mergedConfig);

    return [start, controlPoint, end];
  }

  function getControlPoint(_ref6, _ref7, _ref8) {
    var _ref10 = slicedToArray(_ref6, 2),
        sx = _ref10[0],
        sy = _ref10[1];

    var _ref9 = slicedToArray(_ref7, 2),
        ex = _ref9[0],
        ey = _ref9[1];

    var curvature = _ref8.curvature,
        k = _ref8.k;
    var mx = (sx + ex) / 2,
        my = (sy + ey) / 2;

    var distance = getPointDistance([sx, sy], [ex, ey]);
    var targetLength = distance / curvature;
    var disDived = targetLength / 2;
    var dx = mx,
        dy = my;


    do {
      dx += disDived;
      dy = getKLinePointByx(k, [mx, my], dx)[1];
    } while (getPointDistance([mx, my], [dx, dy]) < targetLength);

    return [dx, dy];
  }

  var consoleClickPos = useCallback(function (_ref11) {
    var offsetX = _ref11.offsetX,
        offsetY = _ref11.offsetY;

    if (!dev) return;

    var relativeX = (offsetX / width).toFixed(2);
    var relativeY = (offsetY / height).toFixed(2);

    console.warn('dv-flyline-chart-enhanced DEV: \n Click Position is [' + offsetX + ', ' + offsetY + '] \n Relative Position is [' + relativeX + ', ' + relativeY + ']');
  }, [width, height, dev]);

  useEffect(function () {
    var lengths = flylines.map(function (foo, i) {
      return pathDomRef.current[i].getTotalLength();
    });

    setFlylineLengths(lengths);
  }, [flylines]);

  var classNames = useMemo(function () {
    return classnames('dv-flyline-chart-enhanced', className);
  }, [className]);

  return React.createElement(
    'div',
    {
      className: classNames,
      ref: domRef,
      style: _extends({ backgroundImage: 'url(' + (mergedConfig ? mergedConfig.bgImgSrc : '') + ')' }, style),
      onClick: consoleClickPos
    },
    flylines.length && React.createElement(
      'svg',
      { width: width, height: height },
      React.createElement(
        'defs',
        null,
        React.createElement(
          'radialGradient',
          {
            id: flylineGradientId,
            cx: '50%', cy: '50%', r: '50%'
          },
          React.createElement('stop', {
            offset: '0%', stopColor: '#fff',
            stopOpacity: '1'
          }),
          React.createElement('stop', {
            offset: '100%', stopColor: '#fff',
            stopOpacity: '0'
          })
        ),
        React.createElement(
          'radialGradient',
          {
            id: haloGradientId,
            cx: '50%', cy: '50%', r: '50%'
          },
          React.createElement('stop', {
            offset: '0%', stopColor: '#fff',
            stopOpacity: '0'
          }),
          React.createElement('stop', {
            offset: '100%', stopColor: '#fff',
            stopOpacity: '1'
          })
        )
      ),
      flylinePoints.map(function (point, i) {
        return React.createElement(
          'g',
          { key: i },
          React.createElement(
            'defs',
            null,
            point.halo.show && React.createElement(
              'circle',
              {
                id: 'halo' + unique + point.key,
                cx: point.coordinate[0],
                cy: point.coordinate[1]
              },
              React.createElement('animate', {
                attributeName: 'r',
                values: '1;' + point.halo.radius,
                dur: point.halo.time + 's',
                repeatCount: 'indefinite'
              }),
              React.createElement('animate', {
                attributeName: 'opacity',
                values: '1;0',
                dur: point.halo.time + 's',
                repeatCount: 'indefinite'
              })
            )
          ),
          React.createElement(
            'mask',
            { id: 'mask' + unique + point.key },
            point.halo.show && React.createElement('use', {
              href: '#halo' + unique + point.key,
              fill: 'url(#' + haloGradientId + ')'
            })
          ),
          point.halo.show && React.createElement('use', {
            href: '#halo' + unique + point.key,
            fill: point.halo.color,
            mask: 'url(#mask' + unique + point.key + ')'
          }),
          point.icon.show && React.createElement('image', {
            href: point.icon.src,
            width: point.icon.width,
            height: point.icon.height,
            x: point.icon.x,
            y: point.icon.y
          }),
          point.text.show && React.createElement(
            'text',
            {
              style: { fontSize: point.text.fontSize + 'px', color: '' + point.text.color },
              fill: point.text.color,
              x: point.text.x,
              y: point.text.y
            },
            point.name
          )
        );
      }),
      flylines.map(function (line, i) {
        return React.createElement(
          'g',
          { key: i },
          React.createElement(
            'defs',
            null,
            React.createElement('path', {
              id: line.key,
              ref: function ref(e) {
                return pathDomRef.current[i] = e;
              },
              d: line.d,
              fill: 'transparent'
            })
          ),
          React.createElement('use', {
            href: '#' + line.key,
            strokeWidth: line.width,
            stroke: line.orbitColor
          }),
          React.createElement(
            'mask',
            { id: 'mask' + unique + line.key },
            React.createElement(
              'circle',
              { cx: '0', cy: '0', r: line.radius, fill: 'url(#' + flylineGradientId + ')' },
              React.createElement('animateMotion', {
                dur: line.time,
                path: line.d,
                rotate: 'auto',
                repeatCount: 'indefinite'
              })
            )
          ),
          flylineLengths[i] && React.createElement(
            'use',
            {
              href: '#' + line.key,
              strokeWidth: line.width,
              stroke: line.color,
              mask: 'url(#mask' + unique + line.key + ')'
            },
            React.createElement('animate', {
              attributeName: 'stroke-dasharray',
              from: '0, ' + flylineLengths[i],
              to: flylineLengths[i] + ', 0',
              dur: line.time,
              repeatCount: 'indefinite'
            })
          )
        );
      })
    )
  );
});

FlyLineChartEnhanced.propTypes = {
  config: PropTypes.object,
  dev: PropTypes.bool,
  className: PropTypes.string,
  style: PropTypes.object

  // 指定 props 的默认值：
};FlyLineChartEnhanced.defaultProps = {
  dev: false
};

export default FlyLineChartEnhanced;
//# sourceMappingURL=index.js.map
