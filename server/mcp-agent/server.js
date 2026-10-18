// PikaMC MCP agent v2 - dieu khien server Minecraft qua RCON cho Claude Code / Antigravity.
//
// Restart an toan: agent KHONG tu goi panel. No ghi file co .restart-flag roi gui "stop";
// test-mcp.sh chay java trong vong lap, thay co thi bat lai java (container + agent van song).
// Khong co co (vd bam Stop tren panel) thi script thoat binh thuong nhu truoc.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import http from "node:http";
import net from "node:net";
import { z } from "zod";
import { McpServer, createMcpHandler } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import { fileURLToPath } from "node:url";

const VERSION = "2.0.0";
const PORT = Number(process.env.MCP_PORT || 25240);
const AGENT_DIR = path.dirname(fileURLToPath(import.meta.url));
const SERVER_DIR = path.resolve(AGENT_DIR, "..");
const TOKEN_FILE = path.join(AGENT_DIR, ".token");
const RCON_TOKEN_FILE = path.join(AGENT_DIR, ".rcon_password");
const RESTART_FLAG = path.join(AGENT_DIR, ".restart-flag");
const LOG_FILE = path.join(SERVER_DIR, "logs", "latest.log");
const RCON_HOST = "127.0.0.1";
const RCON_PORT = Number(process.env.RCON_PORT || 25575);
// test-mcp.sh moi dat bien nay -> biet chac vong lap restart dang hoat dong
const SUPERVISED = process.env.AGENT_SUPERVISED === "1";

const log = (...a) => console.log(`[agent ${new Date().toISOString()}]`, ...a);

// ---------------------------------------------------------------- auth
// Doc lai token khi file doi (doi token khong can restart agent)
let tokenCache = { mtime: 0, value: null };
function currentToken() {
    if (process.env.MCP_TOKEN) return process.env.MCP_TOKEN.trim();
    try {
        const st = fs.statSync(TOKEN_FILE);
        if (st.mtimeMs !== tokenCache.mtime) {
            tokenCache = { mtime: st.mtimeMs, value: fs.readFileSync(TOKEN_FILE, "utf8").trim() };
        }
    } catch {
        // giu token cu neu tam thoi khong doc duoc
    }
    return tokenCache.value;
}
if (!currentToken()) {
    console.error(`FATAL: no MCP_TOKEN env var and cannot read ${TOKEN_FILE}`);
    process.exit(1);
}

// Chan do token: qua 10 lan sai / 10 phut / IP -> tra 429
const failedAuth = new Map();
const FAIL_LIMIT = 10;
const FAIL_WINDOW_MS = 10 * 60 * 1000;
function clientIp(req) {
    return req.socket.remoteAddress || "?";
}
function isBlocked(ip) {
    const e = failedAuth.get(ip);
    if (!e) return false;
    if (Date.now() - e.first > FAIL_WINDOW_MS) {
        failedAuth.delete(ip);
        return false;
    }
    return e.count >= FAIL_LIMIT;
}
function recordFail(ip) {
    const e = failedAuth.get(ip);
    if (!e || Date.now() - e.first > FAIL_WINDOW_MS) failedAuth.set(ip, { first: Date.now(), count: 1 });
    else e.count++;
}
function isAuthorized(req) {
    const header = req.headers["authorization"] || "";
    if (!header.startsWith("Bearer ")) return false;
    const expected = Buffer.from(currentToken() || "");
    const got = Buffer.from(header.slice(7));
    if (expected.length === 0 || expected.length !== got.length) return false;
    return crypto.timingSafeEqual(expected, got);
}

// ---------------------------------------------------------------- rcon
function loadRconPassword() {
    if (process.env.RCON_PASSWORD) return process.env.RCON_PASSWORD.trim();
    try {
        return fs.readFileSync(RCON_TOKEN_FILE, "utf8").trim();
    } catch {
        return null;
    }
}
const RCON_PASSWORD = loadRconPassword();

function buildRconPacket(id, type, body) {
    const bodyBuf = Buffer.from(body, "utf8");
    const size = 4 + 4 + bodyBuf.length + 2;
    const packet = Buffer.alloc(4 + size);
    packet.writeInt32LE(size, 0);
    packet.writeInt32LE(id, 4);
    packet.writeInt32LE(type, 8);
    bodyBuf.copy(packet, 12);
    return packet;
}

function rconCommand(command, timeoutMs = 15000) {
    return new Promise((resolve, reject) => {
        if (!RCON_PASSWORD) return reject(new Error("RCON chua cau hinh (thieu .rcon_password)"));
        const socket = net.createConnection({ host: RCON_HOST, port: RCON_PORT });
        let authed = false;
        let buf = Buffer.alloc(0);
        let body = "";
        let settled = false;
        let idleTimer = null;
        const hardTimer = setTimeout(() => finish(new Error("RCON timeout")), timeoutMs);

        function finish(err) {
            if (settled) return;
            settled = true;
            clearTimeout(hardTimer);
            if (idleTimer) clearTimeout(idleTimer);
            socket.destroy();
            err ? reject(err) : resolve(body);
        }

        socket.on("connect", () => socket.write(buildRconPacket(1, 3, RCON_PASSWORD)));
        socket.on("data", (chunk) => {
            buf = Buffer.concat([buf, chunk]);
            while (buf.length >= 4) {
                const size = buf.readInt32LE(0);
                if (buf.length < 4 + size) break;
                const id = buf.readInt32LE(4);
                const part = buf.toString("utf8", 12, 4 + size - 2);
                buf = buf.subarray(4 + size);
                if (!authed) {
                    if (id === -1) return finish(new Error("RCON sai mat khau"));
                    authed = true;
                    socket.write(buildRconPacket(2, 2, command));
                } else {
                    body += part;
                    if (idleTimer) clearTimeout(idleTimer);
                    idleTimer = setTimeout(() => finish(null), 400);
                }
            }
        });
        socket.on("error", (err) => finish(err));
        socket.on("close", () => finish(null));
    });
}

async function rconSafe(command) {
    try {
        return await rconCommand(command);
    } catch (e) {
        return null;
    }
}

// Mau text (§x) tu RCON -> bo di cho de doc
const stripColor = (s) => String(s ?? "").replace(/§./g, "");

async function getPlayers() {
    const out = await rconCommand("list");
    const m = stripColor(out).match(/There are (\d+) of a max of (\d+) players online:?\s*(.*)/);
    if (!m) return { count: null, max: null, names: [], raw: out };
    return {
        count: Number(m[1]),
        max: Number(m[2]),
        names: m[3] ? m[3].split(",").map((s) => s.trim()).filter(Boolean) : []
    };
}

function tellraw(text, color = "gold") {
    const json = JSON.stringify([{ text: "[Server] ", color: "red", bold: true }, { text, color }]);
    return rconSafe(`tellraw @a ${json}`);
}

// ---------------------------------------------------------------- command guard
// stop/restart: luon chan -> phai dung schedule_restart (co dem nguoc, bao nguoi choi).
const HARD_BLOCK = new Set(["stop", "restart"]);

function normalizeCommand(cmd) {
    return cmd.trim().replace(/^\/+/, "").replace(/\s+/g, " ");
}
function baseName(token) {
    return (token || "").toLowerCase().replace(/^minecraft:/, "");
}
// Lenh thuc su chay (go lop "execute ... run <lenh>")
function innermostCommand(cmd) {
    let c = normalizeCommand(cmd);
    for (let i = 0; i < 10; i++) {
        const t = c.split(" ");
        if (baseName(t[0]) !== "execute") break;
        const idx = t.findIndex((x, j) => j > 0 && x.toLowerCase() === "run");
        if (idx < 0) break;
        c = t.slice(idx + 1).join(" ");
    }
    return c;
}
// Lenh chi doc / vo hai -> chay tu do
function isReadOnly(cmd) {
    const t = normalizeCommand(cmd).split(" ");
    const a = baseName(t[0]);
    const b = (t[1] || "").toLowerCase();
    switch (a) {
        case "list": case "help": case "seed": case "banlist": case "say": case "tellraw": case "me": case "msg": case "tell": case "w":
            return true;
        case "tick": return b === "query";
        case "servercore": return b === "" || b === "status";
        case "whitelist": return b === "list";
        case "forceload": return b === "query";
        case "gamerule": return t.length <= 2;
        case "difficulty": return t.length === 1;
        case "time": return b === "query";
        case "worldborder": return b === "get";
        case "spark": return ["tps", "health", "gc", "gcmonitor", "profiler", "tickmonitor", "ping"].includes(b);
        case "data": return b === "get";
        case "scoreboard": return t.slice(1).some((x) => ["list", "get"].includes(x.toLowerCase()));
        case "attribute": return t.length >= 4 && ["get"].includes((t[3] || "").toLowerCase());
        case "save-all": return true;
        default: return false;
    }
}

// ---------------------------------------------------------------- restart scheduler
let pending = null; // { mode, reason, fireAt, timers, poll, createdAt, deadline }
let lastRestart = null; // { reason, requestedAt, backAt }
const ANNOUNCE_AT = [600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1];

function fmtDuration(sec) {
    if (sec >= 60) {
        const m = Math.floor(sec / 60), s = sec % 60;
        return s ? `${m} phút ${s} giây` : `${m} phút`;
    }
    return `${sec} giây`;
}

function clearPending() {
    if (!pending) return;
    for (const t of pending.timers) clearTimeout(t);
    if (pending.poll) clearInterval(pending.poll);
    pending = null;
}

async function executeRestart(reason) {
    clearPending();
    log(`RESTART: ${reason}`);
    await tellraw("Server khởi động lại NGAY BÂY GIỜ, vào lại sau khoảng 2 phút nhé!", "red");
    await rconSafe("save-all flush");
    fs.writeFileSync(RESTART_FLAG, JSON.stringify({ reason, at: new Date().toISOString() }));
    lastRestart = { reason, requestedAt: new Date().toISOString(), backAt: null };
    try {
        await rconCommand("stop", 20000);
    } catch (e) {
        // RCON dong ket noi khi server tat la binh thuong; chi bao loi neu server con song
        const alive = await rconSafe("list");
        if (alive !== null) {
            fs.rmSync(RESTART_FLAG, { force: true });
            lastRestart = null;
            throw new Error(`Gui stop that bai: ${e.message}`);
        }
    }
    waitUntilBack();
}

// Theo doi server len lai de bao trong restart_status
function waitUntilBack() {
    const started = Date.now();
    const iv = setInterval(async () => {
        if (fs.existsSync(RESTART_FLAG) && Date.now() - started < 15000) return;
        const out = await rconSafe("list");
        if (out !== null && lastRestart) {
            lastRestart.backAt = new Date().toISOString();
            log("Server da len lai sau restart");
            clearInterval(iv);
        } else if (Date.now() - started > 20 * 60 * 1000) {
            clearInterval(iv);
        }
    }, 5000);
}

function scheduleCountdown(delaySec, reason) {
    const fireAt = Date.now() + delaySec * 1000;
    const timers = [];
    for (const s of ANNOUNCE_AT) {
        if (s <= delaySec) {
            timers.push(setTimeout(() => tellraw(`Server sẽ khởi động lại sau ${fmtDuration(s)}${s >= 60 ? ` (${reason})` : ""}.`, s <= 10 ? "red" : "gold"), (delaySec - s) * 1000));
        }
    }
    timers.push(setTimeout(() => executeRestart(reason).catch((e) => log(e.message)), delaySec * 1000));
    pending = { mode: "countdown", reason, fireAt, timers, poll: null, createdAt: Date.now() };
    if (!ANNOUNCE_AT.includes(delaySec)) {
        tellraw(`Server sẽ khởi động lại sau ${fmtDuration(delaySec)} (${reason}).`);
    }
}

function scheduleWhenEmpty(reason, maxWaitMin) {
    const deadline = Date.now() + maxWaitMin * 60 * 1000;
    let emptyStreak = 0;
    const poll = setInterval(async () => {
        if (Date.now() > deadline) {
            log("when_empty: het thoi gian cho, huy restart");
            clearPending();
            return;
        }
        try {
            const p = await getPlayers();
            emptyStreak = p.count === 0 ? emptyStreak + 1 : 0;
            // 2 lan lien tiep (~1 phut) khong ai online moi restart, tranh nguoi vua thoat ra vao lai
            if (emptyStreak >= 2) await executeRestart(reason);
        } catch {
            emptyStreak = 0;
        }
    }, 30000);
    pending = { mode: "when_empty", reason, fireAt: null, timers: [], poll, createdAt: Date.now(), deadline };
}

function describePending() {
    if (!pending) return "Không có lịch restart nào.";
    if (pending.mode === "countdown") {
        const left = Math.max(0, Math.round((pending.fireAt - Date.now()) / 1000));
        return `Đang đếm ngược restart: còn ${fmtDuration(left)} (lý do: ${pending.reason}).`;
    }
    const left = Math.round((pending.deadline - Date.now()) / 60000);
    return `Đang chờ server trống người để restart (lý do: ${pending.reason}), tối đa ${left} phút nữa.`;
}

// ---------------------------------------------------------------- system info
function readFileSafe(p) {
    try {
        return fs.readFileSync(p, "utf8");
    } catch {
        return null;
    }
}
function findJavaPid() {
    try {
        for (const d of fs.readdirSync("/proc")) {
            if (!/^\d+$/.test(d)) continue;
            const cmd = readFileSafe(`/proc/${d}/cmdline`);
            if (cmd && cmd.includes("java") && cmd.includes("server.jar")) return Number(d);
        }
    } catch {}
    return null;
}
function systemInfo() {
    const info = {};
    const mb = (b) => Math.round(Number(b) / 1048576);
    const cur = readFileSafe("/sys/fs/cgroup/memory.current");
    const max = readFileSafe("/sys/fs/cgroup/memory.max");
    if (cur) info.containerMemMB = mb(cur);
    if (max && /^\d+/.test(max)) info.containerLimitMB = mb(max);
    const load = readFileSafe("/proc/loadavg");
    if (load) info.loadavg = load.split(" ").slice(0, 3).join(" ");
    const pid = findJavaPid();
    if (pid) {
        const st = readFileSafe(`/proc/${pid}/status`) || "";
        const rss = st.match(/VmRSS:\s+(\d+) kB/);
        if (rss) info.javaRssMB = Math.round(Number(rss[1]) / 1024);
        const stat = readFileSafe(`/proc/${pid}/stat`);
        const up = readFileSafe("/proc/uptime");
        if (stat && up) {
            const startTicks = Number(stat.slice(stat.lastIndexOf(")") + 2).split(" ")[19]);
            const sec = Math.round(Number(up.split(" ")[0]) - startTicks / 100);
            info.javaUptime = `${Math.floor(sec / 3600)}h${Math.floor((sec % 3600) / 60)}m`;
        }
    }
    return info;
}

// Doc N dong cuoi log (chi doc ~1MB cuoi file)
function tailLog(lines, filter) {
    const fd = fs.openSync(LOG_FILE, "r");
    try {
        const size = fs.fstatSync(fd).size;
        const len = Math.min(size, 1024 * 1024);
        const buf = Buffer.alloc(len);
        fs.readSync(fd, buf, 0, len, size - len);
        let arr = buf.toString("utf8").replace(/\x1b\[[0-9;]*m/g, "").split("\n");
        if (len < size) arr.shift();
        if (filter) {
            let re;
            try {
                re = new RegExp(filter, "i");
            } catch {
                re = new RegExp(filter.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
            }
            arr = arr.filter((l) => re.test(l));
        }
        return arr.filter((l) => l.length).slice(-lines).join("\n");
    } finally {
        fs.closeSync(fd);
    }
}

// ---------------------------------------------------------------- tools
const text = (t, isError = false) => ({ content: [{ type: "text", text: t }], ...(isError ? { isError: true } : {}) });

const handler = createMcpHandler(() => {
    const server = new McpServer({ name: "pikamc-agent", version: VERSION });

    server.registerTool("ping", { description: "Check whether PikaMC agent is alive" }, async () => text(`PIKAMC_AGENT_OK v${VERSION}${SUPERVISED ? "" : " (restart loop NOT active)"}`));

    server.registerTool(
        "server_status",
        { description: "One-call overview: online players, tick time (MSPT/percentiles), ServerCore view/sim distance, Java RAM, container memory, load, uptime, pending restart. Use this first when checking lag or health." },
        async () => {
            const [players, tick, core] = await Promise.all([getPlayers().catch((e) => ({ error: e.message })), rconSafe("tick query"), rconSafe("servercore status")]);
            const lines = [];
            if (players.error) lines.push(`Minecraft: KHÔNG kết nối được RCON (${players.error}) - server đang tắt/khởi động?`);
            else lines.push(`Người chơi: ${players.count}/${players.max}${players.names.length ? " - " + players.names.join(", ") : ""}`);
            if (tick) lines.push(`Tick: ${stripColor(tick).replace(/\n/g, " | ")}`);
            if (core) lines.push(`ServerCore: ${stripColor(core).split("\n").slice(1).map((s) => s.replace(/^»\s*/, "")).join(" | ")}`);
            lines.push(`Hệ thống: ${JSON.stringify(systemInfo())}`);
            lines.push(`Restart: ${describePending()}`);
            if (lastRestart) lines.push(`Lần restart gần nhất: ${JSON.stringify(lastRestart)}`);
            if (!SUPERVISED) lines.push("CẢNH BÁO: vòng lặp restart chưa bật (test-mcp.sh cũ) -> schedule_restart bị khóa.");
            return text(lines.join("\n"));
        }
    );

    server.registerTool(
        "minecraft_command",
        {
            description:
                "Run a Minecraft console command via RCON. Read-only commands (list, tick query, servercore status, spark tps, whitelist list, gamerule <name>, data get, say/tellraw...) run directly. Any command that changes the world/players/config is refused unless confirm_dangerous=true, which you may ONLY set after the user explicitly approved that exact command. 'stop'/'restart' are always refused: use schedule_restart.",
            inputSchema: {
                command: z.string().min(1).describe("Console command without leading slash, e.g. 'list' or 'tick query'"),
                confirm_dangerous: z.boolean().optional().describe("Set true only after explicit user approval of this exact state-changing command")
            }
        },
        async ({ command, confirm_dangerous }) => {
            const cmd = normalizeCommand(command);
            const inner = innermostCommand(cmd);
            if (HARD_BLOCK.has(baseName(inner.split(" ")[0]))) {
                return text("Bị chặn: không được stop/restart trực tiếp. Dùng tool schedule_restart (có đếm ngược, báo người chơi, lưu world).", true);
            }
            if (!isReadOnly(cmd) && !confirm_dangerous) {
                return text(`Lệnh "${cmd}" có thể thay đổi server/world/người chơi nên chưa chạy. Hỏi người dùng xác nhận rồi gọi lại với confirm_dangerous=true.`, true);
            }
            try {
                if (!isReadOnly(cmd)) log(`Dangerous command (confirmed): ${cmd}`);
                const out = await rconCommand(cmd);
                return text(out ? stripColor(out) : "(no output)");
            } catch (e) {
                return text(`RCON error: ${e?.message || e}`, true);
            }
        }
    );

    server.registerTool(
        "read_log",
        {
            description: "Read the last lines of logs/latest.log (ANSI stripped). Optional filter is a case-insensitive regex, e.g. 'ERROR|WARN', 'Can\\'t keep up', a player name.",
            inputSchema: {
                lines: z.number().int().min(1).max(500).optional().describe("How many lines (default 80, max 500)"),
                filter: z.string().optional().describe("Case-insensitive regex filter applied before taking the last N lines")
            }
        },
        async ({ lines, filter }) => {
            try {
                const out = tailLog(lines || 80, filter);
                return text(out || "(không có dòng nào khớp)");
            } catch (e) {
                return text(`Không đọc được log: ${e.message}`, true);
            }
        }
    );

    server.registerTool(
        "reload_datapacks",
        { description: "Run /reload (datapacks, scarpet-loaded data, recipes, loot tables). Does not kick players but may cause a short lag spike on a modded server. Does NOT reload mods or configs that need a restart." },
        async () => {
            try {
                await tellraw("Đang tải lại datapack, có thể giật nhẹ vài giây.", "yellow");
                const out = await rconCommand("reload", 60000);
                return text(stripColor(out) || "Đã gửi /reload.");
            } catch (e) {
                return text(`RCON error: ${e.message}`, true);
            }
        }
    );

    server.registerTool(
        "schedule_restart",
        {
            description:
                "Safely restart the Minecraft server (needed after changing mods/configs). Never instant while players are online: with players it does an in-game countdown (min 60s, default 300s) announcing to players, then save-all + restart. when_empty=true instead waits until nobody is online (checked every 30s, needs ~1 min empty). Only one restart can be pending; cancel with cancel_restart. The agent stays up during the restart.",
            inputSchema: {
                reason: z.string().min(1).max(80).describe("Short reason shown to players, e.g. 'cập nhật mod'"),
                delay_seconds: z.number().int().min(0).max(3600).optional().describe("Countdown length. Default 300 if players online, 10 if empty. Forced to >=60 when players online."),
                when_empty: z.boolean().optional().describe("Wait until the server is empty instead of counting down"),
                max_wait_minutes: z.number().int().min(5).max(1440).optional().describe("For when_empty: give up after this long (default 360)")
            }
        },
        async ({ reason, delay_seconds, when_empty, max_wait_minutes }) => {
            if (!SUPERVISED) {
                return text("Không restart được: test-mcp.sh trên server chưa phải bản có vòng lặp restart, nếu gửi stop server sẽ TẮT HẲN. Cần restart container từ panel một lần để bật bản mới.", true);
            }
            if (pending) return text(`Đã có lịch restart: ${describePending()} Gọi cancel_restart trước nếu muốn đổi.`, true);
            if (fs.existsSync(RESTART_FLAG)) return text("Server đang trong quá trình restart.", true);
            let players;
            try {
                players = await getPlayers();
            } catch (e) {
                return text(`Không kết nối được RCON (${e.message}) - server có đang chạy không?`, true);
            }
            if (when_empty) {
                if (players.count === 0) {
                    scheduleCountdown(10, reason);
                    return text("Server đang trống -> restart sau 10 giây.");
                }
                scheduleWhenEmpty(reason, max_wait_minutes || 360);
                return text(`Đang có ${players.count} người (${players.names.join(", ")}). Sẽ restart khi server trống khoảng 1 phút (tối đa chờ ${max_wait_minutes || 360} phút).`);
            }
            let delay = delay_seconds ?? (players.count > 0 ? 300 : 10);
            let note = "";
            if (players.count > 0 && delay < 60) {
                delay = 60;
                note = " (đã nâng lên 60 giây vì đang có người chơi)";
            }
            scheduleCountdown(delay, reason);
            return text(`Đã lên lịch restart sau ${fmtDuration(delay)}${note}. Người chơi online: ${players.count}${players.names.length ? " - " + players.names.join(", ") : ""}. Hủy bằng cancel_restart.`);
        }
    );

    server.registerTool("cancel_restart", { description: "Cancel a pending scheduled restart and tell players." }, async () => {
        if (!pending) return text("Không có lịch restart nào để hủy.");
        const wasCountdown = pending.mode === "countdown";
        clearPending();
        if (wasCountdown) await tellraw("Đã HỦY khởi động lại server.", "green");
        return text("Đã hủy lịch restart.");
    });

    server.registerTool("restart_status", { description: "Show pending restart / last restart result." }, async () => {
        const parts = [describePending()];
        if (fs.existsSync(RESTART_FLAG)) parts.push("Server đang tắt/khởi động lại.");
        if (lastRestart) parts.push(`Lần gần nhất: ${JSON.stringify(lastRestart)}`);
        parts.push(`Vòng lặp restart: ${SUPERVISED ? "đang bật" : "CHƯA bật"}`);
        return text(parts.join("\n"));
    });

    server.registerTool(
        "reload_agent",
        { description: "Restart only this agent process (to load a new server.js / token). Minecraft keeps running, players are not affected. Pending restarts are cancelled." },
        async () => {
            if (!SUPERVISED) return text("Không tự khởi động lại được: agent chưa chạy trong vòng lặp (test-mcp.sh cũ).", true);
            clearPending();
            setTimeout(() => process.exit(0), 500);
            return text("Agent sẽ khởi động lại sau 0.5 giây.");
        }
    );

    return server;
});

const nodeHandler = toNodeHandler(handler);

const httpServer = http.createServer(async (req, res) => {
    if (req.url !== "/mcp") {
        res.writeHead(404);
        return res.end("Not found");
    }
    const ip = clientIp(req);
    if (isBlocked(ip)) {
        res.writeHead(429, { "Content-Type": "text/plain" });
        return res.end("Too many failed attempts");
    }
    if (!isAuthorized(req)) {
        recordFail(ip);
        res.writeHead(401, { "Content-Type": "text/plain" });
        return res.end("Unauthorized");
    }
    failedAuth.delete(ip);
    try {
        await nodeHandler(req, res);
    } catch (error) {
        console.error("MCP ERROR:", error?.stack || error);
        if (!res.headersSent) {
            res.writeHead(500, { "Content-Type": "text/plain" });
            res.end("MCP Internal Error");
        }
    }
});

httpServer.on("error", (e) => {
    console.error(`FATAL: ${e.message}`);
    process.exit(1);
});
httpServer.listen(PORT, "0.0.0.0", () => log(`MCP SERVER READY v${VERSION} on :${PORT}${SUPERVISED ? " (supervised)" : ""}`));
