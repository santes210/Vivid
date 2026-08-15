/**
 * Vivid — Cloud Function proxy a Backblaze B2 con Signed URLs
 * ============================================================
 *
 * SEGURIDAD (2026-08):
 *  - Todos los endpoints exigen `Authorization: Bearer <idToken>` (Firebase Auth).
 *  - La key del bucket debe vivir bajo el namespace del usuario autenticado:
 *      "<tipo>/<uid>/<archivo>"  (p. ej. reels/UID/123.mp4, posts/UID/x.jpg)
 *      o "<uid>/<archivo>". Un usuario no puede subir, firmar ni borrar
 *      contenido de otro.
 *  - CORS restringido a los orígenes listados en `cors.origins` (config).
 *    Los clientes nativos (APK) no usan CORS, así que no se ven afectados.
 *  - App Check opcional: `appcheck.enforce=true` exige el header
 *    X-Firebase-AppCheck con un token válido.
 *
 * Config:
 *   firebase functions:config:set \
 *     b2.key_id=... b2.application_key=... b2.bucket_id=... b2.bucket_name=... \
 *     cors.origins="https://tu-web.web.app" \
 *     appcheck.enforce=true
 *
 * Deploy:
 *   firebase deploy --only functions
 *
 * NOTA: Las Cloud Functions requieren plan Blaze (pay-as-you-go).
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const corsFactory = require("cors");
const { URL } = require("url");

admin.initializeApp();

const CFG = functions.config();
const B2_CFG = CFG.b2 || {};
const KEY_ID = B2_CFG.key_id;
const APP_KEY = B2_CFG.application_key;
const BUCKET_ID = B2_CFG.bucket_id;
const BUCKET_NAME = B2_CFG.bucket_name;

// Orígenes permitidos para CORS (solo afecta a navegadores).
const ALLOWED_ORIGINS = ((CFG.cors && CFG.cors.origins) || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

// App Check opcional (ver cabecera del archivo).
const APP_CHECK_ENFORCE =
  String((CFG.appcheck && CFG.appcheck.enforce) || "").toLowerCase() === "true";

function b2Request({ url, method = "GET", headers = {}, body = null }) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = require("https").request({
      hostname: u.hostname,
      path: u.pathname + u.search,
      method,
      headers,
    }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); } catch { resolve(data); }
        } else {
          reject(new Error(`B2 ${method} ${u.pathname} → ${res.statusCode}: ${data}`));
        }
      });
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

function authHeader() {
  return "Basic " + Buffer.from(`${KEY_ID}:${APP_KEY}`).toString("base64");
}

let session = null;
let sessionCreatedAt = 0;
async function getSession() {
  if (session && Date.now() - sessionCreatedAt < 12 * 3600 * 1000) return session;
  const resp = await b2Request({
    url: "https://api.backblazeb2.com/b2api/v2/b2_authorize_account",
    headers: { Authorization: authHeader() },
  });
  session = {
    apiUrl: resp.apiUrl,
    authToken: resp.authorizationToken,
    downloadUrl: resp.downloadUrl,
  };
  sessionCreatedAt = Date.now();
  console.log("B2 session renewed");
  return session;
}

async function signDownloadUrl(session, fileName, ttlSec) {
  const resp = await b2Request({
    url: `${session.apiUrl}/b2api/v2/b2_get_download_authorization`,
    method: "POST",
    headers: {
      Authorization: session.authToken,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      bucketId: BUCKET_ID,
      fileNamePrefix: fileName,
      validDurationInSeconds: ttlSec,
    }),
  });
  return `${session.downloadUrl}/file/${BUCKET_NAME}/${fileName}?Authorization=${resp.authorizationToken}`;
}

// ── SEGURIDAD ──────────────────────────────────────────────

function httpError(code, message) {
  const err = new Error(message);
  err.code = code;
  return err;
}

/**
 * Verifica `Authorization: Bearer <idToken>` con Firebase Auth y devuelve
 * el uid del usuario. Lanza 401 si falta o es inválido.
 */
async function authenticate(req) {
  const match = /^Bearer\s+(.+)$/i.exec(req.headers.authorization || "");
  if (!match) throw httpError(401, "Falta Authorization: Bearer <idToken>");
  try {
    const decoded = await admin.auth().verifyIdToken(match[1].trim());
    return decoded.uid;
  } catch (e) {
    throw httpError(401, "ID token inválido o vencido: " + e.message);
  }
}

/**
 * App Check opcional: si appcheck.enforce=true, exige el header
 * X-Firebase-AppCheck con un token válido.
 */
async function enforceAppCheck(req) {
  if (!APP_CHECK_ENFORCE) return;
  const token = (req.headers["x-firebase-appcheck"] || "").trim();
  if (!token) throw httpError(401, "Falta header X-Firebase-AppCheck");
  try {
    await admin.appCheck().verifyToken(token);
  } catch (e) {
    throw httpError(401, "App Check token inválido: " + e.message);
  }
}

/**
 * Valida que la key del bucket pertenezca al usuario autenticado.
 * Las keys usan el formato "<tipo>/<uid>/<archivo>" (reels/UID/123.mp4,
 * posts/UID/x.jpg, stories/UID/x.mp4...) o "<uid>/<archivo>", por lo que el
 * uid debe ser el primer o segundo segmento del path. Así un usuario no puede
 * pisar, firmar ni borrar el contenido de otro.
 */
function assertOwnKey(uid, key) {
  if (typeof key !== "string" || key.length === 0) {
    throw httpError(400, "Falta key");
  }
  if (!/^[A-Za-z0-9._/-]+$/.test(key) || key.includes("..")) {
    throw httpError(400, "Formato de key inválido");
  }
  const segments = key.split("/").filter(Boolean);
  const owned = segments.length >= 2 && (segments[0] === uid || segments[1] === uid);
  if (!owned) throw httpError(403, `No autorizado para operar sobre "${key}"`);
}

// CORS restringido: solo responde Access-Control-Allow-Origin para orígenes
// en la allowlist. Peticiones sin header Origin (APK, curl) no se ven afectadas.
const cors = corsFactory({
  origin: (origin, cb) => cb(
    null,
    origin && ALLOWED_ORIGINS.includes(origin) ? origin : false
  ),
});

// Envuelve cada handler con CORS + manejo uniforme de errores HTTP.
function handle(fn) {
  return (req, res) => cors(req, res, async () => {
    try {
      await fn(req, res);
    } catch (e) {
      console.error(`${req.method} ${req.path} →`, e);
      res.status(e.code || 500).json({ error: e.message });
    }
  });
}

// ── ENDPOINTS B2 ──────────────────────────────────────────

exports.uploadReel = functions.https.onRequest(handle(async (req, res) => {
  if (req.method !== "POST") throw httpError(405, "POST only");
  const uid = await authenticate(req);
  await enforceAppCheck(req);

  const { key, contentType = "video/mp4" } = req.body || {};
  assertOwnKey(uid, key);

  const sess = await getSession();
  const upResp = await b2Request({
    url: `${sess.apiUrl}/b2api/v2/b2_get_upload_url`,
    method: "POST",
    headers: { Authorization: sess.authToken, "Content-Type": "application/json" },
    body: JSON.stringify({ bucketId: BUCKET_ID }),
  });
  const signedUrl = await signDownloadUrl(sess, key, 3600);
  const response = {
    uploadUrl: upResp.uploadUrl,
    uploadAuthToken: upResp.authorizationToken,
    signedDownloadUrl: signedUrl,
    bucketName: BUCKET_NAME,
    key, expiresIn: 3600,
  };
  if (key.startsWith("reels/")) {
    const thumbKey = key.replace(/\.mp4$/, "_thumb.jpg");
    const thumbUp = await b2Request({
      url: `${sess.apiUrl}/b2api/v2/b2_get_upload_url`,
      method: "POST",
      headers: { Authorization: sess.authToken, "Content-Type": "application/json" },
      body: JSON.stringify({ bucketId: BUCKET_ID }),
    });
    response.thumbnailKey = thumbKey;
    response.thumbnailUploadUrl = thumbUp.uploadUrl;
    response.thumbnailUploadAuthToken = thumbUp.authorizationToken;
    response.thumbnailSignedUrl = await signDownloadUrl(sess, thumbKey, 3600);
  }
  res.json(response);
}));

exports.signDownload = functions.https.onRequest(handle(async (req, res) => {
  // Se acepta GET (cliente actual) y POST (clientes antiguos con query string).
  if (req.method !== "GET" && req.method !== "POST") {
    throw httpError(405, "GET o POST only");
  }
  const uid = await authenticate(req);
  await enforceAppCheck(req);

  const key = req.query.key;
  assertOwnKey(uid, key);

  const ttlSec = parseInt(req.query.ttl) || 3600;
  const ttl = Math.min(Math.max(ttlSec, 1), 604800);
  const sess = await getSession();
  res.json({
    signedUrl: await signDownloadUrl(sess, key, ttl),
    expiresAt: Date.now() + ttl * 1000,
    expiresIn: ttl,
  });
}));

exports.deleteFile = functions.https.onRequest(handle(async (req, res) => {
  if (req.method !== "DELETE") throw httpError(405, "DELETE only");
  const uid = await authenticate(req);
  await enforceAppCheck(req);

  const { key } = req.body || {};
  assertOwnKey(uid, key);

  const sess = await getSession();
  const list = await b2Request({
    url: `${sess.apiUrl}/b2api/v2/b2_list_file_names`,
    method: "POST",
    headers: { Authorization: sess.authToken, "Content-Type": "application/json" },
    body: JSON.stringify({ bucketId: BUCKET_ID, startFileName: key, maxFileCount: 1 }),
  });
  const file = list.files && list.files[0];
  if (!file) return res.status(404).json({ ok: false });
  await b2Request({
    url: `${sess.apiUrl}/b2api/v2/b2_delete_file_version`,
    method: "POST",
    headers: { Authorization: sess.authToken, "Content-Type": "application/json" },
    body: JSON.stringify({ fileName: file.fileName, fileId: file.fileId }),
  });
  res.json({ ok: true });
}));

/*
 * ════════════════════════════════════════════════════
 * TRIGGERS DE NOTIFICACIONES PUSH (requieren Blaze)
 * ════════════════════════════════════════════════════
 * Descomenta esta sección cuando actives el plan Blaze.
 * Mientras tanto, las notificaciones funcionan localmente
 * desde el APK (LocalNotificationWatcher.kt).

const db = admin.firestore();
const messaging = admin.messaging();

async function sendPushToUser(uid, opts) { ... }

exports.onReelLike = functions.firestore
  .document("reels/{reelId}/likes/{userId}").onCreate(...);

exports.onReelComment = functions.firestore
  .document("reels/{reelId}/comments/{commentId}").onCreate(...);

exports.onFollow = functions.firestore
  .document("users/{uid}/followers/{followerUid}").onCreate(...);

exports.onMessageCreated = functions.firestore
  .document("chats/{chatId}/messages/{messageId}").onCreate(...);
*/
