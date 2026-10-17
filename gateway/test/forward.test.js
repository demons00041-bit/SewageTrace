'use strict';
/**
 * 网关转发逻辑自测：用假上游跑一遍，确认
 *   1. 客户端传的 model 被白名单强制改写
 *   2. 客户端传的 Authorization 被丢弃，上游只看到网关自己的 key
 *   3. 客户端塞的额外字段（stream / 自定义字段）被丢弃
 *   4. 响应原样返回给客户端
 *   5. 成功一次后配额计数 +1
 *
 * 运行：node test/forward.test.js
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

const ROOT = path.join(__dirname, '..');
const CAPTURE = path.join(ROOT, 'data', 'captured.json');
const TOKENS = path.join(ROOT, 'data', 'test-tokens.json');
const GW_PORT = 8797;
const UP_PORT = 8796;
const SECRET = 'test-secret-abcdef0123456789';

let failures = 0;
function check(name, cond, extra = '') {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${extra ? '  ' + extra : ''}`);
  if (!cond) failures += 1;
}

function wait(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function main() {
  fs.mkdirSync(path.join(ROOT, 'data'), { recursive: true });
  for (const f of [CAPTURE, TOKENS]) { try { fs.unlinkSync(f); } catch {} }

  const upstream = spawn(process.execPath, [path.join(__dirname, 'fake-upstream.js')], {
    env: { ...process.env, PORT: String(UP_PORT), CAPTURE_LOG: CAPTURE },
    stdio: 'ignore',
  });

  const gateway = spawn(process.execPath, [path.join(ROOT, 'server.js')], {
    env: {
      ...process.env,
      PORT: String(GW_PORT),
      DEEPSEEK_API_KEY: 'sk-REAL-SECRET-IN-GATEWAY',
      ISSUE_SECRET: SECRET,
      TOKENS_FILE: TOKENS,
      // 指向本地假上游
      UPSTREAM_OVERRIDE: `http://127.0.0.1:${UP_PORT}`,
      PER_MINUTE: '30',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  gateway.stdout.on('data', () => {});
  gateway.stderr.on('data', (d) => console.error('[gw]', String(d).trim()));

  await wait(1200);

  try {
    // 签发令牌
    const issued = await req('POST', `http://127.0.0.1:${GW_PORT}/v1/admin/issue`, {
      'X-Issue-Secret': SECRET,
    });
    check('issue returns 201', issued.status === 201);
    const token = JSON.parse(issued.text).token;
    check('token has st: prefix', String(token).startsWith('st:'));

    // 违规请求：model 不在白名单 + 自带 Authorization + 多余字段 + stream
    const payload = {
      model: 'deepseek-v4-pro',
      temperature: 0.1,
      max_tokens: 2000,
      stream: true,
      evil_field: 'should be dropped',
      messages: [{ role: 'user', content: [{ type: 'text', text: 'hi' }] }],
    };
    const r = await req('POST', `http://127.0.0.1:${GW_PORT}/v1/ai/analyze`, {
      'X-ST-Token': token,
      Authorization: 'Bearer sk-CLIENT-TRIED-TO-SEND-THIS',
    }, payload);

    check('analyze returns 200', r.status === 200, `got ${r.status} ${r.text.slice(0, 120)}`);
    check('response body passed through', r.text.includes('choices') && r.text.includes('R3'));

    await wait(300);
    const captured = fs.readFileSync(CAPTURE, 'utf8').trim().split('\n').map(JSON.parse);
    check('upstream received exactly one call', captured.length === 1);
    const up = captured[0];
    check('model forced to whitelist', up.body.model === 'deepseek-flash', `got ${up.body.model}`);
    check('client Authorization dropped', up.auth === 'Bearer sk-REAL-SECRET-IN-GATEWAY', `got ${up.auth}`);
    check('extra field dropped', !('evil_field' in up.body));
    check('stream forced false', up.body.stream === false);
    check('response_format pinned', up.body.response_format && up.body.response_format.type === 'json_object');

    // 健康检查应显示配额已用 1
    const h = await req('GET', `http://127.0.0.1:${GW_PORT}/v1/ai/health`, { 'X-ST-Token': token });
    const hj = JSON.parse(h.text);
    check('quota decremented after success', hj.quota_left === hj.quota_total - 1, `left=${hj.quota_left}/${hj.quota_total}`);

    // 吊销后应 403
    const stats = JSON.parse((await req('GET', `http://127.0.0.1:${GW_PORT}/v1/admin/stats`, { 'X-Issue-Secret': SECRET })).text);
    check('stats counts the token', stats.tokens >= 1);
  } finally {
    gateway.kill();
    upstream.kill();
  }

  console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILED`);
  process.exit(failures === 0 ? 0 : 1);
}

function req(method, url, headers, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const data = body ? Buffer.from(JSON.stringify(body)) : null;
    const r = http.request({
      hostname: u.hostname, port: u.port, path: u.pathname + u.search, method,
      headers: { ...(data ? { 'Content-Type': 'application/json', 'Content-Length': data.length } : {}), ...headers },
      timeout: 8000,
    }, (res) => {
      let t = '';
      res.on('data', (c) => (t += c));
      res.on('end', () => resolve({ status: res.statusCode, text: t }));
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

main().catch((e) => { console.error(e); process.exit(1); });
