// 会话超时检测：服务端会话闲置 30 分钟失效（见 deploy/DevspecSession.pm.example）。
// - 每分钟及页面重新可见时查询 /api/session（不续期），失效则跳转登录页；
// - 用户在门户或图示 iframe 内操作时，最多每分钟调用一次 /api/session/touch 续期。
(() => {
  'use strict';
  const CHECK_INTERVAL_MS = 60 * 1000;
  const TOUCH_THROTTLE_MS = 60 * 1000;
  const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'];
  let lastTouchAt = 0;
  let expired = false;

  function expire() {
    if (expired) return;
    expired = true;
    window.top.location.replace('login.html?reason=timeout');
  }

  function request(path, method) {
    return fetch(path, { method, credentials: 'same-origin', cache: 'no-store', redirect: 'manual' })
      .then(response => { if (response.status === 401) expire(); })
      .catch(() => { /* 网络抖动时保留当前页面，下一轮再检测 */ });
  }

  function check() {
    request('api/session', 'GET');
  }

  function touch() {
    const now = Date.now();
    if (now - lastTouchAt < TOUCH_THROTTLE_MS) return;
    lastTouchAt = now;
    request('api/session/touch', 'POST');
  }

  function watchActivity(doc) {
    if (!doc) return;
    ACTIVITY_EVENTS.forEach(type => doc.addEventListener(type, touch, { passive: true, capture: true }));
  }

  watchActivity(document);
  const frame = document.getElementById('diagram-frame');
  if (frame) {
    const attach = () => { try { watchActivity(frame.contentDocument); } catch (error) { /* 跨源页面无法监听 */ } };
    frame.addEventListener('load', attach);
    if (frame.contentDocument && frame.contentDocument.readyState === 'complete') attach();
  }
  window.setInterval(check, CHECK_INTERVAL_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
  check();
})();
