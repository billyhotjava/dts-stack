import React, { forwardRef, useLayoutEffect } from 'react';
import { s as styleInject, P as PropTypes } from '../style-inject.es-3bd340c8.js';
import '../index-65ee9985.js';
import '../_babelHelpers-a63acad8.js';
import { u as useAutoResize } from '../autoResize-73247714.js';

var css = "#dv-full-screen-container {\n  position: fixed;\n  top: 0px;\n  left: 0px;\n  overflow: hidden;\n  transform-origin: left top;\n  z-index: 999;\n}\n";
styleInject(css);

var FullScreenContainer = forwardRef(function (_ref, ref) {
  var children = _ref.children,
      className = _ref.className,
      style = _ref.style;

  var _useAutoResize = useAutoResize(ref),
      domRef = _useAutoResize.domRef;

  useLayoutEffect(function () {
    var _window$screen = window.screen,
        width = _window$screen.width,
        height = _window$screen.height;


    Object.assign(domRef.current.style, {
      width: width + 'px',
      height: height + 'px'
    });

    domRef.current.style.transform = 'scale(' + document.body.clientWidth / width + ')';
  });

  return React.createElement(
    'div',
    {
      id: 'dv-full-screen-container',
      className: className,
      style: style,
      ref: domRef
    },
    children
  );
});

FullScreenContainer.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object
};

export default FullScreenContainer;
//# sourceMappingURL=index.js.map
