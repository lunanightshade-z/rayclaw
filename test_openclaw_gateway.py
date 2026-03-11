#!/usr/bin/env python3
"""
OpenClaw 外部命令行对话客户端（HTTP API 版）

特性：
- 支持 Gateway WebSocket RPC、OpenResponses (/v1/responses) 与 Chat Completions (/v1/chat/completions)
- 支持多轮会话（通过 user 字段保持会话关联）
- 默认 UTF-8 编码，避免中文乱码
- 从环境变量读取 token，避免明文写入脚本
- 支持流式输出（SSE）
- 现代化UI/UX设计，优雅的工具调用展示
- 自动隐藏工具调用中间信息，优先展示最终回答

用法示例：
  export OPENCLAW_GATEWAY_TOKEN="你的token"
  python3 openclaw_cli_chat.py --base-url http://127.0.0.1:18789 --endpoint responses --agent main
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.request
import uuid
from typing import Any, Dict, Iterable, List, Optional
from urllib.parse import urlparse, urlunparse
from dotenv import load_dotenv
import websockets

load_dotenv()

HTTP_ENDPOINT_RESPONSES = "responses"
HTTP_ENDPOINT_CHAT = "chat"
TRANSPORT_WS = "ws"
TRANSPORT_HTTP = "http"

def ensure_utf8_stdio() -> None:
    """强制标准输出使用 UTF-8，减少终端乱码概率。"""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")


class Terminal:
    """现代化终端UI组件库"""
    
    RESET = "\033[0m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    ITALIC = "\033[3m"
    
    class Color:
        BLACK = "\033[30m"
        RED = "\033[31m"
        GREEN = "\033[32m"
        YELLOW = "\033[33m"
        BLUE = "\033[34m"
        MAGENTA = "\033[35m"
        CYAN = "\033[36m"
        WHITE = "\033[37m"
        
        BR_BLACK = "\033[90m"
        BR_RED = "\033[91m"
        BR_GREEN = "\033[92m"
        BR_YELLOW = "\033[93m"
        BR_BLUE = "\033[94m"
        BR_MAGENTA = "\033[95m"
        BR_CYAN = "\033[96m"
        BR_WHITE = "\033[97m"
    
    class BG:
        BLACK = "\033[40m"
        RED = "\033[41m"
        GREEN = "\033[42m"
        YELLOW = "\033[43m"
        BLUE = "\033[44m"
        MAGENTA = "\033[45m"
        CYAN = "\033[46m"
        WHITE = "\033[47m"
    
    def __init__(self, no_color: bool = False):
        self.no_color = no_color
    
    def _format(self, text: str, *codes: str) -> str:
        if self.no_color:
            return text
        return "".join(codes) + text + self.RESET
    
    def success(self, text: str) -> str:
        return self._format(text, self.Color.BR_GREEN, self.BOLD)
    
    def error(self, text: str) -> str:
        return self._format(text, self.Color.BR_RED, self.BOLD)
    
    def warning(self, text: str) -> str:
        return self._format(text, self.Color.BR_YELLOW, self.BOLD)
    
    def info(self, text: str) -> str:
        return self._format(text, self.Color.BR_CYAN)
    
    def user_prompt(self, text: str) -> str:
        return self._format(text, self.Color.BR_CYAN, self.BOLD)
    
    def ai_prompt(self, text: str) -> str:
        return self._format(text, self.Color.BR_MAGENTA, self.BOLD)
    
    def dim(self, text: str) -> str:
        return self._format(text, self.Color.BR_BLACK)
    
    def tool_start(self, tool_name: str) -> str:
        badge = self._format(f" {tool_name.upper()} ", self.BG.BLUE, self.Color.WHITE, self.BOLD)
        return f"{badge} {self._format('执行中...', self.Color.BR_BLUE)}"
    
    def tool_done(self, tool_name: str) -> str:
        badge = self._format(f" {tool_name.upper()} ", self.BG.GREEN, self.Color.WHITE, self.BOLD)
        return f"{badge} {self._format('完成', self.Color.BR_GREEN)}"
    
    def divider(self) -> str:
        return self._format("─" * 50, self.Color.BR_BLACK)
    
    def box(self, title: str, lines: List[str]) -> str:
        result = []
        result.append(self._format("┌" + "─" * (len(title) + 2) + "┐", self.Color.BR_BLACK))
        result.append(self._format("│ ", self.Color.BR_BLACK) + self._format(title, self.Color.BR_WHITE, self.BOLD) + self._format(" │", self.Color.BR_BLACK))
        result.append(self._format("├" + "─" * (len(title) + 2) + "┤", self.Color.BR_BLACK))
        for line in lines:
            result.append(self._format("│ ", self.Color.BR_BLACK) + line + self._format(" │", self.Color.BR_BLACK))
        result.append(self._format("└" + "─" * (len(title) + 2) + "┘", self.Color.BR_BLACK))
        return "\n".join(result)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="OpenClaw HTTP 命令行对话客户端")
    parser.add_argument(
        "--transport",
        choices=[TRANSPORT_WS, TRANSPORT_HTTP],
        default=os.getenv("OPENCLAW_TRANSPORT", TRANSPORT_WS),
        help="传输方式：ws=Gateway WebSocket RPC（推荐），http=兼容 HTTP API",
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("OPENCLAW_BASE_URL", "http://127.0.0.1:18789"),
        help="Gateway 基础地址，例如 http://127.0.0.1:18789",
    )
    parser.add_argument(
        "--endpoint",
        choices=[HTTP_ENDPOINT_RESPONSES, HTTP_ENDPOINT_CHAT],
        default=os.getenv("OPENCLAW_HTTP_ENDPOINT", HTTP_ENDPOINT_RESPONSES),
        help="HTTP 端点类型：responses=/v1/responses（推荐），chat=/v1/chat/completions（仅兼容纯文本）",
    )
    parser.add_argument(
        "--agent",
        default=os.getenv("OPENCLAW_AGENT_ID", "main"),
        help="OpenClaw agent id，例如 main",
    )
    parser.add_argument(
        "--token",
        default=os.getenv("OPENCLAW_GATEWAY_TOKEN", ""),
        help="Gateway token（建议用环境变量 OPENCLAW_GATEWAY_TOKEN）",
    )
    parser.add_argument(
        "--user",
        default=os.getenv("OPENCLAW_CHAT_USER", f"cli-{secrets.token_hex(4)}"),
        help="用于会话关联的 user 标识（固定值可保持多轮上下文）",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=float(os.getenv("OPENCLAW_HTTP_TIMEOUT", "120")),
        help="请求超时（秒）",
    )
    parser.add_argument(
        "--no-color",
        action="store_true",
        help="禁用彩色输出",
    )
    parser.add_argument(
        "--no-stream",
        action="store_true",
        help="禁用流式输出（默认启用流式）",
    )
    fallback_group = parser.add_mutually_exclusive_group()
    fallback_group.add_argument(
        "--fallback-chat",
        action="store_true",
        help="responses 遇到客户端 function_call 时，允许回退到 chat 端点获取纯文本回答",
    )
    fallback_group.add_argument(
        "--no-fallback-chat",
        action="store_true",
        help="显式关闭 fallback-chat（默认关闭，仅保留兼容参数）",
    )
    args = parser.parse_args()
    env_fallback = os.getenv("OPENCLAW_AUTO_FALLBACK_CHAT", "").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }
    args.auto_fallback_chat = bool(args.fallback_chat or (env_fallback and not args.no_fallback_chat))
    return args


class OpenClawHttpClient:
    def __init__(
        self,
        base_url: str,
        endpoint: str,
        agent_id: str,
        token: str,
        user: str,
        timeout: float,
        transport: str = TRANSPORT_WS,
        auto_fallback_chat: bool = False,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.endpoint = endpoint
        self.agent_id = agent_id
        self.token = token.strip()
        self.user = user
        self.timeout = timeout
        self.transport = transport
        self.auto_fallback_chat = auto_fallback_chat
        self._session_verbose_patched: set[str] = set()

    @property
    def api_path(self) -> str:
        return (
            "/v1/responses"
            if self.endpoint == HTTP_ENDPOINT_RESPONSES
            else "/v1/chat/completions"
        )

    @property
    def api_url(self) -> str:
        return f"{self.base_url}{self.api_path}"

    @property
    def ws_url(self) -> str:
        return self._to_ws_url(self.base_url)

    def _to_ws_url(self, raw_url: str) -> str:
        parsed = urlparse(raw_url)
        scheme = parsed.scheme.lower()
        if scheme in {"ws", "wss"}:
            return raw_url
        if scheme == "http":
            next_scheme = "ws"
        elif scheme == "https":
            next_scheme = "wss"
        else:
            next_scheme = "ws"
        return urlunparse((next_scheme, parsed.netloc, parsed.path or "", "", "", ""))

    def _normalize_token(self, value: str) -> str:
        normalized = re.sub(r"[^a-zA-Z0-9_-]+", "-", value.strip()).strip("-").lower()
        return normalized or f"cli-{secrets.token_hex(4)}"

    def _build_session_key(self) -> str:
        return f"agent:{self.agent_id}:cli-user:{self._normalize_token(self.user)}"

    def _tool_call_notice(self) -> str:
        return (
            "检测到 `function_call`。当前客户端不会在 HTTP 侧执行客户端工具，"
            "已保留工具调用语义且未自动降级为 chat 文本兼容模式。"
            "如需强制回退纯文本，可加 `--fallback-chat`；如需完整工具能力，优先使用 `--transport ws`。"
        )

    def _http_chat_warning(self) -> str:
        return (
            "当前使用 `/v1/chat/completions` 兼容端点。"
            "OpenClaw 这条链路主要用于文本兼容，不适合依赖 tool calling 的场景。"
        )

    def _extract_message_text(self, message: Any) -> str:
        if isinstance(message, dict):
            content = message.get("content")
            if isinstance(content, str):
                return content.strip()
            if isinstance(content, list):
                texts: List[str] = []
                for item in content:
                    if isinstance(item, dict):
                        txt = item.get("text")
                        if isinstance(txt, str) and txt:
                            texts.append(txt)
                if texts:
                    return "\n".join(texts).strip()
            txt = message.get("text")
            if isinstance(txt, str):
                return txt.strip()
        return ""

    def _extract_tool_result_text(self, result: Any) -> str:
        if isinstance(result, dict):
            content = result.get("content")
            if isinstance(content, list):
                parts: List[str] = []
                for item in content:
                    if isinstance(item, dict):
                        txt = item.get("text")
                        if isinstance(txt, str) and txt:
                            parts.append(txt)
                if parts:
                    return "\n".join(parts).strip()
        if isinstance(result, str):
            return result.strip()
        try:
            return json.dumps(result, ensure_ascii=False, indent=2)
        except Exception:
            return str(result).strip()

    async def _ws_send_req(self, ws: Any, req_id: str, method: str, params: Optional[Dict[str, Any]] = None) -> None:
        frame = {"type": "req", "id": req_id, "method": method}
        if params is not None:
            frame["params"] = params
        await ws.send(json.dumps(frame, ensure_ascii=False))

    async def _ask_via_ws(self, text: str, stream: bool) -> str:
        session_key = self._build_session_key()
        run_id = f"cli-run-{uuid.uuid4().hex}"
        connect_req_id = f"connect-{uuid.uuid4().hex}"
        patch_req_id = f"patch-{uuid.uuid4().hex}"
        chat_req_id = f"chat-{uuid.uuid4().hex}"
        assistant_text = ""
        final_text = ""
        final_state: Optional[str] = None
        patch_sent = False
        chat_sent = False
        streamed_assistant_output = False
        streamed_text = ""

        async with websockets.connect(
            self.ws_url,
            open_timeout=self.timeout,
            close_timeout=3,
            max_size=25 * 1024 * 1024,
        ) as ws:
            started_at = time.monotonic()
            while True:
                remaining = self.timeout - (time.monotonic() - started_at)
                if remaining <= 0:
                    raise RuntimeError("WebSocket 请求超时")

                raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
                obj = json.loads(raw)
                frame_type = obj.get("type")

                if frame_type == "event":
                    event_name = obj.get("event")
                    payload = obj.get("payload")

                    if event_name == "connect.challenge":
                        connect_params = {
                            "minProtocol": 3,
                            "maxProtocol": 3,
                            "client": {
                                "id": "gateway-client",
                                "displayName": "external-cli",
                                "version": "1.0",
                                "platform": sys.platform,
                                "mode": "ui",
                                "instanceId": f"cli-{uuid.uuid4().hex}",
                            },
                            "caps": ["tool-events"],
                            "role": "operator",
                            "scopes": ["operator.admin"],
                        }
                        if self.token:
                            connect_params["auth"] = {"token": self.token}
                        await self._ws_send_req(
                            ws,
                            connect_req_id,
                            "connect",
                            connect_params,
                        )
                        continue

                    if event_name == "chat" and isinstance(payload, dict):
                        if payload.get("runId") != run_id:
                            continue
                        state = payload.get("state")
                        message = payload.get("message")
                        text_now = self._extract_message_text(message)

                        if state == "delta" and text_now:
                            if stream:
                                if text_now.startswith(assistant_text):
                                    suffix = text_now[len(assistant_text):]
                                elif assistant_text.startswith(text_now):
                                    # 某些网关会回传较短快照，避免倒退导致重复打印
                                    suffix = ""
                                else:
                                    suffix = text_now
                                if suffix:
                                    print(suffix, end="", flush=True)
                                    streamed_text += suffix
                                    streamed_assistant_output = True
                            assistant_text = text_now
                            continue

                        if state == "final":
                            final_state = "final"
                            final_text = text_now or assistant_text
                            break

                        if state in {"error", "aborted"}:
                            final_state = str(state)
                            err = payload.get("errorMessage") or f"run {state}"
                            raise RuntimeError(str(err))

                    if event_name == "agent" and isinstance(payload, dict):
                        if payload.get("runId") != run_id:
                            continue
                        if payload.get("stream") != "tool":
                            continue
                        continue

                elif frame_type == "res":
                    if obj.get("id") == connect_req_id:
                        if not obj.get("ok"):
                            raise RuntimeError(f"connect 失败: {obj.get('error')}")
                        if session_key not in self._session_verbose_patched and not patch_sent:
                            patch_sent = True
                            await self._ws_send_req(
                                ws,
                                patch_req_id,
                                "sessions.patch",
                                {"key": session_key, "verboseLevel": "full"},
                            )
                        else:
                            await self._ws_send_req(
                                ws,
                                chat_req_id,
                                "chat.send",
                                {
                                    "sessionKey": session_key,
                                    "message": text,
                                    "idempotencyKey": run_id,
                                    "timeoutMs": int(self.timeout * 1000),
                                },
                            )
                            chat_sent = True
                        continue

                    if obj.get("id") == patch_req_id:
                        if obj.get("ok"):
                            self._session_verbose_patched.add(session_key)
                        if not chat_sent:
                            await self._ws_send_req(
                                ws,
                                chat_req_id,
                                "chat.send",
                                {
                                    "sessionKey": session_key,
                                    "message": text,
                                    "idempotencyKey": run_id,
                                    "timeoutMs": int(self.timeout * 1000),
                                },
                            )
                            chat_sent = True
                        continue

                    if obj.get("id") == chat_req_id:
                        if not obj.get("ok"):
                            raise RuntimeError(f"chat.send 失败: {obj.get('error')}")
                        continue

            merged = (final_text or assistant_text)
            merged_stripped = merged.strip()
            if stream and streamed_assistant_output:
                # 如果最终文本比流式增量更完整，补打印缺失尾部，避免回答被截断
                if merged:
                    if merged.startswith(streamed_text):
                        tail = merged[len(streamed_text):]
                    elif merged != streamed_text:
                        tail = merged
                    else:
                        tail = ""
                    if tail:
                        print(tail, end="", flush=True)
                print()
                return ""
            if merged_stripped:
                return merged_stripped
            if not merged_stripped and final_state == "final":
                return "（已完成，但没有可显示的最终文本）"
            return merged_stripped

    def build_payload(self, text: str) -> Dict[str, Any]:
        model_name = f"openclaw:{self.agent_id}"
        if self.endpoint == HTTP_ENDPOINT_RESPONSES:
            return {
                "model": model_name,
                "input": text,
                "user": self.user,
            }
        return {
            "model": model_name,
            "messages": [{"role": "user", "content": text}],
            "user": self.user,
        }

    def _build_payload_for_endpoint(self, text: str, endpoint: str, stream: bool) -> Dict[str, Any]:
        model_name = f"openclaw:{self.agent_id}"
        if endpoint == HTTP_ENDPOINT_RESPONSES:
            return {
                "model": model_name,
                "input": text,
                "user": self.user,
                "stream": stream,
            }
        return {
            "model": model_name,
            "messages": [{"role": "user", "content": text}],
            "user": self.user,
            "stream": stream,
        }

    def _extract_text_from_responses(self, obj: Dict[str, Any]) -> str:
        if isinstance(obj.get("output_text"), str) and obj["output_text"].strip():
            return obj["output_text"]

        output = obj.get("output")
        if isinstance(output, list):
            texts: List[str] = []
            for item in output:
                if not isinstance(item, dict):
                    continue
                content = item.get("content")
                if isinstance(content, list):
                    for part in content:
                        if not isinstance(part, dict):
                            continue
                        txt = part.get("text")
                        if isinstance(txt, str) and txt.strip():
                            texts.append(txt)
                txt2 = item.get("text")
                if isinstance(txt2, str) and txt2.strip():
                    texts.append(txt2)
            if texts:
                return "\n".join(texts).strip()

        return json.dumps(obj, ensure_ascii=False, indent=2)

    def _extract_text_from_chat(self, obj: Dict[str, Any]) -> str:
        choices = obj.get("choices")
        if isinstance(choices, list) and choices:
            first = choices[0]
            if isinstance(first, dict):
                msg = first.get("message")
                if isinstance(msg, dict):
                    content = msg.get("content")
                    if isinstance(content, str) and content.strip():
                        return content
        return json.dumps(obj, ensure_ascii=False, indent=2)

    def _sanitize_tool_blocks(self, text: str) -> str:
        if not text:
            return text
        patterns = [
            r"```exec[\s\S]*?```",
            r"```tool[\s\S]*?```",
            r"```bash[\s\S]*?```",
        ]
        cleaned = text
        for p in patterns:
            cleaned = re.sub(p, "", cleaned, flags=re.IGNORECASE)
        return cleaned.strip() or text.strip()

    def _parse_sse_lines(self, resp: Any) -> Iterable[Dict[str, Any]]:
        event_name: Optional[str] = None
        data_lines: List[str] = []
        for raw in resp:
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if not line:
                if not data_lines:
                    event_name = None
                    continue
                data_payload = "\n".join(data_lines)
                data_lines = []
                if data_payload == "[DONE]":
                    yield {"__done__": True}
                else:
                    try:
                        obj = json.loads(data_payload)
                    except json.JSONDecodeError:
                        obj = {"raw": data_payload}
                    if event_name:
                        obj["__event__"] = event_name
                    yield obj
                event_name = None
                continue
            if line.startswith(":"):
                continue
            if line.startswith("event:"):
                event_name = line.split(":", 1)[1].strip()
                continue
            if line.startswith("data:"):
                data_lines.append(line.split(":", 1)[1].lstrip())

    def _stream_http(self, text: str, endpoint: str) -> str:
        payload = self._build_payload_for_endpoint(text=text, endpoint=endpoint, stream=True)
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        path = "/v1/responses" if endpoint == HTTP_ENDPOINT_RESPONSES else "/v1/chat/completions"
        url = f"{self.base_url}{path}"
        headers = {
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "text/event-stream",
            "x-openclaw-agent-id": self.agent_id,
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        req = urllib.request.Request(url, data=body, headers=headers, method="POST")
        chunks: List[str] = []
        saw_tool_call = False
        streamed_printed = False

        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            for evt in self._parse_sse_lines(resp):
                if evt.get("__done__"):
                    break
                if endpoint == HTTP_ENDPOINT_CHAT:
                    choices = evt.get("choices")
                    if isinstance(choices, list) and choices:
                        delta = choices[0].get("delta") if isinstance(choices[0], dict) else None
                        if isinstance(delta, dict):
                            content = delta.get("content")
                            if isinstance(content, str) and content:
                                print(content, end="", flush=True)
                                chunks.append(content)
                                streamed_printed = True
                else:
                    event_type = evt.get("type") or evt.get("__event__")
                    if event_type == "response.output_text.delta":
                        delta = evt.get("delta")
                        if isinstance(delta, str) and delta:
                            print(delta, end="", flush=True)
                            chunks.append(delta)
                            streamed_printed = True
                    elif event_type in {"response.output_item.added", "response.output_item.done"}:
                        item = evt.get("item")
                        if isinstance(item, dict) and item.get("type") == "function_call":
                            saw_tool_call = True
                    elif event_type == "response.completed":
                        resp_obj = evt.get("response")
                        if isinstance(resp_obj, dict) and resp_obj.get("status") == "incomplete":
                            saw_tool_call = True

        print()
        merged = "".join(chunks).strip()
        merged = self._sanitize_tool_blocks(merged)
        if merged and streamed_printed:
            return ""
        if merged:
            return merged

        if endpoint == HTTP_ENDPOINT_RESPONSES and saw_tool_call and self.auto_fallback_chat:
            return self.ask(text=text, stream=False, force_endpoint=HTTP_ENDPOINT_CHAT)

        if endpoint == HTTP_ENDPOINT_RESPONSES and saw_tool_call:
            return self._tool_call_notice()

        return ""

    def ask(self, text: str, stream: bool = False, force_endpoint: Optional[str] = None) -> str:
        if self.transport == TRANSPORT_WS:
            return asyncio.run(self._ask_via_ws(text=text, stream=stream))

        endpoint = force_endpoint or self.endpoint
        if stream:
            return self._stream_http(text=text, endpoint=endpoint)

        payload = self._build_payload_for_endpoint(text=text, endpoint=endpoint, stream=False)
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        path = "/v1/responses" if endpoint == HTTP_ENDPOINT_RESPONSES else "/v1/chat/completions"
        headers = {
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json; charset=utf-8",
            "x-openclaw-agent-id": self.agent_id,
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        req = urllib.request.Request(
            f"{self.base_url}{path}",
            data=body,
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read()
                text_body = raw.decode("utf-8", errors="replace")
                data = json.loads(text_body)
                if endpoint == HTTP_ENDPOINT_RESPONSES:
                    status = data.get("status")
                    output = data.get("output")
                    has_tool_call = (
                        status == "incomplete"
                        and isinstance(output, list)
                        and any(isinstance(it, dict) and it.get("type") == "function_call" for it in output)
                    )
                    if has_tool_call:
                        if self.auto_fallback_chat:
                            return self.ask(text=text, stream=False, force_endpoint=HTTP_ENDPOINT_CHAT)
                        return self._tool_call_notice()
                    return self._sanitize_tool_blocks(self._extract_text_from_responses(data))
                return self._sanitize_tool_blocks(self._extract_text_from_chat(data))
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {e.code}: {err_body}") from e
        except urllib.error.URLError as e:
            raise RuntimeError(f"网络错误: {e}") from e
        except json.JSONDecodeError as e:
            raise RuntimeError(f"响应不是合法 JSON: {e}") from e


def run_repl(client: OpenClawHttpClient, no_color: bool, stream: bool) -> None:
    term = Terminal(no_color=no_color)
    
    print()
    print(term.success("✨ OpenClaw CLI Chat 已连接"))
    print(term.divider())
    print(f"  {term.info('🌐 地址')}: {client.ws_url if client.transport == TRANSPORT_WS else client.api_url}")
    print(f"  {term.info('🤖 Agent')}: {client.agent_id}")
    print(f"  {term.info('📡 传输')}: {client.transport.upper()}", end="")
    if client.transport == TRANSPORT_HTTP:
        print(f" | {term.info('🔌 端点')}: {client.endpoint}", end="")
    print()
    print(f"  {term.info('👤 用户')}: {client.user}")
    print(f"  {term.info('⚡ 流式')}: {'✓ 启用' if stream else '✗ 禁用'}")
    
    if client.transport == TRANSPORT_HTTP and client.endpoint == HTTP_ENDPOINT_CHAT:
        print(f"  {term.warning('⚠ 提示')}: {client._http_chat_warning()}")
    
    if client.transport == TRANSPORT_HTTP and client.endpoint == HTTP_ENDPOINT_RESPONSES:
        mode_text = "✓ 启用" if client.auto_fallback_chat else "✗ 禁用"
        print(f"  {term.info('🔄 自动回退')}: {mode_text}")
    
    print(term.divider())
    print(f"  输入 {term.info('/help')} 查看命令，{term.info('/exit')} 退出")
    print()

    cmd_help = f"""
  {term.info('/help')}            查看此帮助
  {term.info('/exit')}            退出会话
  {term.info('/endpoint')} <类型> 切换端点 (chat / responses)
  {term.info('/agent')} <id>      切换 agent ID
  {term.info('/user')} <标识>     切换会话用户标识
  {term.info('/stream')} <on/off> 切换流式输出
"""

    while True:
        try:
            prompt = term.user_prompt("你> ")
            user_input = input(prompt).strip()
        except (EOFError, KeyboardInterrupt):
            print()
            print(term.dim("已退出。"))
            return

        if not user_input:
            continue
        
        if user_input in {"/exit", "/quit"}:
            print(term.success("✓ 已安全退出"))
            return
        
        if user_input == "/help":
            print(cmd_help)
            continue
        
        if user_input.startswith("/stream "):
            val = user_input.split(" ", 1)[1].strip().lower()
            if val in {"on", "true", "1"}:
                stream = True
                print(term.success("✓ 流式输出已启用"))
            elif val in {"off", "false", "0"}:
                stream = False
                print(term.success("✓ 流式输出已禁用"))
            else:
                print(term.error("✗ 用法: /stream on|off"))
            continue
        
        if user_input.startswith("/endpoint "):
            ep = user_input.split(" ", 1)[1].strip()
            if ep in {HTTP_ENDPOINT_CHAT, HTTP_ENDPOINT_RESPONSES}:
                client.endpoint = ep
                print(term.success(f"✓ 端点已切换为: {ep}"))
                if client.transport == TRANSPORT_HTTP and client.endpoint == HTTP_ENDPOINT_CHAT:
                    print(term.warning(f"⚠ {client._http_chat_warning()}"))
            else:
                print(term.error(f"✗ 端点仅支持: {', '.join([HTTP_ENDPOINT_CHAT, HTTP_ENDPOINT_RESPONSES])}"))
            continue
        
        if user_input.startswith("/agent "):
            agent = user_input.split(" ", 1)[1].strip()
            if agent:
                client.agent_id = agent
                print(term.success(f"✓ Agent 已切换为: {agent}"))
            continue
        
        if user_input.startswith("/user "):
            user_id = user_input.split(" ", 1)[1].strip()
            if user_id:
                client.user = user_id
                print(term.success(f"✓ 用户标识已切换为: {user_id}"))
            continue

        t0 = time.time()
        try:
            if stream:
                print(term.ai_prompt("AI> "), end="", flush=True)
            
            answer = client.ask(user_input, stream=stream)
            dt = time.time() - t0
            
            if not stream:
                print(term.ai_prompt("AI> ") + answer)
            elif answer:
                print(answer)
            
            print(term.dim(f"[{dt:.2f}s]\n"))
        except Exception as e:
            print(term.error(f"✗ 请求失败: {e}\n"))


def main() -> None:
    ensure_utf8_stdio()
    args = parse_args()
    client = OpenClawHttpClient(
        base_url=args.base_url,
        endpoint=args.endpoint,
        agent_id=args.agent,
        token=args.token,
        user=args.user,
        timeout=args.timeout,
        transport=args.transport,
        auto_fallback_chat=args.auto_fallback_chat,
    )
    run_repl(client, no_color=args.no_color, stream=not args.no_stream)


if __name__ == "__main__":
    main()
