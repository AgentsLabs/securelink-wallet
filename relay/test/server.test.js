import assert from "node:assert/strict";
import test from "node:test";
import { createRelayServer } from "../src/server.js";

async function withRelay(run) {
  const server = createRelayServer();
  await new Promise((resolve) => server.listen(0, resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  try { await run(baseUrl); } finally { await new Promise((resolve) => server.close(resolve)); }
}

const token = "p".repeat(40);
const peerToken = "q".repeat(40);
const headers = (value) => ({ authorization: `Bearer ${value}`, "content-type": "application/json" });

test("relay registers an inbox and returns queued opaque signals once", async () => withRelay(async (baseUrl) => {
  let response = await fetch(`${baseUrl}/v1/devices/register`, { method: "POST", headers: headers(token), body: JSON.stringify({ deviceId: "device-receiver-123" }) });
  assert.equal(response.status, 201);
  response = await fetch(`${baseUrl}/v1/signals`, { method: "POST", headers: headers(token), body: JSON.stringify({ recipientDeviceId: "device-receiver-123", senderDeviceId: "device-sender-456", payload: "sl-contact-opaque" }) });
  assert.equal(response.status, 202);
  response = await fetch(`${baseUrl}/v1/signals?deviceId=device-receiver-123`, { headers: headers(token) });
  const firstRead = await response.json();
  assert.equal(firstRead.signals.length, 1);
  assert.equal(firstRead.signals[0].senderDeviceId, "device-sender-456");
  assert.equal(firstRead.signals[0].payload, "sl-contact-opaque");
  assert.ok(firstRead.signals[0].expiresAtMillis > Date.now());
  response = await fetch(`${baseUrl}/v1/signals?deviceId=device-receiver-123`, { headers: headers(token) });
  assert.deepEqual(await response.json(), { signals: [] });
}));

test("relay refuses a signal without the recipient inbox token", async () => withRelay(async (baseUrl) => {
  await fetch(`${baseUrl}/v1/devices/register`, { method: "POST", headers: headers(token), body: JSON.stringify({ deviceId: "device-receiver-123" }) });
  const response = await fetch(`${baseUrl}/v1/signals`, { method: "POST", headers: headers(peerToken), body: JSON.stringify({ recipientDeviceId: "device-receiver-123", senderDeviceId: "device-sender-456", payload: "sl-contact-opaque" }) });
  assert.equal(response.status, 401);
}));
