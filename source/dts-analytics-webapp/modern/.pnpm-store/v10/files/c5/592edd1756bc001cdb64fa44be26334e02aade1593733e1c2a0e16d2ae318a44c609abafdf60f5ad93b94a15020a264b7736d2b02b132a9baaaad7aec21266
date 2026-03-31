import React, { forwardRef, useRef, useEffect, useMemo } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';
import { c as classnames } from '../index-bddb7af5.js';
import '../index-989b4d2a.js';
import '../index-bf9f069c.js';
import '../index-b7412e2a.js';
import '../index-c9d89c8c.js';
import { C as Charts$1 } from '../index-dc0cd592.js';

var css = ".dv-charts-container {\n  position: relative;\n  width: 100%;\n  height: 100%;\n}\n.dv-charts-container .charts-canvas-container {\n  width: 100%;\n  height: 100%;\n}\n";
styleInject(css);

var Charts = forwardRef(function (_ref, ref) {
  var _ref$option = _ref.option,
      option = _ref$option === undefined ? {} : _ref$option,
      className = _ref.className,
      style = _ref.style;

  var _useAutoResize = useAutoResize(ref),
      width = _useAutoResize.width,
      height = _useAutoResize.height,
      domRef = _useAutoResize.domRef;

  var chartRef = useRef(null);

  var chartInstanceofRef = useRef(null);

  useEffect(function () {
    chartInstanceofRef.current || (chartInstanceofRef.current = new Charts$1(chartRef.current));

    chartInstanceofRef.current.setOption(option || {}, true);
  }, [option]);

  useEffect(function () {
    chartInstanceofRef.current.resize();
  }, [width, height]);

  var classNames = useMemo(function () {
    return classnames('dv-charts-container', className);
  }, [className]);

  return React.createElement(
    'div',
    { className: classNames, style: style, ref: domRef },
    React.createElement('div', { className: 'charts-canvas-container', ref: chartRef })
  );
});

Charts.propTypes = {
  option: PropTypes.object,
  className: PropTypes.string,
  style: PropTypes.object
};

export default Charts;
//# sourceMappingURL=index.js.map
