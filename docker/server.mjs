import express from 'express';
import http from 'http';
import https from 'https';
import { WebSocketServer, WebSocket } from 'ws';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;
const LLM_URL = process.env.OPENCLAW_LLM_URL || 'https://api.deepseek.com';
const LLM_KEY = process.env.OPENCLAW_LLM_KEY || '';
const LLM_MODEL = process.env.OPENCLAW_LLM_MODEL || 'deepseek-v4-pro';
const INSTANCE_NAME = process.env.INSTANCE_NAME || 'XClaw';
const GATEWAY_HOST = process.env.OPENCLAW_GATEWAY || 'localhost';
const GATEWAY_PORT = process.env.OPENCLAW_GATEWAY_PORT || '18789';

const app = express();
app.use(express.json());

// Static files - serve OpenClaw native control UI
const publicDir = path.join(__dirname, 'public');
app.use(express.static(publicDir));

// SPA fallback - serve index.html for all non-API routes
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/v1/') || req.path.startsWith('/health')) {
    return next();
  }
  res.sendFile(path.join(publicDir, 'index.html'));
});

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', model: LLM_MODEL, name: INSTANCE_NAME, port: PORT });
});

// Chat API (keep for backward compatibility with non-native chat)
app.post('/v1/chat/completions', async (req, res) => {
  try {
    const { messages, stream, model } = req.body;
    const finalModel = model || LLM_MODEL;
    const postData = JSON.stringify({ model: finalModel, messages, stream: stream !== false });

    const url = new URL('/v1/chat/completions', LLM_URL);
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${LLM_KEY}`
    };

    if (stream) {
      res.setHeader('Content-Type', 'text/event-stream');
      res.setHeader('Cache-Control', 'no-cache');
      res.setHeader('Connection', 'keep-alive');
      res.setHeader('X-Accel-Buffering', 'no');

      const llmReq = https.request(url, { method: 'POST', headers }, (llmRes) => {
        llmRes.pipe(res);
      });
      llmReq.on('error', (e) => {
        res.write(`data: ${JSON.stringify({ error: e.message })}\n\n`);
        res.end();
      });
      llmReq.write(postData);
      llmReq.end();
    } else {
      const llmReq = https.request(url, { method: 'POST', headers }, (llmRes) => {
        let body = '';
        llmRes.on('data', (chunk) => body += chunk);
        llmRes.on('end', () => {
          res.status(llmRes.statusCode).set('Content-Type', 'application/json').send(body);
        });
      });
      llmReq.on('error', (e) => res.status(500).json({ error: e.message }));
      llmReq.write(postData);
      llmReq.end();
    }
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const server = http.createServer(app);

// WebSocket proxy to OpenClaw gateway
const wss = new WebSocketServer({ server });

wss.on('connection', (clientWs, req) => {
  console.log(`[proxy] WS client connected, proxying to ${GATEWAY_HOST}:${GATEWAY_PORT}`);
  
  const targetWs = new WebSocket(`ws://${GATEWAY_HOST}:${GATEWAY_PORT}/`);
  let clientReady = false;
  let targetReady = false;

  // Buffer messages until both sides are ready
  const clientBuffer = [];
  const targetBuffer = [];

  targetWs.on('open', () => {
    targetReady = true;
    // Flush client buffer
    if (clientReady) {
      for (const msg of clientBuffer) targetWs.send(msg);
      clientBuffer.length = 0;
      for (const msg of targetBuffer) clientWs.send(msg);
      targetBuffer.length = 0;
    }
  });

  clientWs.on('message', (data) => {
    if (!targetReady && data) {
      clientBuffer.push(data);
    } else {
      targetWs.send(data);
    }
  });

  targetWs.on('message', (data) => {
    if (!clientReady) {
      targetBuffer.push(data);
    } else {
      clientWs.send(data);
    }
  });

  // Mark client as ready once it's fully connected
  clientReady = true;
  if (targetReady) {
    for (const msg of clientBuffer) targetWs.send(msg);
    clientBuffer.length = 0;
    for (const msg of targetBuffer) clientWs.send(msg);
    targetBuffer.length = 0;
  }

  clientWs.on('close', () => targetWs.close());
  targetWs.on('close', () => clientWs.close());
  
  clientWs.on('error', (e) => { console.error('[proxy] client WS error:', e.message); targetWs.close(); });
  targetWs.on('error', (e) => { console.error('[proxy] target WS error:', e.message); clientWs.close(); });
});

server.listen(PORT, () => {
  console.log(`OpenClaw Native UI on port ${PORT}, model=${LLM_MODEL}, gateway=${GATEWAY_HOST}:${GATEWAY_PORT}`);
});
