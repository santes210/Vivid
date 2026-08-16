const TOKEN_URL = "https://oauth2.googleapis.com/token";
const FIREBASE_JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const encoder = new TextEncoder();

let jwksCache = { expiresAt: 0, keys: null };
let accessTokenCache = { token: null, expiresAt: 0 };

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") return corsResponse(null, 204);
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "vivid-push" });
    }
    if (request.method !== "POST" || url.pathname !== "/notify") {
      return json({ error: "Not found" }, 404);
    }

    try {
      const projectId = env.FIREBASE_PROJECT_ID;
      const serviceAccount = parseServiceAccount(env.FIREBASE_SERVICE_ACCOUNT_JSON, projectId);
      const actorUid = await authenticateFirebaseUser(request, projectId);
      const body = await readJson(request);
      const event = await resolveAndValidateEvent(serviceAccount, projectId, actorUid, body);

      if (event.targetUid === actorUid) return json({ ok: true, skipped: "self" });

      const targetUser = await getDocument(serviceAccount, projectId, `users/${event.targetUid}`);
      if (!preferenceEnabled(targetUser, event.preference)) {
        return json({ ok: true, skipped: "preference" });
      }

      const markerPath = `_pushNotifications/${encodeURIComponent(event.eventId)}`;
      const claimed = await claimEvent(serviceAccount, projectId, markerPath, event);
      if (!claimed) return json({ ok: true, duplicate: true });

      try {
        const tokens = await listFcmTokens(serviceAccount, projectId, event.targetUid);
        if (tokens.length === 0) {
          await finishEvent(serviceAccount, projectId, markerPath, "no_tokens", 0);
          return json({ ok: true, sent: 0 });
        }

        const results = await Promise.all(tokens.map((token) =>
          sendFcm(serviceAccount, projectId, token, event.data)
            .then(() => ({ token, ok: true }))
            .catch((error) => ({ token, ok: false, error }))
        ));
        const invalid = results.filter((r) => !r.ok && isInvalidTokenError(r.error));
        ctx.waitUntil(Promise.all(invalid.map((r) =>
          deleteDocument(serviceAccount, projectId,
            `users/${event.targetUid}/fcmTokens/${encodeURIComponent(r.token)}`)
        )));

        const sent = results.filter((r) => r.ok).length;
        const retryableFailures = results.filter((r) => !r.ok && !isInvalidTokenError(r.error));
        if (sent === 0 && retryableFailures.length > 0) {
          await deleteDocument(serviceAccount, projectId, markerPath);
          throw retryableFailures[0].error;
        }
        await finishEvent(serviceAccount, projectId, markerPath, "sent", sent);
        return json({ ok: true, sent, invalidTokens: invalid.length });
      } catch (error) {
        // Permite que WorkManager reintente si todavía no se entregó a ningún token.
        await deleteDocument(serviceAccount, projectId, markerPath).catch(() => {});
        throw error;
      }
    } catch (error) {
      console.error("notify failed", error);
      const status = error instanceof HttpError ? error.status : 500;
      return json({ error: status >= 500 ? "Temporary push delivery error" : error.message }, status);
    }
  }
};

async function resolveAndValidateEvent(sa, projectId, actorUid, input) {
  const type = requiredString(input.type, "type");
  const actor = await getDocument(sa, projectId, `users/${actorUid}`);
  const actorName = displayName(actor);
  let targetUid, eventId, title, body, preference, data;

  switch (type) {
    case "reel_like": {
      const reelId = requiredId(input.reelId, "reelId");
      const [reel, like] = await Promise.all([
        getDocument(sa, projectId, `reels/${reelId}`),
        getDocument(sa, projectId, `reels/${reelId}/likes/${actorUid}`)
      ]);
      assertActor(like, actorUid);
      targetUid = requiredField(reel, "userId");
      eventId = `reel_like:${reelId}:${actorUid}:${like.timestamp || "legacy"}`;
      title = "Nuevo Me gusta";
      body = `@${actorName} indicó que le gusta tu reel`;
      preference = "notifyLikesComments";
      data = { type, reelId, fromUserId: actorUid };
      break;
    }
    case "reel_comment": {
      const reelId = requiredId(input.reelId, "reelId");
      const commentId = requiredId(input.commentId, "commentId");
      const [reel, comment] = await Promise.all([
        getDocument(sa, projectId, `reels/${reelId}`),
        getDocument(sa, projectId, `reels/${reelId}/comments/${commentId}`)
      ]);
      assertActor(comment, actorUid);
      targetUid = requiredField(reel, "userId");
      eventId = `reel_comment:${reelId}:${commentId}`;
      title = "Nuevo comentario";
      body = `@${actorName} comentó tu reel`;
      preference = "notifyLikesComments";
      data = { type, reelId, fromUserId: actorUid };
      break;
    }
    case "post_like": {
      const postId = requiredId(input.postId, "postId");
      const [post, like] = await Promise.all([
        getDocument(sa, projectId, `posts/${postId}`),
        getDocument(sa, projectId, `posts/${postId}/likes/${actorUid}`)
      ]);
      assertActor(like, actorUid);
      targetUid = requiredField(post, "userId");
      eventId = `post_like:${postId}:${actorUid}:${like.timestamp || "legacy"}`;
      title = "Nuevo Me gusta";
      body = `@${actorName} indicó que le gusta tu publicación`;
      preference = "notifyLikesComments";
      data = { type, postId, fromUserId: actorUid };
      break;
    }
    case "post_comment": {
      const postId = requiredId(input.postId, "postId");
      const commentId = requiredId(input.commentId, "commentId");
      const [post, comment] = await Promise.all([
        getDocument(sa, projectId, `posts/${postId}`),
        getDocument(sa, projectId, `posts/${postId}/comments/${commentId}`)
      ]);
      assertActor(comment, actorUid);
      targetUid = requiredField(post, "userId");
      eventId = `post_comment:${postId}:${commentId}`;
      title = "Nuevo comentario";
      body = `@${actorName} comentó tu publicación`;
      preference = "notifyLikesComments";
      data = { type, postId, fromUserId: actorUid };
      break;
    }
    case "new_follower": {
      const target = requiredId(input.targetUid, "targetUid");
      const follower = await getDocument(sa, projectId, `users/${target}/followers/${actorUid}`);
      if (!follower) throw new HttpError(404, "Follow relationship not found");
      targetUid = target;
      eventId = `new_follower:${target}:${actorUid}:${follower.timestamp || "legacy"}`;
      title = "Nuevo seguidor";
      body = `@${actorName} comenzó a seguirte`;
      preference = "notifyNewFollowers";
      data = { type, fromUserId: actorUid };
      break;
    }
    case "follow_request": {
      const target = requiredId(input.targetUid, "targetUid");
      const request = await getDocument(sa, projectId, `users/${target}/followRequests/${actorUid}`);
      if (!request || (request.requesterId && request.requesterId !== actorUid)) {
        throw new HttpError(404, "Follow request not found");
      }
      targetUid = target;
      eventId = `follow_request:${target}:${actorUid}:${request.timestamp || "legacy"}`;
      title = "Nueva solicitud";
      body = `@${actorName} quiere seguirte`;
      preference = "notifyNewFollowers";
      data = { type, fromUserId: actorUid };
      break;
    }
    case "message": {
      const chatId = requiredId(input.chatId, "chatId");
      const messageId = requiredId(input.messageId, "messageId");
      const [chat, message] = await Promise.all([
        getDocument(sa, projectId, `chats/${chatId}`),
        getDocument(sa, projectId, `chats/${chatId}/messages/${messageId}`)
      ]);
      assertActor(message, actorUid, "senderId");
      targetUid = requiredField(message, "receiverId");
      const participants = chat?.participants;
      if (!Array.isArray(participants) || !participants.includes(actorUid) || !participants.includes(targetUid)) {
        throw new HttpError(403, "Invalid chat participants");
      }
      eventId = `message:${chatId}:${messageId}`;
      title = `Mensaje de @${actorName}`;
      body = messagePreview(message);
      preference = "notifyDirectMessages";
      data = { type, chatId, fromUserId: actorUid };
      break;
    }
    default:
      throw new HttpError(400, "Unsupported notification type");
  }

  return {
    eventId, targetUid, preference,
    data: stringifyData({ ...data, title, body })
  };
}

function messagePreview(message) {
  switch (message.type || "text") {
    case "image": return "Te envió una imagen";
    case "voice": return "Te envió una nota de voz";
    case "story_reply": return "Respondió a tu historia";
    default: {
      const text = typeof message.text === "string" ? message.text.trim() : "";
      return text ? (text.length > 120 ? `${text.slice(0, 117)}…` : text) : "Te envió un mensaje";
    }
  }
}

function preferenceEnabled(user, key) {
  return user?.[key] !== false;
}

function displayName(user) {
  const value = user?.username || user?.displayName || "usuario";
  return String(value).replace(/^@/, "").slice(0, 50);
}

function assertActor(document, actorUid, field = "userId") {
  if (!document || document[field] !== actorUid) throw new HttpError(403, "Action does not belong to authenticated user");
}

async function authenticateFirebaseUser(request, projectId) {
  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) throw new HttpError(401, "Missing Firebase ID token");
  const token = auth.slice(7);
  const parts = token.split(".");
  if (parts.length !== 3) throw new HttpError(401, "Malformed Firebase ID token");
  const header = JSON.parse(decodeBase64Url(parts[0]));
  const claims = JSON.parse(decodeBase64Url(parts[1]));
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== "RS256" || !header.kid || claims.aud !== projectId ||
      claims.iss !== `https://securetoken.google.com/${projectId}` ||
      typeof claims.sub !== "string" || !claims.sub || claims.exp <= now || claims.iat > now + 60) {
    throw new HttpError(401, "Invalid Firebase ID token claims");
  }
  const jwks = await getFirebaseJwks();
  const jwk = jwks.keys.find((key) => key.kid === header.kid);
  if (!jwk) throw new HttpError(401, "Unknown Firebase signing key");
  const cryptoKey = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey,
    base64UrlBytes(parts[2]), encoder.encode(`${parts[0]}.${parts[1]}`));
  if (!valid) throw new HttpError(401, "Invalid Firebase ID token signature");
  return requiredId(claims.sub, "uid");
}

async function getFirebaseJwks() {
  if (jwksCache.keys && jwksCache.expiresAt > Date.now()) return jwksCache.keys;
  const response = await fetch(FIREBASE_JWKS_URL);
  if (!response.ok) throw new Error("Unable to load Firebase signing keys");
  const keys = await response.json();
  const maxAge = Number((response.headers.get("cache-control") || "").match(/max-age=(\d+)/)?.[1] || 3600);
  jwksCache = { keys, expiresAt: Date.now() + maxAge * 1000 };
  return keys;
}

function parseServiceAccount(raw, projectId) {
  if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is not configured");
  let value;
  try { value = typeof raw === "string" ? JSON.parse(raw) : raw; } catch { throw new Error("Invalid service account JSON"); }
  if (!value.client_email || !value.private_key || value.project_id !== projectId) {
    throw new Error("Service account does not match FIREBASE_PROJECT_ID");
  }
  return value;
}

async function getAccessToken(sa) {
  if (accessTokenCache.token && accessTokenCache.expiresAt > Date.now() + 60_000) return accessTokenCache.token;
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(sa, {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600
  });
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion })
  });
  if (!response.ok) throw new Error(`OAuth token exchange failed: ${response.status}`);
  const result = await response.json();
  accessTokenCache = { token: result.access_token, expiresAt: Date.now() + result.expires_in * 1000 };
  return result.access_token;
}

async function signJwt(sa, claims) {
  const header = base64UrlJson({ alg: "RS256", typ: "JWT" });
  const payload = base64UrlJson(claims);
  const key = await crypto.subtle.importKey("pkcs8", pemToBytes(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(`${header}.${payload}`));
  return `${header}.${payload}.${bytesToBase64Url(new Uint8Array(signature))}`;
}

async function firestoreRequest(sa, projectId, path, options = {}) {
  const token = await getAccessToken(sa);
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${path}${options.query || ""}`;
  const response = await fetch(url, {
    method: options.method || "GET",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  if (options.allow404 && response.status === 404) return null;
  if (!response.ok) {
    const detail = await response.text();
    throw new HttpError(response.status >= 500 ? 503 : response.status, `Firestore request failed (${response.status}): ${detail.slice(0, 200)}`);
  }
  return response.status === 204 ? null : response.json();
}

async function getDocument(sa, projectId, path) {
  const doc = await firestoreRequest(sa, projectId, path, { allow404: true });
  return doc ? decodeFields(doc.fields || {}) : null;
}

async function listFcmTokens(sa, projectId, uid) {
  let pageToken = "";
  const tokens = [];
  do {
    const query = `?pageSize=500${pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : ""}`;
    const result = await firestoreRequest(sa, projectId, `users/${uid}/fcmTokens`, { query, allow404: true });
    if (!result) break;
    for (const doc of result.documents || []) tokens.push(decodeURIComponent(doc.name.split("/").pop()));
    pageToken = result.nextPageToken || "";
  } while (pageToken && tokens.length < 2000);
  return [...new Set(tokens)];
}

async function claimEvent(sa, projectId, path, event) {
  try {
    await firestoreRequest(sa, projectId, path, {
      method: "PATCH",
      query: "?currentDocument.exists=false",
      body: { fields: encodeFields({ status: "processing", targetUid: event.targetUid, createdAt: Date.now(), eventId: event.eventId }) }
    });
    return true;
  } catch (error) {
    if (error instanceof HttpError && (error.status === 409 || error.status === 412)) return false;
    throw error;
  }
}

async function finishEvent(sa, projectId, path, status, sent) {
  await firestoreRequest(sa, projectId, path, {
    method: "PATCH",
    body: { fields: encodeFields({ status, sent, completedAt: Date.now() }) }
  });
}

async function deleteDocument(sa, projectId, path) {
  return firestoreRequest(sa, projectId, path, { method: "DELETE", allow404: true });
}

async function sendFcm(sa, projectId, token, data) {
  const accessToken = await getAccessToken(sa);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message: { token, data, android: { priority: "high" } } })
  });
  if (!response.ok) {
    const text = await response.text();
    const error = new Error(`FCM ${response.status}: ${text.slice(0, 500)}`);
    error.fcmStatus = response.status;
    error.fcmBody = text;
    throw error;
  }
}

function isInvalidTokenError(error) {
  return error?.fcmStatus === 404 || /UNREGISTERED|registration-token-not-registered|INVALID_ARGUMENT/.test(error?.fcmBody || "");
}

function decodeFields(fields) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, decodeValue(value)]));
}
function decodeValue(value) {
  if ("stringValue" in value) return value.stringValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("timestampValue" in value) return value.timestampValue;
  if ("nullValue" in value) return null;
  if ("arrayValue" in value) return (value.arrayValue.values || []).map(decodeValue);
  if ("mapValue" in value) return decodeFields(value.mapValue.fields || {});
  if ("referenceValue" in value) return value.referenceValue;
  return undefined;
}
function encodeFields(object) {
  return Object.fromEntries(Object.entries(object).map(([key, value]) => [key, encodeValue(value)]));
}
function encodeValue(value) {
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  return { stringValue: String(value) };
}

function requiredString(value, name) {
  if (typeof value !== "string" || !value.trim() || value.length > 1500) throw new HttpError(400, `Invalid ${name}`);
  return value.trim();
}
function requiredId(value, name) {
  const id = requiredString(value, name);
  // Los IDs generados por Firebase/Vivid solo usan este conjunto. Restringirlo
  // impide que una petición manipule rutas REST con /, ?, # o escapes.
  if (!/^[A-Za-z0-9_-]+$/.test(id)) throw new HttpError(400, `Invalid ${name}`);
  return id;
}
function requiredField(document, field) {
  if (!document) throw new HttpError(404, "Referenced document not found");
  return requiredId(document[field], field);
}
function stringifyData(data) {
  return Object.fromEntries(Object.entries(data).filter(([, value]) => value != null).map(([key, value]) => [key, String(value)]));
}
async function readJson(request) {
  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > 16_384) throw new HttpError(413, "Request too large");
  try { return await request.json(); } catch { throw new HttpError(400, "Invalid JSON"); }
}
function corsResponse(body, status = 200) {
  return new Response(body, { status, headers: {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Allow-Methods": "POST, OPTIONS"
  }});
}
function json(value, status = 200) {
  const response = corsResponse(JSON.stringify(value), status);
  response.headers.set("Content-Type", "application/json; charset=utf-8");
  response.headers.set("Cache-Control", "no-store");
  return response;
}
function base64UrlJson(value) { return bytesToBase64Url(encoder.encode(JSON.stringify(value))); }
function decodeBase64Url(value) { return new TextDecoder().decode(base64UrlBytes(value)); }
function base64UrlBytes(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return Uint8Array.from(atob(normalized), (c) => c.charCodeAt(0));
}
function bytesToBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function pemToBytes(pem) {
  return Uint8Array.from(atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s/g, "")), (c) => c.charCodeAt(0));
}
class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
