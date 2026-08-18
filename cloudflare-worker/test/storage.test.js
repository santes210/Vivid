/**
 * Tests del broker de almacenamiento (storage.js).
 *
 * Se ejecutan con Node (sin el runtime de Cloudflare):
 *   npm test
 *
 * Cubren:
 *   - Validación pura (sizeBytes, uploadId, límites, cuotas, techo absoluto).
 *   - Rutas /storage/upload-url, /storage/complete y /storage/delete con
 *     dependencias simuladas y fetch mockeado (nada toca la red real).
 */
import { test, describe, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";

import {
  handleStorageRoute,
  normalizeSize,
  normalizeUploadId,
  sizeLimitFor,
  quotaBytesFor,
  absoluteMaxBytes
} from "../src/storage.js";

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const jsonResp = (obj) =>
  new Response(JSON.stringify(obj), {
    status: 200,
    headers: { "Content-Type": "application/json" }
  });

/** Dependencias simuladas con valores por defecto "felices". */
function deps(overrides = {}) {
  return {
    env: {
      B2_BUCKET_ID: "bucket-1",
      B2_KEY_ID: "key-id",
      B2_APPLICATION_KEY: "app-key",
      B2_RESTRICT_UPLOAD_KEYS: "false"
    },
    json: (body, status = 200) => ({ status, body }),
    HttpError,
    readJson: async () => ({
      key: "posts/uidA/photo.jpg",
      contentType: "image/jpeg",
      sizeBytes: 1000
    }),
    authenticate: async () => "uidA",
    getDocument: async () => null,
    createIfAbsent: async () => true,
    writeDocument: async () => {},
    deleteDocument: async () => {},
    ...overrides
  };
}

const post = (body) => ({ method: "POST", ...(body ? { body } : {}) });

/**
 * Llama a la ruta igual que lo hace index.js: handleStorageRoute lanza
 * HttpError y el fetch handler del Worker lo convierte en una respuesta
 * JSON con su status. Este wrapper replica ese contrato.
 */
async function callStorageRoute(request, pathname, deps) {
  try {
    return await handleStorageRoute(request, pathname, deps);
  } catch (error) {
    const status = error instanceof HttpError ? error.status : 500;
    return { status, body: { error: status >= 500 ? "Temporary storage error" : error.message } };
  }
}

// ---------------------------------------------------------------------------
// Validación pura
// ---------------------------------------------------------------------------

describe("normalizeSize", () => {
  test("acepta enteros positivos", () => {
    assert.equal(normalizeSize(1024, HttpError), 1024);
    assert.equal(normalizeSize("2048", HttpError), 2048);
  });

  test("rechaza 0, negativos, flotantes, strings vacíos y ausentes", () => {
    assert.throws(() => normalizeSize(0, HttpError), /Invalid sizeBytes/);
    assert.throws(() => normalizeSize(-5, HttpError), /Invalid sizeBytes/);
    assert.throws(() => normalizeSize(1.5, HttpError), /Invalid sizeBytes/);
    assert.throws(() => normalizeSize("", HttpError), /Invalid sizeBytes/);
    assert.throws(() => normalizeSize(undefined, HttpError), /Invalid sizeBytes/);
  });
});

describe("normalizeUploadId", () => {
  test("acepta el formato de la app (sha1:tamaño)", () => {
    assert.equal(normalizeUploadId("a".repeat(40) + ":1024", HttpError), "a".repeat(40) + ":1024");
  });

  test("rechaza vacío, demasiado largo y caracteres raros", () => {
    assert.throws(() => normalizeUploadId("", HttpError), /Invalid uploadId/);
    assert.throws(() => normalizeUploadId("x".repeat(129), HttpError), /Invalid uploadId/);
    assert.throws(() => normalizeUploadId("../etc/passwd", HttpError), /Invalid uploadId/);
    assert.throws(() => normalizeUploadId("id with spaces", HttpError), /Invalid uploadId/);
  });
});

describe("límites y cuotas", () => {
  test("sizeLimitFor: límite por tipo y default para desconocidos", () => {
    assert.equal(sizeLimitFor("image/jpeg"), 15 * 1024 * 1024);
    assert.equal(sizeLimitFor("video/mp4"), 300 * 1024 * 1024);
    assert.equal(sizeLimitFor("application/octet-stream"), 25 * 1024 * 1024);
  });

  test("quotaBytesFor: por namespace, default y override por env", () => {
    assert.equal(quotaBytesFor("avatars", {}), 200 * 1024 * 1024);
    assert.equal(quotaBytesFor("reels", {}), 3 * 1024 * 1024 * 1024);
    assert.equal(quotaBytesFor("unknown", {}), 2 * 1024 * 1024 * 1024);
    assert.equal(quotaBytesFor("posts", { UPLOAD_QUOTA_BYTES: "999" }), 999);
  });

  test("absoluteMaxBytes: default y override por env", () => {
    assert.equal(absoluteMaxBytes({}), 512 * 1024 * 1024);
    assert.equal(absoluteMaxBytes({ UPLOAD_MAX_BYTES: "1024" }), 1024);
  });
});

// ---------------------------------------------------------------------------
// POST /storage/upload-url
// ---------------------------------------------------------------------------

describe("POST /storage/upload-url", () => {
  test("rechaza sin sizeBytes (400)", async () => {
    const d = deps({
      readJson: async () => ({ key: "posts/uidA/x.jpg", contentType: "image/jpeg" })
    });
    const res = await callStorageRoute(post(), "/storage/upload-url", d);
    assert.equal(res.status, 400);
  });

  test("rechaza un archivo que excede el límite por tipo (413)", async () => {
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/x.jpg",
        contentType: "image/jpeg",
        sizeBytes: 16 * 1024 * 1024 // image/jpeg máx = 15 MB
      })
    });
    const res = await callStorageRoute(post(), "/storage/upload-url", d);
    assert.equal(res.status, 413);
  });

  test("rechaza un archivo que excede el techo absoluto (413)", async () => {
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/x.mp4",
        contentType: "video/mp4",
        sizeBytes: 600 * 1024 * 1024 // techo absoluto = 512 MB
      })
    });
    const res = await callStorageRoute(post(), "/storage/upload-url", d);
    assert.equal(res.status, 413);
  });

  test("rechaza cuando la cuota del namespace se excedería (403)", async () => {
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/x.mp4",
        contentType: "video/mp4",
        sizeBytes: 100 * 1024 * 1024
      }),
      // posts: 1.5 GB de cuota; ya hay 1.45 GB → 1.45 GB + 100 MB > 1.5 GB
      getDocument: async (path) =>
        path === "_storageUsage/uidA/namespaces/posts"
          ? { bytes: 1.45 * 1024 * 1024 * 1024, files: 3 }
          : null
    });
    const res = await callStorageRoute(post(), "/storage/upload-url", d);
    assert.equal(res.status, 403);
    assert.match(res.body.error, /quota/i);
  });

  test("feliz: entrega uploadUrl + token cuando todo es válido", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      const u = String(url);
      if (u.includes("b2_authorize_account")) {
        return jsonResp({ apiUrl: "https://api.example", downloadUrl: "https://dl.example", authorizationToken: "ses" });
      }
      if (u.includes("b2_get_upload_url")) {
        return jsonResp({ uploadUrl: "https://upload.example", authorizationToken: "up" });
      }
      throw new Error(`fetch inesperado: ${u}`);
    };
    try {
      const res = await callStorageRoute(post(), "/storage/upload-url", deps());
      assert.equal(res.status, 200);
      assert.equal(res.body.uploadUrl, "https://upload.example");
      assert.equal(res.body.contentType, "image/jpeg");
      assert.equal(res.body.restricted, false);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

// ---------------------------------------------------------------------------
// POST /storage/complete
// ---------------------------------------------------------------------------

describe("POST /storage/complete", () => {
  const b2Fetch = () => {
    globalThis.fetch = async (url) => {
      const u = String(url);
      if (u.includes("b2_authorize_account")) {
        return jsonResp({ apiUrl: "https://api.example", downloadUrl: "https://dl.example", authorizationToken: "ses" });
      }
      if (u.includes("b2_list_file_names")) {
        return jsonResp({ files: [{ fileName: "posts/uidA/photo.jpg", contentLength: 1000 }] });
      }
      throw new Error(`fetch inesperado: ${u}`);
    };
  };

  test("feliz: verifica tamaño real en B2 y registra la cuota", async () => {
    const originalFetch = globalThis.fetch;
    b2Fetch();
    let written = null;
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/photo.jpg",
        uploadId: "abc123:1000",
        sizeBytes: 1000,
        contentType: "image/jpeg"
      }),
      getDocument: async (path) =>
        path === "_storageUsage/uidA/namespaces/posts" ? { bytes: 4000, files: 2 } : null,
      writeDocument: async (path, fields) => { written = fields; }
    });
    try {
      const res = await callStorageRoute(post(), "/storage/complete", d);
      assert.equal(res.status, 200);
      assert.equal(res.body.ok, true);
      assert.equal(res.body.duplicate, false);
      assert.equal(res.body.sizeBytes, 1000);
      assert.equal(res.body.usageBytes, 5000);
      assert.equal(written.bytes, 5000);
      assert.equal(written.files, 3);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("idempotente: un uploadId repetido no duplica la cuota", async () => {
    const originalFetch = globalThis.fetch;
    b2Fetch();
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/photo.jpg",
        uploadId: "abc123:1000",
        sizeBytes: 1000,
        contentType: "image/jpeg"
      }),
      createIfAbsent: async () => false // ya contabilizado (retry)
    });
    try {
      const res = await callStorageRoute(post(), "/storage/complete", d);
      assert.equal(res.status, 200);
      assert.equal(res.body.duplicate, true);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("rechaza cuando el tamaño REAL en B2 supera el límite del tipo (413)", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      const u = String(url);
      if (u.includes("b2_authorize_account")) {
        return jsonResp({ apiUrl: "https://api.example", downloadUrl: "https://dl.example", authorizationToken: "ses" });
      }
      if (u.includes("b2_list_file_names")) {
        return jsonResp({ files: [{ fileName: "posts/uidA/photo.jpg", contentLength: 16 * 1024 * 1024 }] });
      }
      throw new Error(`fetch inesperado: ${u}`);
    };
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/photo.jpg",
        uploadId: "abc:1",
        sizeBytes: 1000, // el cliente declaró poco...
        contentType: "image/jpeg"
      })
    });
    try {
      const res = await callStorageRoute(post(), "/storage/complete", d);
      assert.equal(res.status, 413);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  test("rechaza cuando la cuota se excede con el tamaño real (403)", async () => {
    const originalFetch = globalThis.fetch;
    b2Fetch();
    const d = deps({
      readJson: async () => ({
        key: "posts/uidA/photo.jpg",
        uploadId: "abc:1000",
        sizeBytes: 1000,
        contentType: "image/jpeg"
      }),
      // Cuota posts = 1.5 GB; ya está al límite → +1000 bytes la excede.
      getDocument: async (path) =>
        path === "_storageUsage/uidA/namespaces/posts"
          ? { bytes: 1.5 * 1024 * 1024 * 1024, files: 10 }
          : null
    });
    try {
      const res = await callStorageRoute(post(), "/storage/complete", d);
      assert.equal(res.status, 403);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

// ---------------------------------------------------------------------------
// POST /storage/delete
// ---------------------------------------------------------------------------

describe("POST /storage/delete", () => {
  test("borra en B2 y descuenta la cuota por el tamaño real", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url) => {
      const u = String(url);
      if (u.includes("b2_authorize_account")) {
        return jsonResp({ apiUrl: "https://api.example", downloadUrl: "https://dl.example", authorizationToken: "ses" });
      }
      if (u.includes("b2_list_file_names")) {
        return jsonResp({ files: [{ fileName: "posts/uidA/photo.jpg", fileId: "f1", contentLength: 1000 }] });
      }
      if (u.includes("b2_delete_file_version")) {
        return jsonResp({});
      }
      throw new Error(`fetch inesperado: ${u}`);
    };
    let written = null;
    const d = deps({
      readJson: async () => ({ key: "posts/uidA/photo.jpg" }),
      getDocument: async (path) =>
        path === "_storageUsage/uidA/namespaces/posts" ? { bytes: 5000, files: 3 } : null,
      writeDocument: async (path, fields) => { written = fields; }
    });
    try {
      const res = await callStorageRoute(post(), "/storage/delete", d);
      assert.equal(res.status, 200);
      assert.equal(res.body.deleted, true);
      assert.equal(written.bytes, 4000);
      assert.equal(written.files, 2);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
