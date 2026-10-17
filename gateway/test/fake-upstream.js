'use strict';
// 假上游：记录收到的 body，返回一个合法形状的回应，用于验证网关的转发与改写逻辑
const http = require('node:http');
const fs = require('node:fs');
const LOG = process.env.CAPTURE_LOG || './captured.json';

http.createServer((req, res) => {
  let raw = '';
  req.on('data', (c) => (raw += c));
  req.on('end', () => {
    let body = null;
    try { body = JSON.parse(raw); } catch { body = raw; }
    fs.appendFileSync(LOG, JSON.stringify({
      method: req.method,
      path: req.url,
      auth: req.headers.authorization || null,
      body,
    }) + '\n');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      choices: [{
        message: {
          role: 'assistant',
          content: JSON.stringify({
            is_water: true, index: 61, level: 'R3', confidence: 'medium',
            suspected_type: 'blackodorous', summary: 'turbid green water with foam',
            evidence: [{ dimension: 'color', observation: 'dark green', score: 70 }],
          }),
        },
        finish_reason: 'stop',
      }],
      usage: { total_tokens: 1234 },
    }));
  });
}).listen(Number(process.env.PORT || 8798), () => {
  console.log('fake upstream on', process.env.PORT || 8798);
});
