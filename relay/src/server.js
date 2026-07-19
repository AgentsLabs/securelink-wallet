import { createHash, timingSafeEqual } from "node:crypto";
import { createServer } from "node:http";

const MAX_PAYLOAD_BYTES = 64 * 1024;
const MAX_QUEUE_PER_DEVICE = 20;
const SIGNAL_TTL_MS = 5 * 60 * 1000;

const hashToken = (token) => createHash("sha256").update(token).digest();
const validDeviceId = (value) => typeof value === "string" && /^[A-Za-z0-9._-]{8,200}$/.test(value);
const validToken = (value) => typeof value === "string" && /^[A-Za-z0-9_-]{32,200}$/.test(value);

function bearerToken(request) {
  const value = request.headers.authorization;
  return typeof value === "string" && value.startsWith("Bearer ") ? value.slice(7) : null;
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let raw = "";
    request.setEncoding("utf8");
    request.on("data", (chunk) => {
      raw += chunk;
      if (Buffer.byteLength(raw) > MAX_PAYLOAD_BYTES + 2048) request.destroy();
    });
    request.on("end", () => {
      try { resolve(JSON.parse(raw || "{}")); } catch { reject(new Error("Body must be JSON")); }
    });
    request.on("error", reject);
  });
}

function respond(response, status, value) {
  response.writeHead(status, { "content-type": "application/json", "cache-control": "no-store" });
  response.end(JSON.stringify(value));
}

/**
 * In-memory reference relay. Put it behind TLS and replace the Maps with a
 * durable TTL store (for example Redis) before operating more than one replica.
 */
export function createRelayServer() {
  const devices = new Map(); // deviceId -> { tokenHash: Buffer, queue: [] }
  const isAuthorized = (device, token) => {
    if (!device || !validToken(token)) return false;
    const supplied = hashToken(token);
    return device.tokenHash.length === supplied.length && timingSafeEqual(device.tokenHash, supplied);
  };
  const expire = (device, now = Date.now()) => {
    device.queue = device.queue.filter((signal) => signal.expiresAtMillis > now);
  };

  return createServer(async (request, response) => {
    const url = new URL(request.url, "http://localhost");
    try {
      if (request.method === "GET" && url.pathname === "/healthz") {
        return respond(response, 200, { ok: true });
      }

      if (request.method === "POST" && url.pathname === "/v1/devices/register") {
        const body = await readJson(request);
        const token = bearerToken(request);
        if (!validDeviceId(body.deviceId) || !validToken(token)) return respond(response, 400, { error: "Invalid device ID or token" });
        const existing = devices.get(body.deviceId);
        if (existing && !isAuthorized(existing, token)) return respond(response, 409, { error: "Device ID is already registered" });
        devices.set(body.deviceId, { tokenHash: hashToken(token), queue: existing?.queue ?? [] });
        return respond(response, 201, { ok: true });
      }

      if (request.method === "POST" && url.pathname === "/v1/signals") {
        const body = await readJson(request);
        const token = bearerToken(request);
        const recipient = devices.get(body.recipientDeviceId);
        if (!validDeviceId(body.recipientDeviceId) || !validDeviceId(body.senderDeviceId) || typeof body.payload !== "string" || Buffer.byteLength(body.payload) > MAX_PAYLOAD_BYTES) {
          return respond(response, 400, { error: "Invalid signal" });
        }
        if (!recipient || !isAuthorized(recipient, token)) return respond(response, 401, { error: "Recipient authorization failed" });
        expire(recipient);
        if (recipient.queue.length >= MAX_QUEUE_PER_DEVICE) return respond(response, 429, { error: "Recipient queue is full" });
        recipient.queue.push({ senderDeviceId: body.senderDeviceId, payload: body.payload, expiresAtMillis: Date.now() + SIGNAL_TTL_MS });
        return respond(response, 202, { ok: true });
      }

      if (request.method === "GET" && url.pathname === "/v1/signals") {
        const deviceId = url.searchParams.get("deviceId");
        const device = devices.get(deviceId);
        if (!validDeviceId(deviceId) || !isAuthorized(device, bearerToken(request))) return respond(response, 401, { error: "Authorization failed" });
        expire(device);
        const signals = device.queue;
        device.queue = [];
        return respond(response, 200, { signals });
      }
      return respond(response, 404, { error: "Not found" });
    } catch (error) {
      return respond(response, 400, { error: error.message || "Bad request" });
    }
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number.parseInt(process.env.PORT || "8080", 10);
  createRelayServer().listen(port, () => console.log(`SecureLink relay listening on ${port}`));
}
