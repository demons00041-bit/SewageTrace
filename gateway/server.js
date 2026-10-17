/**
 * 污水治理 SewageTrace · 云端识别反代网关
 * ---------------------------------------------------------------
 * 目的：APK 里**不存在**任何第三方 API 密钥。
 *
 *   手机 App ──X-ST-Token──▶ 本网关 ──Authorization: Bearer <真 key>──▶ api.deepseek.com
 *
 * 客户端只持有一个自签的设备令牌（`st:` 前缀），泄露它最多只能刷配额，
 * 拿不到密钥、改不了模型、也不会有账单爆炸（下面有配额和速率限制）。
 *
 * 零依赖：只用 Node 内置 http/https/fetch (Node 18+)。不装 npm 包就不会有供应链风险。
 *
 * 启动：
 *   cp .env.example .env        # 填 DEEPSEEK_API_KEY
 *   node server.js
 *
 * 环境变量（全部在 .env 或环境里，代码中不出现任何密钥）：
 *   DEEPSEEK_API_KEY   必填，真实密钥
 *   PORT               默认 8787
 *   ALLOWED_MODELS     逗号分隔白名单，默认 deepseek-flash
 *   ISSUE_SECRET       必填，签发令牌时校验的口令（建议 32 位随机）
 *   DAILY_QUOTA        每个令牌每日识别次数上限，默认 50
 *   PER_MINUTE         每个令牌每分钟上限，默认 6
 *   TOKENS_FILE        令牌库路径，默认 ./data/tokens.json
 */

'use strict';

const http = require('node:http');
const https = require('node:https');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { URL } = require('node:url');
// ------------------------------------------------------------------ 配置

function loadEnv() {
  const file = path.join(__dirname, '.env');
  if (!fs.existsSync(file)) return;
  for (const raw of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const i = line.indexOf('=');
    if (i < 0) continue;
    const k = line.slice(0, i).trim();
    const v = line.slice(i + 1).trim().replace(/^["']|["']$/g, '');
    if (!(k in process.env)) process.env[k] = v;
  }
}
loadEnv();

const CFG = {
  port: Number(process.env.PORT || 8787),
  // 测试时可用 UPSTREAM_OVERRIDE 指向本地假上游，避免打到真实 API
  upstream: process.env.UPSTREAM_OVERRIDE || 'https://api.deepseek.com',
  apiKey: process.env.DEEPSEEK_API_KEY || '',
  allowedModels: (process.env.ALLOWED_MODELS || 'deepseek-flash')
    .split(',').map((s) => s.trim()).filter(Boolean),
  issueSecret: process.env.ISSUE_SECRET || '',
  dailyQuota: Number(process.env.DAILY_QUOTA || 50),
  perMinute: Number(process.env.PER_MINUTE || 6),
  tokensFile: process.env.TOKENS_FILE || path.join(__dirname, 'data', 'tokens.json'),
  // 单张图片缩放后约 300 KB，base64 后约 400 KB；给 8 MB 余量防止滥用
  maxBodyBytes: Number(process.env.MAX_BODY_BYTES || 8 * 1024 * 1024),
};

if (!CFG.apiKey) {
  console.error('[fatal] DEEPSEEK_API_KEY 未设置，网关拒绝启动。');
  process.exit(1);
}

// ------------------------------------------------------------------ 令牌库

/**
 * 令牌以 sha256 存储，磁盘上看不到明文 —— 即使 tokens.json 被拷走也无法直接伪造请求。
 * 形式：{ sha256hex: { id, createdAt, disabled, dailyCount, dayKey, minuteWindow } }
 */
const tokens = new Map();

function loadTokens() {
  try {
    if (fs.existsSync(CFG.tokensFile)) {
      const raw = JSON.parse(fs.readFileSync(CFG.tokensFile, 'utf8'));
      for (const [k, v] of Object.entries(raw)) tokens.set(k, v);
    }
  } catch (e) {
    console.error('[warn] 令牌库读取失败，按空库启动：', e.message);
  }
}

let saveTimer = null;
function saveTokens() {
  // 合并写，避免每次请求都落盘
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.mkdirSync(path.dirname(CFG.tokensFile), { recursive: true });
      const obj = Object.fromEntries(tokens);
      fs.writeFileSync(CFG.tokensFile, JSON.stringify(obj, null, 2));
    } catch (e) {
      console.error('[warn] 令牌库写入失败：', e.message);
    }
  }, 1500);
}

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const dayKey = () => new Date().toISOString().slice(0, 10);

// ------------------------------------------------------------------ HTTP 工具

function send(res, code, body, extraHeaders = {}) {
  const payload = typeof body === 'string' ? body : JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...extraHeaders,
  });
  res.end(payload);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        reject(Object.assign(new Error('body too large'), { code: 413 }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

/** 向 DeepSeek 发请求（用 https 模块而不是 fetch，保留对超时和响应流的控制） */
function upstreamPost(pathname, jsonBody, timeoutMs = 90_000) {
  return new Promise((resolve, reject) => {
    const url = new URL(pathname, CFG.upstream);
    const payload = Buffer.from(JSON.stringify(jsonBody), 'utf8');
    const isHttp = url.protocol === 'http:';
    const transport = isHttp ? http : https;
    const req = transport.request(
      {
        hostname: url.hostname,
        port: url.port || (isHttp ? 80 : 443),
        path: url.pathname + url.search,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          Authorization: `Bearer ${CFG.apiKey}`,
          'Content-Length': payload.length,
        },
        timeout: timeoutMs,
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () =>
          resolve({ status: res.statusCode, text: Buffer.concat(chunks).toString('utf8') })
        );
      }
    );
    req.on('timeout', () => req.destroy(new Error('upstream timeout')));
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

// ------------------------------------------------------------------ 鉴权 & 限流

/**
 * 校验设备令牌。返回 { ok: true, rec } 或 { ok: false, code, reason }。
 * 三道闸：存在 → 未禁用 → 分钟速率 → 日配额。
 */
function authorize(req) {
  const token = req.headers['x-st-token'];
  if (!token || typeof token !== 'string' || !token.startsWith('st:')) {
    return { ok: false, code: 401, reason: 'missing_token' };
  }
  const rec = tokens.get(sha256(token));
  if (!rec) return { ok: false, code: 401, reason: 'unknown_token' };
  if (rec.disabled) return { ok: false, code: 403, reason: 'token_disabled' };

  const now = Date.now();
  const today = dayKey();
  if (rec.dayKey !== today) {
    rec.dayKey = today;
    rec.dailyCount = 0;
  }
  if (rec.dailyCount >= CFG.dailyQuota) {
    return { ok: false, code: 429, reason: 'daily_quota_exceeded' };
  }

  rec.minuteWindow = (rec.minuteWindow || []).filter((t) => now - t < 60_000);
  if (rec.minuteWindow.length >= CFG.perMinute) {
    return { ok: false, code: 429, reason: 'rate_limited' };
  }
  rec.minuteWindow.push(now);
  rec.lastUsedAt = new Date(now).toISOString();
  return { ok: true, rec };
}

// ------------------------------------------------------------------ 路由

/**
 * POST /v1/ai/analyze
 * body: { model?, messages: [...], temperature?, max_tokens?, response_format? }
 * 网关做的事：
 *   1. 丢掉客户端传来的 model，强制用白名单里的第一个（防越权调用贵模型）
 *   2. 丢掉任何客户端传来的 Authorization（防被当成跳板）
 *   3. 鉴权 + 限流 + 计数
 *   4. 转发并把上游响应原样返回（App 侧解析逻辑不变）
 */
async function handleAnalyze(req, res) {
  const auth = authorize(req);
  if (!auth.ok) {
    return send(res, auth.code, { error: { message: auth.reason } });
  }

  let raw;
  try {
    raw = await readBody(req, CFG.maxBodyBytes);
  } catch (e) {
    return send(res, e.code || 400, { error: { message: e.message } });
  }

  let body;
  try {
    body = JSON.parse(raw.toString('utf8'));
  } catch {
    return send(res, 400, { error: { message: 'invalid_json' } });
  }

  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    return send(res, 400, { error: { message: 'messages_required' } });
  }

  // ---- 关键：white-list 化，客户端不能自选模型、不能塞额外字段
  const model = CFG.allowedModels.includes(body.model)
    ? body.model
    : CFG.allowedModels[0];

  const fwd = {
    model,
    messages: body.messages,
    temperature: typeof body.temperature === 'number' ? Math.min(Math.max(body.temperature, 0), 1) : 0.1,
    max_tokens: Math.min(Number(body.max_tokens) || 2000, 3000),
    stream: false,
    response_format: { type: 'json_object' },
  };

  let out;
  try {
    out = await upstreamPost('/chat/completions', fwd);
  } catch (e) {
    console.error('[upstream]', e.message);
    return send(res, 502, { error: { message: `upstream_error: ${e.message}` } });
  }

  if (out.status >= 200 && out.status < 300) {
    auth.rec.dailyCount += 1;
    saveTokens();
  }

  res.writeHead(out.status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(out.text);
}

/** GET /v1/ai/health —— App 的「测试连接」按钮打这里。不消耗任何 token。 */
function handleHealth(req, res) {
  const auth = authorize(req);
  if (!auth.ok) {
    return send(res, auth.code, { error: { message: auth.reason } });
  }
  return send(res, 200, {
    ok: true,
    models: CFG.allowedModels,
    quota_left: Math.max(0, CFG.dailyQuota - auth.rec.dailyCount),
    quota_total: CFG.dailyQuota,
  });
}

/**
 * POST /v1/admin/issue —— 签发新设备令牌。
 * 用 ISSUE_SECRET 保护；**只在内网/本机调用**，不要把这个端点暴露到公网。
 * 返回完整令牌，且仅此一次可见（库里只有哈希）。
 */
async function handleIssue(req, res) {
  if (!CFG.issueSecret) {
    return send(res, 501, { error: { message: 'issuing_disabled' } });
  }
  const secret = req.headers['x-issue-secret'];
  // 定时安全比较，避免时序侧信道
  const a = Buffer.from(String(secret || ''));
  const b = Buffer.from(CFG.issueSecret);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return send(res, 403, { error: { message: 'forbidden' } });
  }

  const bytes = crypto.randomBytes(24);
  const token = 'st:' + bytes.toString('hex');
  const now = new Date().toISOString();
  tokens.set(sha256(token), {
    id: crypto.randomUUID(),
    createdAt: now,
    disabled: false,
    dailyCount: 0,
    dayKey: dayKey(),
    minuteWindow: [],
  });
  saveTokens();
  return send(res, 201, { token });
}

/** POST /v1/admin/revoke —— 吊销令牌（App 里「生成新令牌」之后调它让旧的立刻失效） */
function handleRevoke(req, res, id) {
  if (!CFG.issueSecret) {
    return send(res, 501, { error: { message: 'issuing_disabled' } });
  }
  const secret = req.headers['x-issue-secret'];
  const a = Buffer.from(String(secret || ''));
  const b = Buffer.from(CFG.issueSecret);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return send(res, 403, { error: { message: 'forbidden' } });
  }
  let hit = false;
  for (const rec of tokens.values()) {
    if (rec.id === id) {
      rec.disabled = true;
      hit = true;
    }
  }
  if (hit) saveTokens();
  return send(res, hit ? 200 : 404, { ok: hit });
}

/** GET /v1/admin/stats —— 看一眼用量 */
function handleStats(req, res) {
  const secret = req.headers['x-issue-secret'];
  const b = Buffer.from(CFG.issueSecret || '');
  const a = Buffer.from(String(secret || ''));
  if (!CFG.issueSecret || a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return send(res, 403, { error: { message: 'forbidden' } });
  }
  const all = [...tokens.values()];
  return send(res, 200, {
    tokens: all.length,
    active: all.filter((t) => !t.disabled).length,
    todayCalls: all.reduce((s, t) => s + (t.dayKey === dayKey() ? t.dailyCount : 0), 0),
  });
}

// ------------------------------------------------------------------ 启动

const server = http.createServer(async (req, res) => {
  // 只接受来自 App 的 JSON；拒绝一切跨域浏览器预检
  res.setHeader('Access-Control-Allow-Origin', 'null');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin': 'null', 'Access-Control-Allow-Headers': 'content-type,x-st-token' });
    return res.end();
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const p = url.pathname;

  try {
    if (p === '/v1/ai/analyze' && req.method === 'POST') return await handleAnalyze(req, res);
    if (p === '/v1/ai/health' && req.method === 'GET') return handleHealth(req, res);
    if (p === '/v1/admin/issue' && req.method === 'POST') return await handleIssue(req, res);
    if (p === '/v1/admin/revoke' && req.method === 'POST') {
      const id = url.searchParams.get('id') || '';
      return handleRevoke(req, res, id);
    }
    if (p === '/v1/admin/stats' && req.method === 'GET') return handleStats(req, res);
    if (p === '/') return send(res, 200, { ok: true, service: 'sewagetrace-gateway' });
    return send(res, 404, { error: { message: 'not_found' } });
  } catch (e) {
    console.error('[unhandled]', e);
    return send(res, 500, { error: { message: 'internal_error' } });
  }
});

loadTokens();
server.listen(CFG.port, '0.0.0.0', () => {
  console.log(`SewageTrace gateway listening on :${CFG.port}`);
  console.log(`  allowed models : ${CFG.allowedModels.join(', ')}`);
  console.log(`  quota          : ${CFG.dailyQuota}/day, ${CFG.perMinute}/min per token`);
  console.log(`  token store    : ${CFG.tokensFile}`);
  console.log(`  issue secret   : ${CFG.issueSecret ? 'set' : 'DISABLED (issue endpoint off)'}`);
});
