#!/usr/bin/env python3
"""Hermes HTTP Bridge — Exposes Hermes agent over a simple HTTP API.

Provides POST /api/chat for synchronous chat and POST /api/chat/stream for SSE streaming.
This allows XClaw to interact with Hermes instances via HTTP instead of ACP TCP protocol.

Environment:
    HERMES_HTTP_PORT  — port (default 3100)
    HERMES_HTTP_HOST  — bind address (default 0.0.0.0)
"""

import asyncio
import json
import logging
import os
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
import threading

# Setup path
project_root = str(Path("/opt/hermes"))
if project_root not in sys.path:
    sys.path.insert(0, project_root)

# Activate venv
venv_site = Path("/opt/hermes/.venv/lib/python3.12/site-packages")
if venv_site.exists():
    sys.path.insert(0, str(venv_site))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    stream=sys.stderr,
)
logger = logging.getLogger("hermes-http-bridge")


def run_agent_sync(user_message: str, conversation_history: list | None = None) -> dict:
    """Run Hermes agent synchronously and return the result."""
    from run_agent import AIAgent

    # Resolve credentials from env vars (set by XClaw when starting the container)
    api_key = os.environ.get("OPENAI_API_KEY", "")
    base_url = os.environ.get("OPENAI_BASE_URL", "")
    model = os.environ.get("HERMES_MODEL", "")

    kwargs = {}
    if api_key:
        kwargs["api_key"] = api_key
    if base_url:
        kwargs["base_url"] = base_url
    if model:
        kwargs["model"] = model

    agent = AIAgent(**kwargs)
    result = agent.run_conversation(
        user_message=user_message,
        conversation_history=conversation_history or [],
    )
    return result


# Store session histories in memory
sessions: dict[str, list] = {}


class HermesHTTPHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self._json_response(200, {"status": "ok", "type": "hermes"})
        elif self.path == "/":
            self._html_response(200, HERMES_UI_HTML)
        else:
            self._json_response(404, {"error": "not found"})

    def do_POST(self):
        if self.path == "/api/chat":
            self._handle_chat()
        elif self.path == "/api/dispatch":
            # Support the old endpoint name for backward compatibility
            self._handle_chat()
        else:
            self._json_response(404, {"error": "not found"})

    def _handle_chat(self):
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            data = json.loads(body) if body else {}

            user_message = data.get("task", data.get("content", data.get("message", "")))
            session_key = data.get("sessionKey", "default")

            if not user_message:
                self._json_response(400, {"error": "missing 'task' or 'content' field"})
                return

            logger.info("Chat request [session=%s]: %s", session_key, user_message[:100])

            # Get or create session history
            if session_key not in sessions:
                sessions[session_key] = []
            history = sessions[session_key]

            # Run agent in a thread to avoid blocking
            result_container = {}
            error_container = {}

            def _run():
                try:
                    result_container["result"] = run_agent_sync(user_message, history)
                except Exception as e:
                    error_container["error"] = str(e)

            t = threading.Thread(target=_run)
            t.start()
            t.join(timeout=300)  # 5 min timeout

            if t.is_alive():
                self._json_response(504, {"error": "agent timeout"})
                return

            if "error" in error_container:
                self._json_response(500, {"error": error_container["error"]})
                return

            result = result_container["result"]
            # Extract the assistant response text
            response_text = ""
            if isinstance(result, dict):
                # AIAgent.run_conversation returns a dict with the final message
                response_text = result.get("content", result.get("message", result.get("response", "")))
                if not response_text and "messages" in result:
                    # Get last assistant message
                    for msg in reversed(result["messages"]):
                        if msg.get("role") == "assistant":
                            response_text = msg.get("content", "")
                            break
                if not response_text:
                    response_text = str(result)

            # Update session history
            history.append({"role": "user", "content": user_message})
            history.append({"role": "assistant", "content": response_text})

            self._json_response(200, {"result": response_text, "output": response_text, "status": "ok"})

        except json.JSONDecodeError:
            self._json_response(400, {"error": "invalid JSON"})
        except Exception as e:
            logger.exception("Error handling chat request")
            self._json_response(500, {"error": str(e)})

    def _json_response(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _html_response(self, code, html):
        body = html.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        logger.info(format, *args)


# Simple Hermes chat UI
HERMES_UI_HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Hermes Agent</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; height: 100vh; display: flex; flex-direction: column; background: #1a1a2e; color: #e0e0e0; }
.header { padding: 16px 24px; background: #16213e; border-bottom: 1px solid #0f3460; display: flex; align-items: center; gap: 12px; }
.header h1 { font-size: 18px; font-weight: 600; color: #e94560; }
.header .badge { background: #e94560; color: #fff; font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.messages { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; }
.msg { max-width: 80%; padding: 12px 16px; border-radius: 16px; line-height: 1.6; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.msg.user { align-self: flex-end; background: #0f3460; color: #e0e0e0; border-bottom-right-radius: 4px; }
.msg.assistant { align-self: flex-start; background: #2a2a4a; color: #e0e0e0; border-bottom-left-radius: 4px; }
.msg.error { align-self: center; background: #5c1a1a; color: #ff6b6b; font-size: 13px; }
.input-area { padding: 16px 24px; background: #16213e; border-top: 1px solid #0f3460; display: flex; gap: 12px; }
.input-area textarea { flex: 1; background: #1a1a2e; border: 1px solid #0f3460; color: #e0e0e0; border-radius: 12px; padding: 12px 16px; font-size: 14px; resize: none; outline: none; font-family: inherit; min-height: 44px; max-height: 120px; }
.input-area textarea:focus { border-color: #e94560; }
.input-area button { background: #e94560; color: #fff; border: none; border-radius: 12px; padding: 0 24px; font-size: 14px; cursor: pointer; font-weight: 600; transition: background 0.2s; }
.input-area button:hover { background: #c73850; }
.input-area button:disabled { background: #555; cursor: not-allowed; }
.typing { color: #888; font-style: italic; }
</style>
</head>
<body>
<div class="header">
  <h1>🤖 Hermes Agent</h1>
  <span class="badge">XClaw</span>
</div>
<div class="messages" id="messages">
  <div class="msg assistant">你好！我是 Hermes 智能助手，拥有终端、浏览器、代码执行等能力。有什么可以帮你的？</div>
</div>
<div class="input-area">
  <textarea id="input" placeholder="输入消息..." rows="1" onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();send()}"></textarea>
  <button id="sendBtn" onclick="send()">发送</button>
</div>
<script>
const msgs = document.getElementById('messages');
const input = document.getElementById('input');
const btn = document.getElementById('sendBtn');

function addMsg(role, text) {
  const div = document.createElement('div');
  div.className = 'msg ' + role;
  div.textContent = text;
  msgs.appendChild(div);
  msgs.scrollTop = msgs.scrollHeight;
  return div;
}

async function send() {
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  btn.disabled = true;
  addMsg('user', text);
  const typing = addMsg('assistant', '思考中...');
  typing.classList.add('typing');
  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({task: text, sessionKey: 'web'})
    });
    const data = await res.json();
    typing.classList.remove('typing');
    typing.textContent = data.result || data.output || data.error || '(无响应)';
  } catch (e) {
    typing.classList.remove('typing');
    typing.className = 'msg error';
    typing.textContent = '连接失败: ' + e.message;
  }
  btn.disabled = false;
  input.focus();
}
input.focus();
</script>
</body>
</html>
"""


def main():
    host = os.environ.get("HERMES_HTTP_HOST", "0.0.0.0")
    port = int(os.environ.get("HERMES_HTTP_PORT", os.environ.get("ACP_TCP_PORT", "3100")))

    # Load Hermes env
    try:
        from hermes_cli.env_loader import load_hermes_dotenv
        from hermes_constants import get_hermes_home
        hermes_home = get_hermes_home()
        load_hermes_dotenv(hermes_home=hermes_home)
        logger.info("Loaded Hermes env from %s", hermes_home)
    except Exception as e:
        logger.warning("Could not load Hermes env: %s", e)

    server = HTTPServer((host, port), HermesHTTPHandler)
    logger.info("Hermes HTTP Bridge listening on %s:%d", host, port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    server.server_close()


if __name__ == "__main__":
    main()
