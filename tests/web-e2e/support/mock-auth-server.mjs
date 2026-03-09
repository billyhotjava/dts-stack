import { createServer } from 'node:http';

const host = '127.0.0.1';
const port = Number(process.env.DTS_WEB_E2E_DEV_SERVER_PORT || '19333');

const USERS = {
  opadmin: { fullName: '业务运维管理员', roles: ['ROLE_OP_ADMIN'] },
  sysadmin: { fullName: '系统管理员', roles: ['ROLE_SYS_ADMIN'] },
  authadmin: { fullName: '授权管理员', roles: ['ROLE_AUTH_ADMIN'] },
  auditadmin: { fullName: '安全审计员', roles: ['ROLE_SECURITY_AUDITOR'] },
};

function createSeedState() {
  return {
    journeys: {
      erp: { status: 'empty', seededAt: null, count: 0 },
      analytics: { status: 'empty', seededAt: null, count: 0 },
      governance: { status: 'empty', seededAt: null, count: 0 },
      auth: { status: 'empty', seededAt: null, count: 0 },
    },
    resetCount: 0,
    lastScope: null,
  };
}

let seedState = createSeedState();

function sendHtml(res, html, status = 200) {
  res.writeHead(status, { 'content-type': 'text/html; charset=utf-8' });
  res.end(html);
}

function sendJson(res, payload, status = 200) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
    req.on('error', reject);
  });
}

function safeReturnUrl(raw) {
  if (!raw || typeof raw !== 'string') return '/expert/';
  const trimmed = raw.trim();
  if (!trimmed.startsWith('/')) return '/expert/';
  if (trimmed.startsWith('//') || trimmed.includes('://')) return '/expert/';
  return trimmed;
}

function renderLoginPage(returnUrl) {
  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <title>统一登录</title>
    <style>
      body { font-family: sans-serif; background: #f7f7f5; margin: 0; display: grid; place-items: center; min-height: 100vh; }
      main { width: 360px; background: white; border-radius: 16px; padding: 24px; box-shadow: 0 24px 80px rgba(0,0,0,.12); }
      h1 { margin: 0 0 8px; font-size: 28px; }
      p { color: #666; margin: 0 0 20px; }
      label { display: block; margin-bottom: 12px; font-size: 14px; }
      input { width: 100%; box-sizing: border-box; padding: 10px 12px; margin-top: 6px; border: 1px solid #d0d0cc; border-radius: 10px; }
      button { width: 100%; border: 0; border-radius: 999px; padding: 12px 16px; background: #111; color: white; font-size: 15px; cursor: pointer; }
      .error { color: #b42318; min-height: 24px; margin-top: 12px; }
      .meta { color: #888; font-size: 12px; margin-top: 16px; }
    </style>
  </head>
  <body>
    <main>
      <h1>统一登录</h1>
      <p>请使用您的账号登录系统</p>
      <form id="login-form">
        <label>用户名
          <input data-testid="username" name="username" placeholder="请输入用户名" autocomplete="username" />
        </label>
        <label>密码
          <input data-testid="password" name="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </label>
        <button data-testid="login-submit" type="submit">登录</button>
      </form>
      <div class="error" data-testid="login-error"></div>
      <div class="meta">returnUrl: ${returnUrl}</div>
    </main>
    <script>
      const RETURN_URL = ${JSON.stringify(returnUrl)};
      const form = document.getElementById('login-form');
      const errorBox = document.querySelector('[data-testid="login-error"]');
      function writeStore(key, payload) {
        localStorage.setItem(key, JSON.stringify(payload));
      }
      function saveSession(result) {
        const store = { state: { userToken: { accessToken: result.accessToken, refreshToken: result.refreshToken }, userInfo: result.user }, version: 0 };
        if (RETURN_URL.startsWith('/admin/')) {
          writeStore('adminUserStore', store);
        } else {
          writeStore('platformUserStore', store);
          writeStore('userStore', store);
          const now = String(Date.now());
          localStorage.setItem('dts.platform.session.loginTs', now);
          localStorage.setItem('dts.platform.session.lastActivity', now);
        }
      }
      form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorBox.textContent = '';
        const username = form.username.value.trim();
        const password = form.password.value;
        const scope = RETURN_URL.startsWith('/admin/') ? 'admin' : 'platform';
        const response = await fetch('/api/keycloak/auth/login', {
          method: 'POST',
          headers: { 'content-type': 'application/json', accept: 'application/json' },
          body: JSON.stringify({ username, password, scope }),
        });
        const body = await response.json().catch(() => null);
        if (!response.ok) {
          errorBox.textContent = body?.message || '登录失败';
          return;
        }
        const payload = body?.data || body || {};
        saveSession(payload);
        window.location.assign(RETURN_URL);
      });
    </script>
  </body>
</html>`;
}

function renderProtectedPage(kind, returnUrl, storeKeys) {
  const items = Object.entries(seedState.journeys)
    .map(([name, meta]) => `<li data-testid="seed-${name}">${meta.status}</li>`)
    .join('');
  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <title>${kind}</title>
    <style>body { font-family: sans-serif; margin: 0; padding: 32px; background: #f6f3ee; }</style>
  </head>
  <body>
    <div data-testid="booting">booting</div>
    <script>
      const STORE_KEYS = ${JSON.stringify(storeKeys)};
      const RETURN_URL = ${JSON.stringify(returnUrl)};
      function hasAccessToken() {
        for (const key of STORE_KEYS) {
          try {
            const raw = localStorage.getItem(key);
            if (!raw) continue;
            const parsed = JSON.parse(raw);
            const token = parsed?.state?.userToken?.accessToken;
            if (typeof token === 'string' && token.trim()) return true;
          } catch {}
        }
        return false;
      }
      if (!hasAccessToken()) {
        window.location.replace('/auth/login?returnUrl=' + encodeURIComponent(RETURN_URL));
      } else {
        document.body.innerHTML = '<main data-testid="app-shell">${kind} shell</main><ul data-testid="seed-state">${items}</ul>';
      }
    </script>
  </body>
</html>`;
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://${host}:${port}`);

  if (req.method === 'GET' && url.pathname === '/healthz') {
    return sendJson(res, { ok: true });
  }

  if (req.method === 'GET' && url.pathname === '/api/test-support/state') {
    return sendJson(res, { data: seedState });
  }

  if (req.method === 'POST' && url.pathname === '/api/test-support/reset') {
    const rawBody = await readBody(req);
    const body = rawBody ? JSON.parse(rawBody) : {};
    seedState = createSeedState();
    seedState.resetCount = 1;
    seedState.lastScope = typeof body.scope === 'string' ? body.scope : 'suite';
    return sendJson(res, { data: seedState });
  }

  if (req.method === 'POST' && url.pathname === '/api/test-support/seed') {
    const rawBody = await readBody(req);
    const body = rawBody ? JSON.parse(rawBody) : {};
    const journeys = Array.isArray(body.journeys) ? body.journeys : [];
    const seededAt = new Date().toISOString();
    for (const journey of journeys) {
      if (!seedState.journeys[journey]) continue;
      seedState.journeys[journey] = {
        status: 'ready',
        seededAt,
        count: 3,
      };
    }
    return sendJson(res, { data: seedState });
  }

  if (req.method === 'GET' && url.pathname === '/auth/login') {
    return sendHtml(res, renderLoginPage(safeReturnUrl(url.searchParams.get('returnUrl'))));
  }

  if (req.method === 'POST' && url.pathname === '/api/keycloak/auth/login') {
    const rawBody = await readBody(req);
    const body = rawBody ? JSON.parse(rawBody) : {};
    const username = String(body.username || '').trim().toLowerCase();
    const password = String(body.password || '');
    const scope = String(body.scope || 'platform').trim().toLowerCase();
    if (!username || !password) {
      return sendJson(res, { message: '缺少用户名或密码' }, 400);
    }
    if (password !== 'sa') {
      return sendJson(res, { message: '用户名或密码错误' }, 401);
    }
    const user = USERS[username];
    if (!user) {
      return sendJson(res, { message: '用户不存在' }, 404);
    }
    if (scope !== 'admin' && user.roles.some((role) => role !== 'ROLE_OP_ADMIN')) {
      return sendJson(res, { message: '系统管理角色用户不能登录业务平台' }, 403);
    }
    const accessToken = `dev-access-${scope}-${username}`;
    const refreshToken = `dev-refresh-${scope}-${username}`;
    return sendJson(res, {
      data: {
        accessToken,
        refreshToken,
        user: {
          username,
          fullName: user.fullName,
          roles: user.roles,
          permissions: [],
        },
      },
    });
  }

  if (req.method === 'GET' && url.pathname === '/analytics/') {
    return sendHtml(res, renderProtectedPage('analytics', '/analytics/', ['platformUserStore', 'userStore']));
  }

  if (req.method === 'GET' && url.pathname === '/expert/') {
    return sendHtml(res, renderProtectedPage('expert', '/expert/', ['platformUserStore', 'userStore']));
  }

  if (req.method === 'GET' && url.pathname === '/admin/') {
    return sendHtml(res, renderProtectedPage('admin', '/admin/', ['adminUserStore']));
  }

  if (req.method === 'GET' && url.pathname === '/') {
    res.writeHead(302, { location: '/expert/' });
    return res.end();
  }

  return sendJson(res, { message: 'not found', path: url.pathname }, 404);
});

server.listen(port, host, () => {
  console.log(`[mock-auth-server] listening on http://${host}:${port}`);
});
