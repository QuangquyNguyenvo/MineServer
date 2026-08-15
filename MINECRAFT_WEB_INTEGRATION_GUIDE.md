# 🌐 Hướng Dẫn Tích Hợp Hiển Thị Trạng Thái & Người Chơi Minecraft Lên Web
> **Dành cho Lập trình viên & AI Coding Agent**  
> **Server Target:** `ancient.pikamc.vn:25238` | **Version:** `1.21.1 Fabric`

---

## 📌 1. Thông Số Kỹ Thuật Máy Chủ (Server Network Specs)

| Thuộc tính | Giá trị | Ghi chú |
| :--- | :--- | :--- |
| **Server Host** | `ancient.pikamc.vn` | Địa chỉ IP / Tên miền máy chủ |
| **Server Port** | `25238` | Cổng game Minecraft (Bắt buộc kèm port khi query) |
| **Full Address** | `ancient.pikamc.vn:25238` | Địa chỉ kết nối đầy đủ |
| **Server Type** | Fabric 1.21.1 | Hỗ trợ Server List Ping (SLP) & Query |
| **Status Protocol** | `enable-status=true` | Trạng thái công khai, cho phép Ping từ xa |
| **Query Protocol** | `enable-query=true` (Port `25238`) | Cho phép lấy chi tiết danh sách người chơi |

---

## ⚡ 2. REST API Truy Vấn Công Khai (Public Status APIs)

Không cần cài đặt backend hay plugin Minecraft, trang web có thể gọi trực tiếp các API REST công khai hỗ trợ CORS:

### 🔹 Endpoint Chính (Primary Endpoint - Khuyên Dùng):
```http
GET https://api.mcsrvstat.us/3/ancient.pikamc.vn:25238
```

### 🔹 Endpoint Dự Phòng (Fallbacks):
- **Minetools API:** `https://api.minetools.eu/ping/ancient.pikamc.vn/25238`
- **MCAPI:** `https://mcapi.us/server/status?ip=ancient.pikamc.vn&port=25238`

### 🔹 Dịch Vụ Lấy Avatar / Đầu Skin Người Chơi (Player Heads):
- **MC-Heads (2D):** `https://mc-heads.net/avatar/{username}/32`
- **MC-Heads (3D Isometric):** `https://mc-heads.net/head/{username}/32`
- **Minotar (2D):** `https://minotar.net/avatar/{username}/32`

---

## 📊 3. TypeScript Type Definition (Data Schema)

```typescript
export interface MinecraftPlayer {
    name: string;
    uuid: string;
}

export interface MinecraftServerStatus {
    online: boolean;
    ip: string;
    port: number;
    hostname?: string;
    version?: string;
    icon?: string; // Base64 encoded PNG
    motd?: {
        raw: string[];
        clean: string[];
        html: string[];
    };
    players?: {
        online: number;
        max: number;
        list?: MinecraftPlayer[];
    };
    debug?: {
        ping: boolean;
        query: boolean;
    };
}
```

---

## 💻 4. Các Mẫu Triển Khai (Implementation Stacks)

### 🌟 Mẫu 1: HTML + Vanilla CSS + JavaScript (Single-file Drop-in)

Dành cho website tĩnh, WordPress, Landing Page, hoặc nhúng nhanh:

```html
<!-- Widget HTML -->
<div id="mc-server-widget" class="mc-widget">
    <div class="mc-header">
        <div class="mc-status">
            <span id="mc-status-dot" class="status-dot"></span>
            <span id="mc-status-text">Đang tải...</span>
        </div>
        <button class="mc-copy-btn" onclick="copyServerIP()">
            <span id="mc-ip-display">ancient.pikamc.vn:25238</span>
            <span class="copy-tooltip">Sao chép</span>
        </button>
    </div>

    <div class="mc-count-wrap">
        <span class="count-label">Người chơi đang online</span>
        <span class="count-val"><strong id="mc-online-num">0</strong> / <span id="mc-max-num">20</span></span>
    </div>

    <div class="mc-progress-bar">
        <div id="mc-progress-fill" class="progress-fill" style="width: 0%;"></div>
    </div>

    <div id="mc-player-list" class="mc-player-list">
        <!-- Danh sách player avatar render tự động -->
    </div>
</div>

<style>
.mc-widget {
    background: #18181b;
    color: #f4f4f5;
    border: 1px solid #27272a;
    border-radius: 12px;
    padding: 18px;
    width: 320px;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    box-shadow: 0 10px 25px rgba(0,0,0,0.4);
}
.mc-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.mc-status { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; }
.status-dot { width: 9px; height: 9px; border-radius: 50%; background: #71717a; }
.status-dot.online { background: #22c55e; box-shadow: 0 0 10px #22c55e; }
.status-dot.offline { background: #ef4444; }
.mc-copy-btn {
    background: #27272a; border: 1px solid #3f3f46; color: #a1a1aa;
    padding: 5px 10px; border-radius: 6px; font-size: 11px; cursor: pointer;
    transition: all 0.2s;
}
.mc-copy-btn:hover { background: #3f3f46; color: #ffffff; }
.mc-count-wrap { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 6px; }
.count-label { color: #a1a1aa; }
.count-val strong { color: #22c55e; font-size: 16px; }
.mc-progress-bar { background: #27272a; height: 6px; border-radius: 3px; overflow: hidden; margin-bottom: 12px; }
.progress-fill { background: linear-gradient(90deg, #3b82f6, #22c55e); height: 100%; transition: width 0.4s ease; }
.mc-player-list { display: flex; flex-wrap: wrap; gap: 6px; max-height: 120px; overflow-y: auto; }
.player-tag {
    display: inline-flex; align-items: center; gap: 5px;
    background: #27272a; padding: 3px 8px; border-radius: 16px; font-size: 11px;
    border: 1px solid #3f3f46;
}
.player-head { width: 16px; height: 16px; border-radius: 3px; }
</style>

<script>
const MC_HOST = 'ancient.pikamc.vn:25238';

async function fetchMinecraftStatus() {
    try {
        const res = await fetch(`https://api.mcsrvstat.us/3/${MC_HOST}`);
        const data = await res.json();
        
        const dot = document.getElementById('mc-status-dot');
        const text = document.getElementById('mc-status-text');
        const onlineEl = document.getElementById('mc-online-num');
        const maxEl = document.getElementById('mc-max-num');
        const fill = document.getElementById('mc-progress-fill');
        const listEl = document.getElementById('mc-player-list');
        
        if (data.online) {
            dot.className = 'status-dot online';
            text.innerText = 'Trực tuyến';
            text.style.color = '#22c55e';
            
            const online = data.players ? data.players.online : 0;
            const max = data.players ? data.players.max : 20;
            
            onlineEl.innerText = online;
            maxEl.innerText = max;
            fill.style.width = `${Math.min(100, (online / max) * 100)}%`;
            
            listEl.innerHTML = '';
            if (data.players && data.players.list && data.players.list.length > 0) {
                data.players.list.forEach(p => {
                    const tag = document.createElement('div');
                    tag.className = 'player-tag';
                    tag.innerHTML = `
                        <img class="player-head" src="https://mc-heads.net/avatar/${p.name}/16" alt="${p.name}" loading="lazy">
                        <span>${p.name}</span>
                    `;
                    listEl.appendChild(tag);
                });
            } else if (online > 0) {
                listEl.innerHTML = '<span style="font-size:11px;color:#71717a;">Người chơi đang ẩn danh</span>';
            } else {
                listEl.innerHTML = '<span style="font-size:11px;color:#71717a;">Chưa có người chơi nào</span>';
            }
        } else {
            dot.className = 'status-dot offline';
            text.innerText = 'Ngoại tuyến';
            text.style.color = '#ef4444';
            onlineEl.innerText = '0';
            fill.style.width = '0%';
            listEl.innerHTML = '<span style="font-size:11px;color:#71717a;">Server đang bảo trì / tắt</span>';
        }
    } catch (e) {
        console.error('Failed to fetch MC status:', e);
    }
}

function copyServerIP() {
    navigator.clipboard.writeText(MC_HOST);
    alert('Đã sao chép IP: ' + MC_HOST);
}

// Chạy lần đầu và lặp lại mỗi 30 giây
fetchMinecraftStatus();
setInterval(fetchMinecraftStatus, 30000);
</script>
```

---

### ⚛️ Mẫu 2: React / Next.js Hook & Component (`useMinecraftServer.ts`)

#### 1. Custom Hook (`useMinecraftServer.ts`):
```typescript
import { useState, useEffect } from 'react';

export interface MinecraftPlayer {
    name: string;
    uuid: string;
}

export interface ServerState {
    online: boolean;
    onlinePlayers: number;
    maxPlayers: number;
    players: MinecraftPlayer[];
    version: string;
    loading: boolean;
}

export function useMinecraftServer(serverAddress: string = 'ancient.pikamc.vn:25238', intervalMs: number = 30000) {
    const [state, setState] = useState<ServerState>({
        online: false,
        onlinePlayers: 0,
        maxPlayers: 20,
        players: [],
        version: '',
        loading: true,
    });

    useEffect(() => {
        let isMounted = true;

        const checkStatus = async () => {
            try {
                const res = await fetch(`https://api.mcsrvstat.us/3/${serverAddress}`);
                const data = await res.json();
                if (!isMounted) return;

                if (data.online) {
                    setState({
                        online: true,
                        onlinePlayers: data.players?.online || 0,
                        maxPlayers: data.players?.max || 20,
                        players: data.players?.list || [],
                        version: data.version || '',
                        loading: false,
                    });
                } else {
                    setState({
                        online: false,
                        onlinePlayers: 0,
                        maxPlayers: 20,
                        players: [],
                        version: '',
                        loading: false,
                    });
                }
            } catch (err) {
                if (isMounted) setState(prev => ({ ...prev, online: false, loading: false }));
            }
        };

        checkStatus();
        const timer = setInterval(checkStatus, intervalMs);
        return () => {
            isMounted = false;
            clearInterval(timer);
        };
    }, [serverAddress, intervalMs]);

    return state;
}
```

#### 2. React Component (`MinecraftStatusCard.tsx`):
```tsx
import React from 'react';
import { useMinecraftServer } from './useMinecraftServer';

export const MinecraftStatusCard: React.FC = () => {
    const { online, onlinePlayers, maxPlayers, players, loading } = useMinecraftServer('ancient.pikamc.vn:25238');

    return (
        <div className="p-4 bg-zinc-900 border border-zinc-800 rounded-xl text-white w-80 shadow-lg">
            <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                    <span className={`w-2.5 h-2.5 rounded-full ${online ? 'bg-emerald-500 shadow-[0_0_8px_#10b981]' : 'bg-red-500'}`} />
                    <span className="text-xs font-semibold">{loading ? 'Đang kiểm tra...' : online ? 'Trực Tuyến' : 'Ngoại Tuyến'}</span>
                </div>
                <button
                    onClick={() => navigator.clipboard.writeText('ancient.pikamc.vn:25238')}
                    className="text-xs bg-zinc-800 hover:bg-zinc-700 px-2 py-1 rounded border border-zinc-700 text-zinc-300 transition-colors"
                >
                    Copy IP
                </button>
            </div>

            <div className="flex justify-between items-center text-sm mb-1.5">
                <span className="text-zinc-400 text-xs">Người chơi</span>
                <span className="font-bold text-emerald-400">{onlinePlayers} <span className="text-zinc-500 font-normal">/ {maxPlayers}</span></span>
            </div>

            <div className="w-full bg-zinc-800 h-1.5 rounded-full overflow-hidden mb-3">
                <div className="bg-emerald-500 h-full transition-all duration-300" style={{ width: `${(onlinePlayers / maxPlayers) * 100}%` }} />
            </div>

            <div className="flex flex-wrap gap-1.5 max-h-28 overflow-y-auto">
                {players.map((p) => (
                    <div key={p.uuid || p.name} className="flex items-center gap-1.5 bg-zinc-800/80 px-2 py-1 rounded-full text-xs border border-zinc-700">
                        <img src={`https://mc-heads.net/avatar/${p.name}/16`} alt={p.name} className="w-4 h-4 rounded" loading="lazy" />
                        <span>{p.name}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};
```

---

### 🟢 Mẫu 3: Backend Node.js / Express (Direct SLP Protocol)

Nếu web cần lấy dữ liệu trực tiếp từ backend không qua bên thứ ba:

```bash
npm install minecraft-server-util
```

```javascript
const express = require('express');
const util = require('minecraft-server-util');
const app = express();

const MC_HOST = 'ancient.pikamc.vn';
const MC_PORT = 25238;

app.get('/api/mc-status', async (req, res) => {
    try {
        const result = await util.status(MC_HOST, MC_PORT, { timeout: 5000, enableSRV: false });
        res.json({
            online: true,
            onlinePlayers: result.players.online,
            maxPlayers: result.players.max,
            samplePlayers: result.players.sample || [],
            motd: result.motd.clean,
            version: result.version.name,
            roundTripLatency: result.roundTripLatency
        });
    } catch (err) {
        res.json({ online: false, error: err.message });
    }
});

app.listen(3000, () => console.log('MC Status API running on port 3000'));
```

---

## 🛡️ 5. Các Lưu Ý Tối Ưu Cho Production (Best Practices)

1. **Tần suất Polling (Polling Interval):**
   - Đặt `setInterval` tối ưu từ **30s đến 60s**.
   - Tránh gọi API dưới **10s** để không bị rate-limit bởi CDN / Proxy.
2. **Khả năng dự phòng (Fallback Handling):**
   - Nếu `api.mcsrvstat.us` tạm thời timeout, tự động fallback sang `api.minetools.eu`.
3. **Hiển thị giao diện khi máy chủ tắt (Offline Graceful Degradation):**
   - Luôn hiển thị trạng thái `Ngoại tuyến / Đang bảo trì` rõ ràng, không để crash UI.
4. **Sao chép IP nhanh (1-Click Copy):**
   - Sử dụng `navigator.clipboard.writeText('ancient.pikamc.vn:25238')` để người chơi tiện vào game.
