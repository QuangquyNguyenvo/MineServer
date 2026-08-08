#!/usr/bin/env bash
# Tự động đăng ký MCP server pikamc-agent cho Claude Code.
# Dùng làm "startup command" trong môi trường dev/cloud sandbox để không phải add tay mỗi lần.
set -euo pipefail

MCP_NAME="pikamc-agent"
MCP_URL="http://ancient.pikamc.vn:25240/mcp"
MCP_TOKEN="toiyeufembi"

if ! command -v claude >/dev/null 2>&1; then
  echo "[mcp-setup] Không tìm thấy lệnh 'claude' trong PATH, bỏ qua đăng ký MCP." >&2
  exit 0
fi

if claude mcp list 2>/dev/null | grep -q "^${MCP_NAME}"; then
  echo "[mcp-setup] MCP '${MCP_NAME}' đã được cấu hình, bỏ qua."
else
  claude mcp add --transport http "${MCP_NAME}" "${MCP_URL}" --scope local -H "Authorization: Bearer ${MCP_TOKEN}"
  echo "[mcp-setup] Đã thêm MCP '${MCP_NAME}'."
fi
