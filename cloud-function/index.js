/**
 * Vivid — Cloud Function proxy a Backblaze B2 con Signed URLs
 * ============================================================
 *
 * NOTA: Las Cloud Functions requieren plan Blaze (pay-as-you-go).
 * Si estás en Spark (gratuito), solo se desplegarán las reglas de Firestore.
 *
 * Las notificaciones push sin Blaze se envían mediante cloudflare-worker/.
 * Este archivo conserva únicamente el proxy B2 existente.
 */

const functions = require("firebase-functions");
const cors = require("cors")({ origin: true });
const { URL } = require("url");

const CFG = functions.config().b2 || {};
const KEY_ID = CFG.key_id;
const APP_KEY = CFG.application_key;
const BUCKET_ID = CFG.bucket_id;
const BUCKET_NAME = CFG.bucket_name;

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

// ── ENDPOINTS B2 (no requieren Blaze) ─────────────

exports.uploadReel = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    if (req.method !== "POST") return res.status(405).send("POST only");
    try {
      const { key, contentType = "video/mp4" } = req.body || {};
      if (!key) return res.status(400).json({ error: "Falta key" });
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
    } catch (e) {
      console.error("uploadReel error:", e);
      res.status(500).json({ error: e.message });
    }
  });
});

exports.signDownload = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    try {
      const key = req.query.key;
      const ttlSec = parseInt(req.query.ttl) || 3600;
      if (!key) return res.status(400).json({ error: "Falta key" });
      const sess = await getSession();
      res.json({
        signedUrl: await signDownloadUrl(sess, key, Math.min(Math.max(ttlSec, 1), 604800)),
        expiresAt: Date.now() + Math.min(Math.max(ttlSec, 1), 604800) * 1000,
        expiresIn: Math.min(Math.max(ttlSec, 1), 604800),
      });
    } catch (e) {
      console.error("signDownload error:", e);
      res.status(500).json({ error: e.message });
    }
  });
});

exports.deleteFile = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    if (req.method !== "DELETE") return res.status(405).send("DELETE only");
    try {
      const { key } = req.body || {};
      if (!key) return res.status(400).json({ error: "Falta key" });
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
    } catch (e) {
      console.error("deleteFile error:", e);
      res.status(500).json({ ok: false, error: e.message });
    }
  });
});

/*
 * ════════════════════════════════════════════════════
 * TRIGGERS DE NOTIFICACIONES PUSH (requieren Blaze)
 * ════════════════════════════════════════════════════
 * Alternativa futura si se activa Blaze. La implementación actual sin Blaze
 * vive en cloudflare-worker/ y la app la invoca mediante WorkManager.

const admin = require("firebase-admin");
admin.initializeApp();
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
