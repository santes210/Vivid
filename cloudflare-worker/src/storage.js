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
 *   POST /storage/sign        → { signedUrl, expiresAt }
 *   POST /storage/delete      → { ok, deleted }
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
  await assertCanWrite(remoteKey, actorUid, deps);

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
