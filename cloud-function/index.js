/**
 * Vivid — Cloud Function proxy a Backblaze B2 con Signed URLs + Push Notifications
 * ============================================================
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
//  Configuración B2
// =====================================================
const CFG = functions.config().b2 || {};
const KEY_ID = CFG.key_id;
const APP_KEY = CFG.application_key;
const BUCKET_ID = CFG.bucket_id;
const BUCKET_NAME = CFG.bucket_name;

if (!KEY_ID || !APP_KEY || !BUCKET_ID || !BUCKET_NAME) {
  console.error("Faltan credenciales B2. firebase functions:config:set b2.*");
}

// =====================================================
//  Helpers B2
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

// =====================================================
//  ENDPOINTS HTTP
// =====================================================

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
        headers: {
          Authorization: sess.authToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ bucketId: BUCKET_ID }),
      });
      const signedUrl = await signDownloadUrl(sess, key, 3600);

      const response = {
        uploadUrl: upResp.uploadUrl,
        uploadAuthToken: upResp.authorizationToken,
        signedDownloadUrl: signedUrl,
        bucketName: BUCKET_NAME,
        key,
        expiresIn: 3600,
      };

      if (key.startsWith("reels/")) {
        const thumbKey = key.replace(/\.mp4$/, "_thumb.jpg");
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

exports.signDownload = functions.https.onRequest((req, res) => {
  cors(req, res, async () => {
    try {
      const key = req.query.key;
      const ttlSec = parseInt(req.query.ttl) || 3600;
      const validTtl = Math.min(Math.max(ttlSec, 1), 604800);
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
//  PUSH NOTIFICATIONS — Firestore Triggers
// =====================================================

const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Helper: envía FCM al usuario buscando sus tokens en /users/{uid}/fcmTokens
 */
async function sendPushToUser(uid, {
  title,
  body,
  data,
  tag,
  notificationType,
  channelId = "general_channel",
  priority = "normal",
}) {
  try {
    const userDoc = await db.collection("users").document(uid).get();
    const userData = userDoc.data();

    const notificationSettings = {
      "reel_like": userData?.notifyLikesComments ?? true,
      "reel_comment": userData?.notifyLikesComments ?? true,
      "new_follower": userData?.notifyNewFollowers ?? true,
      "message": userData?.notifyDirectMessages ?? true,
    };

    if (notificationType && notificationSettings[notificationType] === false) {
      console.log(`Notificaciones tipo ${notificationType} desactivadas para ${uid}`);
      return;
    }

    const tokensSnap = await db.collection("users").document(uid)
      .collection("fcmTokens").get();

    if (tokensSnap.empty) {
      console.log(`No FCM tokens for user ${uid}`);
      return;
    }

    const tokens = tokensSnap.docs.map(d => d.id);
    const message = {
      notification: { title, body },
      data: { ...data, channelId },
      android: {
        priority,
        notification: {
          tag,
          channelId,
          clickAction: "FLUTTER_NOTIFICATION_CLICK",
        },
      },
      tokens,
    };

    const response = await messaging.sendMulticast(message);
    console.log(`Sent ${response.successCount}/${tokens.length} pushes to ${uid} (${notificationType})`);

    if (response.failureCount > 0) {
      const failedTokens = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          console.error(`Token failed: ${tokens[idx]?.substring(0, 20)}...`, resp.error?.message);
          failedTokens.push(tokens[idx]);
        }
      });
      for (const token of failedTokens) {
        await db.collection("users").document(uid)
          .collection("fcmTokens").doc(token).delete();
      }
    }
  } catch (e) {
    console.error(`Error en sendPushToUser para ${uid}:`, e);
  }
}

/* ── Triggers ── */

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
      if (ownerUid === likerUid) return null;

      const likerDoc = await db.collection("users").document(likerUid).get();
      const likerName = likerDoc.data()?.username || "alguien";

      await sendPushToUser(ownerUid, {
        title: "❤️ Nuevo like",
        body: `A ${likerName} le gustó tu reel`,
        data: { type: "reel_like", reelId, fromUserId: likerUid },
        tag: `reel_like_${reelId}`,
        notificationType: "reel_like",
        channelId: "general_channel",
      });
    } catch (e) {
      console.error("onReelLike error:", e);
    }
    return null;
  });

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
        title: `💬 ${authorName} comentó`,
        body: comment.text?.substring(0, 100) || "Nuevo comentario en tu reel",
        data: { type: "reel_comment", reelId, fromUserId: authorUid },
        tag: `reel_comment_${reelId}`,
        notificationType: "reel_comment",
        channelId: "general_channel",
      });
    } catch (e) {
      console.error("onReelComment error:", e);
    }
    return null;
  });

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
        title: "👋 Nuevo seguidor",
        body: `${followerName} empezó a seguirte`,
        data: { type: "new_follower", fromUserId: followerUid },
        tag: `follow_${followerUid}`,
        notificationType: "new_follower",
        channelId: "general_channel",
      });
    } catch (e) {
      console.error("onFollow error:", e);
    }
    return null;
  });

exports.onMessageCreated = functions.firestore
  .document("chats/{chatId}/messages/{messageId}")
  .onCreate(async (snap, context) => {
    const chatId = context.params.chatId;
    const message = snap.data();
    if (!message) return null;
    try {
      const chatDoc = await db.collection("chats").document(chatId).get();
      const chatData = chatDoc.data();
      if (!chatData) return null;

      const participants = chatData.participants || [];
      const senderId = message.senderId;
      const receiverId = participants.find(p => p !== senderId);
      if (!receiverId) return null;

      const senderDoc = await db.collection("users").document(senderId).get();
      const senderName = senderDoc.data()?.username || "Alguien";

      await sendPushToUser(receiverId, {
        title: senderName,
        body: message.text?.substring(0, 150) || "Nuevo mensaje",
        data: { type: "message", chatId, fromUserId: senderId },
        tag: `chat_msg_${chatId}`,
        notificationType: "message",
        channelId: "messages_channel",
        priority: "high",
      });
    } catch (e) {
      console.error("onMessageCreated error:", e);
    }
    return null;
  });
