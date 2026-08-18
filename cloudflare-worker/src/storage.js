/**
 * Broker de almacenamiento Backblaze B2.
 *
 * Las claves de B2 viven SOLO aquí, como secretos cifrados de Cloudflare.
 * La app nunca las ve: pide permisos temporales con su ID token de Firebase.
 *
 * Los bytes del archivo NUNCA pasan por el Worker. El Worker entrega una
 * uploadUrl de B2 y la app sube el binario directamente. Así se esquiva el
 * límite de 100 MB de cuerpo de petición del plan gratuito y no se consume
 * ni CPU ni ancho de banda del Worker.
 *
 * Endpoints:
 *   POST /storage/upload-url  → { remoteKey, uploadUrl, uploadAuthToken }
 *                              (valida contentType, sizeBytes y cuota)
 *   POST /storage/complete    → { ok, sizeBytes, usageBytes, quotaBytes }
 *                              (verifica el tamaño real en B2 y registra la
 *                               cuota del usuario; idempotente por uploadId)
 *   POST /storage/sign        → { signedUrl, expiresAt }
 *   POST /storage/delete      → { ok, deleted } (y descuenta la cuota)
 *
 * Límites y cuotas:
 *   El Worker nunca ve los bytes (la app sube directo a B2), así que la
 *   protección contra abuso se apoya en tres capas:
 *     1. Límite por tipo MIME (SIZE_LIMITS_BYTES) validado en upload-url con
 *        el sizeBytes que declara la app y RE-verificado en complete contra
 *        el tamaño real que reporta B2.
 *     2. Cuota por usuario y namespace (NAMESPACE_QUOTA_BYTES) con un ledger
 *        en Firestore (_storageUsage/{uid}/namespaces/{namespace}) que se
 *        suma en complete y se resta en delete.
 *     3. Allowlist de contentType (ALLOWED_CONTENT_TYPES).
 */

const B2_API_VERSION = "v4";
const B2_AUTHORIZE_URL = `https://api.backblazeb2.com/b2api/${B2_API_VERSION}/b2_authorize_account`;

// TTL máximo que admite b2_get_download_authorization (7 días).
export const MAX_SIGNED_TTL_SEC = 604_800;
const DEFAULT_SIGNED_TTL_SEC = MAX_SIGNED_TTL_SEC;

// La sesión de B2 dura 24 h. Se cachea en el isolate para no reautorizar
// en cada petición. Si el isolate se recicla, simplemente se vuelve a pedir.
let b2SessionCache = { session: null, expiresAt: 0 };

// Tipos de contenido permitidos. Evita que alguien use el bucket como
// hosting de binarios arbitrarios.
const ALLOWED_CONTENT_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
  "video/mp4",
  "audio/mp4",
  "audio/aac",
  "audio/mpeg",
  "audio/ogg",
  "audio/wav"
]);

// -------------------------------------------------------------------------
// Límites de tamaño y cuotas por usuario
// -------------------------------------------------------------------------
const MB = 1024 * 1024;
const GB = 1024 * MB;

// Tamaño máximo por tipo de contenido. La app comprime antes de subir
// (imágenes ≤ ~1.5 MB, vídeos 720p a 1.2-2.5 Mbps ≈ 45-90 MB en 5 min,
// notas de voz AAC .m4a ≈ 0.5 MB/min), así que estos techos son generosos.
const SIZE_LIMITS_BYTES = Object.freeze({
  "image/jpeg": 15 * MB,
  "image/png": 15 * MB,
  "image/webp": 15 * MB,
  "image/gif": 20 * MB,
  "video/mp4": 300 * MB,
  "audio/mp4": 30 * MB, // .m4a (notas de voz AAC)
  "audio/aac": 30 * MB,
  "audio/mpeg": 30 * MB,
  "audio/ogg": 30 * MB,
  "audio/wav": 50 * MB
});
const DEFAULT_SIZE_LIMIT_BYTES = 25 * MB;

// Techo absoluto para CUALQUIER archivo, se sobreescriba o no el límite por
// tipo. Env: UPLOAD_MAX_BYTES.
const ABSOLUTE_MAX_BYTES = 512 * MB;

// Cuota de almacenamiento por usuario y namespace: la suma de bytes de los
// archivos VIVOS del usuario en ese namespace no puede superarla. Env:
// UPLOAD_QUOTA_BYTES redefine la cuota global de un usuario (aplica a todos
// los namespaces).
const NAMESPACE_QUOTA_BYTES = Object.freeze({
  posts: 1.5 * GB,
  reels: 3 * GB,
  stories: 1 * GB,
  avatars: 200 * MB,
  chat_images: 1 * GB,
  chat_voice: 500 * MB
});
const DEFAULT_QUOTA_BYTES = 2 * GB;

/**
 * Prefijos válidos y cómo se autoriza cada uno.
 *
 *   owner → la segunda parte de la ruta debe ser el uid del usuario
 *   chat  → la segunda parte es un chatId y el usuario debe ser participante
 */
const KEY_NAMESPACES = {
  posts: "owner",
  reels: "owner",
  stories: "owner",
  avatars: "owner",
  chat_images: "chat",
  chat_voice: "chat"
};

// -------------------------------------------------------------------------
// Router
// -------------------------------------------------------------------------

/**
 * @param {Request} request
 * @param {string} pathname
 * @param {object} deps  helpers compartidos con index.js
 * @returns {Promise<Response>|null} null si la ruta no es de storage
 */
export function handleStorageRoute(request, pathname, deps) {
  if (!pathname.startsWith("/storage/")) return null;
  if (request.method !== "POST") return deps.json({ error: "Method not allowed" }, 405);

  switch (pathname) {
    case "/storage/upload-url": return createUploadUrl(request, deps);
    case "/storage/complete": return completeUpload(request, deps);
    case "/storage/sign": return signDownload(request, deps);
    case "/storage/delete": return deleteObject(request, deps);
    default: return deps.json({ error: "Not found" }, 404);
  }
}

// -------------------------------------------------------------------------
// POST /storage/upload-url
// -------------------------------------------------------------------------

async function createUploadUrl(request, deps) {
  const { env, HttpError, json } = deps;
  const actorUid = await deps.authenticate(request);
  const body = await deps.readJson(request);

  const remoteKey = normalizeKey(body.key, HttpError);
  const contentType = normalizeContentType(body.contentType, HttpError);
  const sizeBytes = normalizeSize(body.sizeBytes, HttpError);
  const namespace = keyNamespace(remoteKey, HttpError);
  await assertCanWrite(remoteKey, actorUid, deps);

  // Límites de tamaño y cuota ANTES de entregar el ticket de B2. El sizeBytes
  // lo declara la app (File.length()) y se vuelve a comprobar contra el
  // tamaño REAL en /storage/complete, así que mentir aquí solo engaña al
  // propio ticket.
  if (sizeBytes > sizeLimitFor(contentType)) {
    throw new HttpError(413, `File too large: max ${sizeLimitFor(contentType)} bytes for ${contentType}`);
  }
  if (sizeBytes > absoluteMaxBytes(env)) {
    throw new HttpError(413, "File too large");
  }
  await assertQuotaAvailable(actorUid, namespace, sizeBytes, deps, env);

  const session = await getB2Session(env, HttpError);
  const bucketId = requireEnv(env, "B2_BUCKET_ID", HttpError);

  // Intenta acotar el permiso a la carpeta del usuario. El token que devuelve
  // b2_get_upload_url autoriza subir a CUALQUIER ruta del bucket, así que
  // primero creamos una application key temporal restringida por prefijo.
  const prefix = keyPrefix(remoteKey);
  let uploadSession = session;
  let restricted = false;

  if (env.B2_RESTRICT_UPLOAD_KEYS !== "false") {
    const scoped = await createScopedKey(session, bucketId, prefix, HttpError);
    if (scoped) {
      uploadSession = scoped;
      restricted = true;
    } else {
      // La clave del Worker no tiene capacidad writeKeys. Se sigue adelante,
      // pero conviene saberlo: el token de subida no queda acotado al prefijo.
      console.warn(
        "B2: no se pudo crear una key restringida (falta la capacidad writeKeys). " +
        "El token de subida no queda limitado al prefijo del usuario."
      );
    }
  }

  const upload = await b2Post(uploadSession, "b2_get_upload_url", { bucketId }, HttpError);

  return json({
    remoteKey,
    contentType,
    uploadUrl: upload.uploadUrl,
    uploadAuthToken: upload.authorizationToken,
    restricted
  });
}

/**
 * Crea una application key efímera limitada al bucket y al prefijo indicado.
 * Devuelve una sesión B2 lista para usar, o null si no se pudo crear.
 */
async function createScopedKey(session, bucketId, namePrefix, HttpError) {
  try {
    const created = await b2Post(session, "b2_create_key", {
      capabilities: ["writeFiles"],
      keyName: scopedKeyName(namePrefix),
      bucketId,
      namePrefix,
      validDurationInSeconds: 3600
    }, HttpError);

    // Hay que autorizar de nuevo con la clave recién creada para obtener su
    // propio apiUrl y token.
    return await b2Authorize(created.applicationKeyId, created.applicationKey, HttpError);
  } catch (error) {
    return null;
  }
}

/** B2 solo admite [A-Za-z0-9-] en keyName y máximo 100 caracteres. */
function scopedKeyName(prefix) {
  const safe = prefix.replace(/[^A-Za-z0-9-]/g, "-").slice(0, 60);
  return `vivid-up-${safe}-${Date.now().toString(36)}`.slice(0, 100);
}

// -------------------------------------------------------------------------
// POST /storage/complete
// -------------------------------------------------------------------------

/**
 * Confirma una subida ya realizada directamente a B2.
 *
 * El Worker no ve los bytes, así que aquí se verifica con la metadata real
 * de B2 (b2_list_file_names) que el archivo existe y que su tamaño no excede
 * los límites, y se registra en el ledger de cuota del usuario.
 *
 * Idempotente por `uploadId` (la app manda "sha1:tamaño"): si el marker ya
 * existe, la cuota ya se contabilizó (retry de la app) y no se duplica.
 */
async function completeUpload(request, deps) {
  const { env, HttpError, json } = deps;
  const actorUid = await deps.authenticate(request);
  const body = await deps.readJson(request);

  const remoteKey = normalizeKey(body.key, HttpError);
  const uploadId = normalizeUploadId(body.uploadId, HttpError);
  const declaredSize = normalizeSize(body.sizeBytes, HttpError);
  const contentType = normalizeContentType(body.contentType, HttpError);
  const namespace = keyNamespace(remoteKey, HttpError);
  await assertCanWrite(remoteKey, actorUid, deps);

  // Tamaño REAL según B2; si la lista aún no lo ve (consistencia eventual),
  // se usa el declarado (que el ticket ya validó contra el mismo límite).
  const actualSize = (await resolveActualSize(remoteKey, deps)) ?? declaredSize;

  if (actualSize > sizeLimitFor(contentType)) {
    throw new HttpError(413, `File too large: max ${sizeLimitFor(contentType)} bytes for ${contentType}`);
  }
  if (actualSize > absoluteMaxBytes(env)) {
    throw new HttpError(413, "File too large");
  }

  // Marker de idempotencia: solo la primera vez contabiliza cuota.
  const markerPath = `_storageUsage/${actorUid}/uploads/${uploadId}`;
  const firstTime = await deps.createIfAbsent(markerPath, {
    key: remoteKey,
    namespace,
    sizeBytes: actualSize,
    createdAt: Date.now()
  });
  if (!firstTime) {
    return json({ ok: true, duplicate: true, sizeBytes: actualSize });
  }

  const usagePath = usageDocPath(actorUid, namespace);
  const usage = await deps.getDocument(usagePath);
  const quota = quotaBytesFor(namespace, env);
  const used = Number(usage?.bytes || 0) + actualSize;
  const files = Number(usage?.files || 0) + 1;

  if (used > quota) {
    // Solo pasa si el cliente declaró en upload-url menos de lo que subió.
    // El archivo queda en B2: la app recibe el 403 y debe borrarlo.
    await deps.deleteDocument(markerPath).catch(() => {});
    throw new HttpError(403, `Upload quota exceeded (${namespace})`);
  }

  // Lectura + escritura sin transacción: dos completes concurrentes pueden
  // perder una suma. Es una protección anti-abuso, no una contabilidad exacta.
  await deps.writeDocument(usagePath, {
    bytes: used,
    files,
    updatedAt: Date.now()
  });

  return json({
    ok: true,
    duplicate: false,
    sizeBytes: actualSize,
    usageBytes: used,
    quotaBytes: quota
  });
}

/** Tamaño real del archivo en B2, o null si aún no es visible en la lista. */
async function resolveActualSize(remoteKey, deps) {
  const { env, HttpError } = deps;
  try {
    const session = await getB2Session(env, HttpError);
    const bucketId = requireEnv(env, "B2_BUCKET_ID", HttpError);
    const listed = await b2Post(session, "b2_list_file_names", {
      bucketId,
      startFileName: remoteKey,
      maxFileCount: 1
    }, HttpError);
    const file = (listed.files || []).find((entry) => entry.fileName === remoteKey);
    return file ? Number(file.contentLength) || null : null;
  } catch (error) {
    return null; // Best-effort: se usará el tamaño declarado (ya validado).
  }
}

// -------------------------------------------------------------------------
// POST /storage/sign
// -------------------------------------------------------------------------

async function signDownload(request, deps) {
  const { env, HttpError, json } = deps;
  const actorUid = await deps.authenticate(request);
  const body = await deps.readJson(request);

  const remoteKey = normalizeKey(body.key, HttpError);
  const ttlSec = normalizeTtl(body.ttlSec, HttpError);
  await assertCanRead(remoteKey, actorUid, deps);

  const session = await getB2Session(env, HttpError);
  const bucketId = requireEnv(env, "B2_BUCKET_ID", HttpError);
  const bucketName = requireEnv(env, "B2_BUCKET_NAME", HttpError);

  const auth = await b2Post(session, "b2_get_download_authorization", {
    bucketId,
    fileNamePrefix: remoteKey,
    validDurationInSeconds: ttlSec
  }, HttpError);

  // MEDIA_BASE_URL permite servir a través de un dominio propio detrás de
  // Cloudflare (Bandwidth Alliance = egress gratis). Si no está configurado
  // se usa el downloadUrl nativo de B2.
  const base = (env.MEDIA_BASE_URL || "").replace(/\/+$/, "") || session.downloadUrl;
  const signedUrl =
    `${base}/file/${bucketName}/${encodeKeyForUrl(remoteKey)}` +
    `?Authorization=${encodeURIComponent(auth.authorizationToken)}`;

  return json({
    signedUrl,
    expiresAt: Math.floor(Date.now() / 1000) + ttlSec
  });
}

// -------------------------------------------------------------------------
// POST /storage/delete
// -------------------------------------------------------------------------

async function deleteObject(request, deps) {
  const { env, HttpError, json } = deps;
  const actorUid = await deps.authenticate(request);
  const body = await deps.readJson(request);

  const remoteKey = normalizeKey(body.key, HttpError);
  // Borrar exige ser el dueño, no basta con poder leerlo.
  await assertCanWrite(remoteKey, actorUid, deps);

  const session = await getB2Session(env, HttpError);
  const bucketId = requireEnv(env, "B2_BUCKET_ID", HttpError);

  const listed = await b2Post(session, "b2_list_file_names", {
    bucketId,
    startFileName: remoteKey,
    maxFileCount: 1
  }, HttpError);

  const file = (listed.files || []).find((entry) => entry.fileName === remoteKey);
  if (!file) return json({ ok: true, deleted: false });

  await b2Post(session, "b2_delete_file_version", {
    fileName: file.fileName,
    fileId: file.fileId
  }, HttpError);

  // Descuenta la cuota del usuario por el tamaño REAL del archivo borrado.
  // El borrador siempre es quien subió (reglas de Firestore: solo el autor
  // borra sus posts/reels/mensajes), así que actorUid == dueño del ledger.
  const fileSize = Number(file.contentLength) || 0;
  if (fileSize > 0) {
    const { namespace } = splitKey(remoteKey, HttpError);
    const usagePath = usageDocPath(actorUid, namespace);
    const usage = await deps.getDocument(usagePath);
    if (usage) {
      const bytes = Math.max(0, Number(usage.bytes || 0) - fileSize);
      const files = Math.max(0, Number(usage.files || 0) - 1);
      await deps.writeDocument(usagePath, { bytes, files, updatedAt: Date.now() });
    }
  }

  return json({ ok: true, deleted: true });
}

// -------------------------------------------------------------------------
// Autorización
// -------------------------------------------------------------------------

/** Escribir o borrar: hay que ser el dueño de la carpeta. */
async function assertCanWrite(remoteKey, actorUid, deps) {
  const { HttpError } = deps;
  const { namespace, scope } = splitKey(remoteKey, HttpError);

  if (KEY_NAMESPACES[namespace] === "owner") {
    if (scope !== actorUid) throw new HttpError(403, "Key does not belong to the authenticated user");
    return;
  }
  await assertChatParticipant(scope, actorUid, deps);
}

/**
 * Leer: el dueño siempre puede. Un tercero solo si el contenido es público o
 * si sigue a una cuenta privada. Réplica de canReadOwnedContent() de
 * firestore.rules.
 */
async function assertCanRead(remoteKey, actorUid, deps) {
  const { HttpError } = deps;
  const { namespace, scope } = splitKey(remoteKey, HttpError);

  if (KEY_NAMESPACES[namespace] === "chat") {
    await assertChatParticipant(scope, actorUid, deps);
    return;
  }

  const ownerUid = scope;
  if (ownerUid === actorUid) return;
  if (namespace === "avatars") return; // Las fotos de perfil son públicas.

  const owner = await deps.getDocument(`users/${ownerUid}`);
  if (!owner) throw new HttpError(404, "Owner not found");

  // Bloqueos en cualquiera de los dos sentidos.
  const [blockedByOwner, blockedByActor] = await Promise.all([
    deps.getDocument(`users/${ownerUid}/blockedUsers/${actorUid}`),
    deps.getDocument(`users/${actorUid}/blockedUsers/${ownerUid}`)
  ]);
  if (blockedByOwner || blockedByActor) throw new HttpError(403, "Not allowed");

  if (owner.isPrivate === true) {
    const follower = await deps.getDocument(`users/${ownerUid}/followers/${actorUid}`);
    if (!follower) throw new HttpError(403, "Private account");
  }
}

async function assertChatParticipant(chatId, actorUid, deps) {
  const chat = await deps.getDocument(`chats/${chatId}`);
  if (!chat) throw new deps.HttpError(404, "Chat not found");
  const participants = Array.isArray(chat.participants) ? chat.participants : [];
  if (!participants.includes(actorUid)) {
    throw new deps.HttpError(403, "Not a participant of this chat");
  }
}

// -------------------------------------------------------------------------
// Cuotas y límites
// -------------------------------------------------------------------------

/** Límite de tamaño para un contentType dado (bytes). */
export function sizeLimitFor(contentType) {
  return SIZE_LIMITS_BYTES[contentType] || DEFAULT_SIZE_LIMIT_BYTES;
}

/** Cuota de un usuario en un namespace (bytes). UPLOAD_QUOTA_BYTES la globaliza. */
export function quotaBytesFor(namespace, env = {}) {
  const override = Number(env.UPLOAD_QUOTA_BYTES);
  if (Number.isFinite(override) && override > 0) return override;
  return NAMESPACE_QUOTA_BYTES[namespace] || DEFAULT_QUOTA_BYTES;
}

/** Techo absoluto por archivo (bytes). UPLOAD_MAX_BYTES lo sobreescribe. */
export function absoluteMaxBytes(env = {}) {
  const override = Number(env.UPLOAD_MAX_BYTES);
  if (Number.isFinite(override) && override > 0) return override;
  return ABSOLUTE_MAX_BYTES;
}

function usageDocPath(uid, namespace) {
  return `_storageUsage/${uid}/namespaces/${namespace}`;
}

/** El namespace es la primera parte de la key, ya validada por normalizeKey. */
function keyNamespace(remoteKey, HttpError) {
  return splitKey(remoteKey, HttpError).namespace;
}

/**
 * Comprueba la cuota ANTES de entregar el ticket: uso actual + tamaño nuevo
 * no puede superar la cuota del namespace. El ledger vive en Firestore
 * (_storageUsage) y lo mantienen complete/delete.
 */
async function assertQuotaAvailable(uid, namespace, sizeBytes, deps, env) {
  const usage = await deps.getDocument(usageDocPath(uid, namespace));
  const used = Number(usage?.bytes || 0);
  const quota = quotaBytesFor(namespace, env);
  if (used + sizeBytes > quota) {
    throw new deps.HttpError(403, `Upload quota exceeded (${namespace})`);
  }
}

// -------------------------------------------------------------------------
// Validación de claves
// -------------------------------------------------------------------------

/**
 * Acepta rutas de la forma `<namespace>/<scope>/<nombre>` y rechaza cualquier
 * intento de escapar del prefijo (`..`, `//`, rutas absolutas, control chars).
 */
function normalizeKey(value, HttpError) {
  if (typeof value !== "string") throw new HttpError(400, "Invalid key");
  const key = value.trim();

  if (!key || key.length > 512) throw new HttpError(400, "Invalid key");
  if (key.startsWith("/") || key.includes("//")) throw new HttpError(400, "Invalid key");
  if (key.split("/").some((part) => part === "." || part === "..")) {
    throw new HttpError(400, "Invalid key");
  }
  // Conjunto deliberadamente estrecho: letras, dígitos, / _ - . y nada más.
  if (!/^[A-Za-z0-9/_.-]+$/.test(key)) throw new HttpError(400, "Invalid key");

  return key;
}

function splitKey(remoteKey, HttpError) {
  const parts = remoteKey.split("/");
  if (parts.length < 3) throw new HttpError(400, "Key must be <namespace>/<scope>/<file>");

  const [namespace, scope] = parts;
  if (!Object.prototype.hasOwnProperty.call(KEY_NAMESPACES, namespace)) {
    throw new HttpError(400, `Unsupported key namespace: ${namespace}`);
  }
  if (!/^[A-Za-z0-9_-]+$/.test(scope)) throw new HttpError(400, "Invalid key scope");

  return { namespace, scope };
}

function keyPrefix(remoteKey) {
  const [namespace, scope] = remoteKey.split("/");
  return `${namespace}/${scope}/`;
}

function normalizeContentType(value, HttpError) {
  const contentType = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (!ALLOWED_CONTENT_TYPES.has(contentType)) {
    throw new HttpError(400, `Unsupported contentType: ${contentType || "(missing)"}`);
  }
  return contentType;
}

/** Tamaño declarado por la app: entero positivo (bytes). */
export function normalizeSize(value, HttpError) {
  const size = Number(value);
  if (!Number.isInteger(size) || size <= 0) throw new HttpError(400, "Invalid sizeBytes");
  return size;
}

/** Identificador de subida para la idempotencia de /storage/complete. */
export function normalizeUploadId(value, HttpError) {
  const id = typeof value === "string" ? value.trim() : "";
  if (!id || id.length > 128) throw new HttpError(400, "Invalid uploadId");
  if (!/^[A-Za-z0-9:_-]+$/.test(id)) throw new HttpError(400, "Invalid uploadId");
  return id;
}

function normalizeTtl(value, HttpError) {
  if (value == null) return DEFAULT_SIGNED_TTL_SEC;
  const ttl = Number(value);
  if (!Number.isFinite(ttl) || ttl < 1) throw new HttpError(400, "Invalid ttlSec");
  return Math.min(Math.floor(ttl), MAX_SIGNED_TTL_SEC);
}

/** B2 espera el nombre percent-encoded pero conservando las barras. */
function encodeKeyForUrl(key) {
  return key.split("/").map(encodeURIComponent).join("/");
}

// -------------------------------------------------------------------------
// Cliente B2
// -------------------------------------------------------------------------

async function getB2Session(env, HttpError) {
  if (b2SessionCache.session && b2SessionCache.expiresAt > Date.now()) {
    return b2SessionCache.session;
  }
  const keyId = requireEnv(env, "B2_KEY_ID", HttpError);
  const applicationKey = requireEnv(env, "B2_APPLICATION_KEY", HttpError);

  const session = await b2Authorize(keyId, applicationKey, HttpError);
  // B2 caduca a las 24 h; se renueva bastante antes por seguridad.
  b2SessionCache = { session, expiresAt: Date.now() + 12 * 60 * 60 * 1000 };
  return session;
}

async function b2Authorize(keyId, applicationKey, HttpError) {
  const response = await fetch(B2_AUTHORIZE_URL, {
    headers: { Authorization: `Basic ${btoa(`${keyId}:${applicationKey}`)}` }
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new HttpError(
      response.status === 401 ? 500 : 503,
      `b2_authorize_account failed (${response.status}): ${detail.slice(0, 200)}`
    );
  }
  const result = await response.json();
  const storageApi = result.apiInfo?.storageApi;
  const apiUrl = storageApi?.apiUrl || result.apiUrl;
  const downloadUrl = storageApi?.downloadUrl || result.downloadUrl;

  if (!apiUrl || !downloadUrl || !result.authorizationToken) {
    throw new HttpError(503, "Unexpected b2_authorize_account response");
  }
  return { apiUrl, downloadUrl, authToken: result.authorizationToken };
}

async function b2Post(session, operation, payload, HttpError) {
  const response = await fetch(`${session.apiUrl}/b2api/${B2_API_VERSION}/${operation}`, {
    method: "POST",
    headers: {
      Authorization: session.authToken,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const detail = await response.text();
    // Un token vencido en caché no debe envenenar las siguientes peticiones.
    if (response.status === 401) b2SessionCache = { session: null, expiresAt: 0 };
    throw new HttpError(
      response.status >= 500 || response.status === 401 ? 503 : response.status,
      `${operation} failed (${response.status}): ${detail.slice(0, 200)}`
    );
  }
  return response.json();
}

function requireEnv(env, name, HttpError) {
  const value = env[name];
  if (!value) throw new HttpError(500, `${name} is not configured in the Worker`);
  return value;
}
