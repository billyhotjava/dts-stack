/*
  Static fallback for runtime configuration used by the admin webapp.
  The production image normally generates this file at container start.
  Keeping a stub in /public avoids 404 -> HTML fallback (wrong MIME) in
  offline or misconfigured deployments.
*/
(function (w) {
  try {
    w.__RUNTIME_CONFIG__ = w.__RUNTIME_CONFIG__ || {};
  } catch (e) {}
})(window);

