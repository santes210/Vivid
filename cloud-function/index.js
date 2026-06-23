/**
 * Vivid — Cloud Function proxy a Backblaze B2 con Signed URLs
 * ============================================================
 *
 * ESTA FUNCIÓN SE EJECUTA EN FIREBASE CLOUD FUNCTIONS (Node 18).
 * Tu bucket es PRIVADO, así que esta es la ÚNICA forma de subir
 * y servir archivos de forma segura.
 *
 * Flujo completo:
 *   1. App llama a /uploadReel    → recibe uploadUrl + publicUrl
 *   2. App hace PUT directo a uploadUrl (B2, sin intermediario)
 *   3. App guarda publicUrl en Firestore
 *   4. Cuando alguien quiere VER el video, app llama a /signDownload
 *   5. Cloud Function pide a B2 un authorization token (TTL 1h)
 *   6. App usa {url}?Authorization={token} con ExoPlayer
 *
 * Setup:
 *   cd cloud-function
 *   npm install
 *   firebase functions:config:set b2.key_id="0048f6433d84d000000000004"
 *   firebase functions:config:set b2.application_key="K004..."
 *   firebase functions:config:set b2.bucket_id="..."
 *   firebase functions:config:set b2.bucket_name="VividRivers"
 *   firebase deploy --only functions
 */

const functions = require("firebase-functions");
const cors = require("cors")({ origin: true });
const { URL } = require("url");

// =====================================================
//  Configuración (nunca se compila en el APK)
// =====================================================
const CFG = functions.config().b2 || {};
const KEY_ID = CFG.key_id;
const APP_KEY = CFG.application_key;
const BUCKET_ID = CFG.bucket_id;
const BUCKET_NAME = CFG.bucket_name;
const FRIENDLY_URL = CFG.friendly_url; // ej. https://f002.backblazeb2.com

if (!KEY_ID || !APP_KEY || !BUCKET_ID || !BUCKET_NAME) {
  console.error("Faltan credenciales B2. firebase functions:config:set b2.*");
}

// =====================================================
//  Helpers — protocolo nativo B2
// =====================================================

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

// Cache de sesión (24h)
let session = null;
let sessionCreatedAt = 0;
async function getSession() {
  // Renovar si tiene más de 12h (B2 dura 24h)
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
  return session;
}

// =====================================================
//  ENDPOINTS
// =====================================================

/**
 * POST /uploadReel  body: { key, contentType }
 *   → { uploadUrl, publicUrl, signedDownloadUrl, thumbnailUploadUrl?, expiresIn }
 *
 * Devuelve:
 *   - uploadUrl: para hacer PUT del archivo
 *   - signedDownloadUrl: URL firmada lista para ExoPlayer (TTL 1h)
 *   - thumbnailUploadUrl: si key empieza por "reels/" devuelve también
 *     un uploadUrl paralelo para subir el thumbnail.jpg
 */
exports.uploadReel = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    if (req.method !== "POST") return res.status(405).send("POST only");
    try {
      const { key, contentType = "video/mp4" } = req.body || {};
      if (!key) return res.status(400).json({ error: "Falta key" });

      const sess = await getSession();

      // 1. URL de subida para el video
      const upResp = await b2Request({
        url: `${sess.apiUrl}/b2api/v2/b2_get_upload_url`,
        method: "POST",
        headers: {
          Authorization: sess.authToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ bucketId: BUCKET_ID }),
      });

      // 2. URL firmada para DESPUÉS de subir (para que ExoPlayer pueda leer)
      const signedUrl = await signDownloadUrl(sess, key, 3600); // 1h

      const response = {
        uploadUrl: upResp.uploadUrl,
        uploadAuthToken: upResp.authorizationToken,
        signedDownloadUrl: signedUrl,
        bucketName: BUCKET_NAME,
        key,
        expiresIn: 3600,
      };

      // 3. Si es un reel, devolver también el uploadUrl del thumbnail
      if (key.startsWith("reels/")) {
        const thumbKey = key.replace(/\.mp4$/, "_thumb.jpg");
        // Necesitamos un uploadUrl NUEVO (no se pueden reutilizar)
        const thumbUp = await b2Request({
          url: `${sess.apiUrl}/b2api/v2/b2_get_upload_url`,
          method: "POST",
          headers: {
            Authorization: sess.authToken,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ bucketId: BUCKET_ID }),
        });
        const thumbSigned = await signDownloadUrl(sess, thumbKey, 3600);
        response.thumbnailKey = thumbKey;
        response.thumbnailUploadUrl = thumbUp.uploadUrl;
        response.thumbnailUploadAuthToken = thumbUp.authorizationToken;
        response.thumbnailSignedUrl = thumbSigned;
      }

      res.json(response);
    } catch (e) {
      console.error("uploadReel error:", e);
      res.status(500).json({ error: e.message });
    }
  });
});

/**
 * GET /signDownload?key=...&ttl=3600
 *   → { signedUrl, expiresAt }
 *
 * Refresca una URL firmada sin tener que subir nada nuevo.
 * Útil cuando el usuario hace scroll y el token anterior está por expirar.
 */
exports.signDownload = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    try {
      const key = req.query.key;
      const ttlSec = parseInt(req.query.ttl) || 3600;
      const validTtl = Math.min(Math.max(ttlSec, 1), 604800); // 1s..7d
      if (!key) return res.status(400).json({ error: "Falta key" });

      const sess = await getSession();
      const signedUrl = await signDownloadUrl(sess, key, validTtl);

      res.json({
        signedUrl,
        expiresAt: Date.now() + validTtl * 1000,
        expiresIn: validTtl,
      });
    } catch (e) {
      console.error("signDownload error:", e);
      res.status(500).json({ error: e.message });
    }
  });
});

/**
 * Firma una URL de descarga usando b2_get_download_authorization.
 *
 *   URL final = {friendlyDownloadUrl}/file/{bucket}/{fileName}?Authorization={token}
 *
 * El token es válido entre 1 segundo y 7 días.
 */
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

/**
 * DELETE /deleteFile  body: { key }
 *   → { ok: true }
 */
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
        headers: {
          Authorization: sess.authToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ bucketId: BUCKET_ID, startFileName: key, maxFileCount: 1 }),
      });
      const file = list.files && list.files[0];
      if (!file) return res.status(404).json({ ok: false });

      await b2Request({
        url: `${sess.apiUrl}/b2api/v2/b2_delete_file_version`,
        method: "POST",
        headers: {
          Authorization: sess.authToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ fileName: file.fileName, fileId: file.fileId }),
      });

      res.json({ ok: true });
    } catch (e) {
      console.error("deleteFile error:", e);
      res.status(500).json({ ok: false, error: e.message });
    }
  });
});

// =====================================================
//  PUSH NOTIFICATIONS — Triggers de Firestore
// =====================================================
//  Cuando alguien interactua con un reel/story, se dispara
//  una Cloud Function que envia FCM al dueno del contenido.
//
//  Requiere firebase-admin instalado y Authentication habilitada.

const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Trigger: cuando se crea un documento en /reels/{reelId}/likes/{userId}
 *   -> envia push al dueno del reel
 */
exports.onReelLike = functions.firestore
  .document("reels/{reelId}/likes/{userId}")
  .onCreate(async (snap, context) => {
    const reelId = context.params.reelId;
    const likerUid = context.params.userId;

    try {
      const reelDoc = await db.collection("reels").document(reelId).get();
      const reelData = reelDoc.data();
      if (!reelData) return null;

      const ownerUid = reelData.userId;
      if (ownerUid === likerUid) return null; // no auto-notificar

      const likerDoc = await db.collection("users").document(likerUid).get();
      const likerName = likerDoc.data()?.username || "alguien";

      await sendPushToUser(ownerUid, {
        title: "Vivid",
        body: `${likerName} le dio like a tu reel`,
        data: {
          type: "reel_like",
          reelId,
          fromUserId: likerUid,
        },
        tag: `reel_like_${reelId}`,
      });
    } catch (e) {
      console.error("onReelLike error:", e);
    }
    return null;
  });

/**
 * Trigger: cuando se crea un comentario en /reels/{reelId}/comments/{commentId}
 *   -> envia push al dueno del reel
 */
exports.onReelComment = functions.firestore
  .document("reels/{reelId}/comments/{commentId}")
  .onCreate(async (snap, context) => {
    const reelId = context.params.reelId;
    const comment = snap.data();
    if (!comment) return null;

    try {
      const reelDoc = await db.collection("reels").document(reelId).get();
      const reelData = reelDoc.data();
      if (!reelData) return null;

      const ownerUid = reelData.userId;
      const authorUid = comment.userId;
      if (ownerUid === authorUid) return null;

      const authorDoc = await db.collection("users").document(authorUid).get();
      const authorName = authorDoc.data()?.username || "alguien";

      await sendPushToUser(ownerUid, {
        title: `${authorName} comento`,
        body: comment.text?.substring(0, 100) || "Nuevo comentario",
        data: {
          type: "reel_comment",
          reelId,
          fromUserId: authorUid,
        },
        tag: `reel_comment_${reelId}`,
      });
    } catch (e) {
      console.error("onReelComment error:", e);
    }
    return null;
  });

/**
 * Trigger: cuando alguien sigue a otro usuario
 *   -> envia push al usuario seguido
 */
exports.onFollow = functions.firestore
  .document("users/{uid}/followers/{followerUid}")
  .onCreate(async (snap, context) => {
    const ownerUid = context.params.uid;
    const followerUid = context.params.followerUid;
    if (ownerUid === followerUid) return null;

    try {
      const followerDoc = await db.collection("users").document(followerUid).get();
      const followerName = followerDoc.data()?.username || "alguien";

      await sendPushToUser(ownerUid, {
        title: "Vivid",
        body: `${followerName} empezo a seguirte`,
        data: {
          type: "new_follower",
          fromUserId: followerUid,
        },
        tag: `follow_${followerUid}`,
      });
    } catch (e) {
      console.error("onFollow error:", e);
    }
    return null;
  });

/**
 * Helper: envia FCM al usuario buscando sus tokens en /users/{uid}/fcmTokens
 */
async function sendPushToUser(uid, { title, body, data, tag }) {
  const tokensSnap = await db.collection("users").document(uid)
    .collection("fcmTokens").get();

  if (tokensSnap.empty) {
    console.log(`No FCM tokens for user ${uid}`);
    return;
  }

  const tokens = tokensSnap.docs.map(d => d.id);
  const message = {
    notification: { title, body },
    data: data || {},
    android: {
      priority: "high",
      notification: {
        tag,         // colapsa multiples pushes en uno
        click_action: "OPEN_REEL",
      },
    },
    tokens,
  };

  const response = await messaging.sendMulticast(message);
  console.log(`Sent ${response.successCount} push(es) to ${uid}`);

  // Limpia tokens invalidos
  if (response.failureCount > 0) {
    const failedTokens = [];
    response.responses.forEach((resp, idx) => {
      if (!resp.success) failedTokens.push(tokens[idx]);
    });
    for (const token of failedTokens) {
      await db.collection("users").document(uid)
        .collection("fcmTokens").doc(token).delete();
    }
  }
}
