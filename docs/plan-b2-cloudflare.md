# Plan: mover las claves de Backblaze B2 al Cloudflare Worker

Fecha: 2026-08-16 · Complementa `docs/revision-mejoras.md` (punto crítico 1.1)

## Resumen

**Sí, se puede, y es la mejor opción disponible.** El Worker gratuito puede actuar
como intermediario ("broker") entre la app y Backblaze B2: la app nunca ve las claves,
solo pide permisos temporales al Worker, que sí las tiene guardadas como secreto cifrado
en Cloudflare.

Esto es mejor que la ruta de Cloud Functions que estaba planteada en el repo por una
razón práctica: **Cloud Functions exige el plan Blaze de Firebase (tarjeta de crédito)**.
El Worker no: el plan gratuito de Cloudflare basta y sobra para este caso.

Y hay una ventaja extra que no esperabas: Backblaze y Cloudflare son socios de la
**Bandwidth Alliance**, así que el tráfico de salida de B2 servido a través de Cloudflare
es **gratis, sin tope**. Hoy, con la app descargando directo desde
`f004.backblazeb2.com`, ese egress te cuenta ($0.01/GB después de la cuota gratuita).

---

## 1. La regla de oro del diseño: el Worker NO toca los bytes

Es tentador hacer que la app suba el video al Worker y el Worker lo reenvíe a B2.
**No lo hagas.** Con eso chocas contra dos límites:

- Cuerpo de petición máximo en plan gratuito/Pro: **100 MB**.
- Cada reel subido consume tiempo de CPU y ancho de banda del Worker.

El patrón correcto es **presigned / brokered upload**:

```
  App                        Worker (tiene las claves)          Backblaze B2
   │                                  │                              │
   ├─ POST /storage/upload-url ──────►│                              │
   │   Authorization: Bearer <idToken>│                              │
   │                                  ├─ b2_authorize_account ──────►│
   │                                  ├─ b2_get_upload_url ─────────►│
   │◄─ {remoteKey, uploadUrl, token} ─┤                              │
   │                                                                 │
   ├─ POST del binario (el archivo va DIRECTO a B2) ────────────────►│
   │                                                                 │
   ├─ POST /storage/sign ────────────►│                              │
   │                                  ├─ b2_get_download_auth ──────►│
   │◄─ {signedUrl} ───────────────────┤                              │
```

El archivo viaja del teléfono a B2 sin pasar por Cloudflare. El Worker solo mueve
unos cientos de bytes de JSON.

---

## 2. Cabe de sobra en el plan gratuito

| Límite (Workers Free) | Valor | Tu uso |
|---|---|---|
| Peticiones/día | 100,000 | 2–3 por publicación + 1 por refresco de URL |
| CPU por petición | 10 ms | Firmar/validar JWT ≈ 1–3 ms |
| Subrequests por invocación | 50 | 2–3 (`authorize` + `get_upload_url`) |
| Tamaño del Worker | 3 MB | Actual: unos pocos KB |
| Cuerpo de petición | 100 MB | **No aplica: los bytes no pasan por aquí** |

Fuente: [límites oficiales de Workers](https://developers.cloudflare.com/workers/platform/limits/).
Con 100k req/día tendrías margen para ~30,000 publicaciones diarias.

Nota sobre CPU: el límite de 10 ms es **CPU real**, no tiempo de espera de red. Las
llamadas a la API de B2 pueden tardar 300 ms y no cuentan.

---

## 3. Ya tienes hecho el 70 % del trabajo

Esto es lo bueno: `cloudflare-worker/src/index.js` **ya sabe autenticar usuarios de
Firebase**. La función `authenticateFirebaseUser()` (línea 226) verifica la firma RS256
del ID token contra el JWKS de Google, valida `aud`, `iss`, `exp`, `iat` y devuelve el
`uid`. Es exactamente la pieza que hace falta y está bien implementada.

También hay ya un `getDocument()` para leer Firestore con la service account, útil para
comprobar permisos.

Y del lado Android, `StorageProvider` ya es una interfaz con tres métodos
(`uploadFile`, `deleteFile`, `signDownloadUrl`) y `StorageModule` es el único punto donde
se elige la implementación. La abstracción que dejaste preparada sirve justo para esto.

Además, `cloud-function/index.js` ya tiene la lógica de `uploadReel` / `signDownload` /
`deleteFile` escrita en JS: es portar, no inventar.

---

## 4. Endpoints a añadir al Worker

### `POST /storage/upload-url`

```jsonc
// Petición (Authorization: Bearer <firebase id token>)
{ "kind": "reel", "contentType": "video/mp4" }

// Respuesta
{
  "remoteKey": "reels/AbC123uid/9f2e...-1755300000.mp4",
  "uploadUrl": "https://podXXX.backblaze.com/b2api/v4/b2_upload_file/...",
  "uploadAuthToken": "4_00...",
  "expiresAt": 1755303600
}
```

Puntos clave de seguridad:

1. **El `remoteKey` lo genera el Worker, no la app.** Se construye como
   `${kind}s/${uid}/${uuid}.${ext}` con el `uid` sacado del token verificado. Así un
   usuario no puede sobrescribir archivos de otro pasando
   `remoteKey = "reels/victima/algo.mp4"`.
2. **Lista blanca de `contentType`** (`video/mp4`, `image/jpeg`, `image/webp`,
   `audio/mp4`…). Nada de `application/octet-stream` genérico.
3. Validar `kind` contra un conjunto cerrado: `post`, `reel`, `story`, `avatar`, `chat`.

⚠️ **Riesgo residual honesto:** el `uploadAuthToken` que devuelve
`b2_get_upload_url` autoriza subir a *cualquier* ruta del bucket mientras esté vigente,
no solo a la que tú calculaste. Un usuario malicioso podría capturar su propio token y
subir a otro prefijo. Dos formas de cerrarlo:

- **Simple:** aceptarlo por ahora (el atacante necesita esfuerzo deliberado, y sigue
  siendo infinitamente mejor que la clave maestra en el APK).
- **Completo:** que el Worker cree con `b2_create_key` una application key restringida
  a `bucketId` + `namePrefix = "reels/{uid}/"` con `validDurationInSeconds: 3600`, y
  autorice con ella. Son 2 llamadas extra, cabe de sobra en el presupuesto de CPU.
  Recomendado para la versión final.

### `POST /storage/sign`

```jsonc
{ "key": "reels/AbC123uid/9f2e....mp4", "ttlSec": 604800 }
→ { "signedUrl": "https://f004.backblazeb2.com/file/VividGrem/...?Authorization=...", "expiresAt": ... }
```

Aquí el Worker debe comprobar que el usuario **tiene derecho a ver** ese archivo, no solo
que esté autenticado: leer el documento de Firestore correspondiente y aplicar tus reglas
de privacidad (cuenta privada, bloqueos, etc.). `firestore.rules` ya modela todo eso;
la lógica se puede replicar con `getDocument()`.

Ojo con el TTL: `b2_get_download_authorization` admite máximo **7 días** (604800 s),
que es lo que ya usas en `MAX_SIGNED_TTL_SEC`.

### `POST /storage/delete`

Solo si el `key` empieza por `{kind}s/{uid}/` **o** el usuario es el dueño del documento
en Firestore. Reutiliza `b2_list_file_names` + `b2_delete_file_version` de la clase Kotlin.

### Caché del token de cuenta

`b2_authorize_account` devuelve un token válido 24 h. El Worker debe cachearlo:

- En memoria global (como ya haces con `accessTokenCache` y `jwksCache`) — gratis, pero
  se pierde cuando el isolate se recicla.
- Mejor: en **Workers KV** (1,000 escrituras/día gratis, de sobra para 1 escritura cada
  24 h). Así evitas re-autorizar en cada isolate frío.

---

## 5. Cambios en la app Android

1. **Nueva clase** `WorkerStorageProvider : StorageProvider` en
   `data/storage/`. Es casi un copy-paste de `CloudFunctionsStorageProvider`, pero:
   - Añadiendo el header `Authorization: Bearer ${FirebaseAuth.getInstance()
     .currentUser?.getIdToken(false)?.await()?.token}` a cada llamada al Worker.
   - Apuntando a `BuildConfig.PUSH_WORKER_URL` (ya existe y ya se inyecta desde Actions).
     Conviene renombrarla a `VIVID_WORKER_URL` porque deja de ser solo de push.
2. **`StorageModule`** pasa a construir `WorkerStorageProvider`.
3. **Borrar `BuildConfigSecrets.kt`** y `BackblazeStorageProvider` (o dejar esta última
   solo si quieres un modo debug).
4. La app deja de conocer `bucketId`, `bucketName`, `keyId` y `applicationKey`.

Como `uploadFile()` ya devuelve la URL firmada y el resto de la app trabaja contra la
interfaz, **ni los ViewModels ni la UI se tocan**.

---

## 6. Bonus: egress gratis y caché (fase 2)

Para aprovechar la Bandwidth Alliance de verdad hace falta que el tráfico de descarga
pase por Cloudflare, no directo a `backblazeb2.com`. Requiere un dominio propio (~$10/año)
en tu cuenta de Cloudflare:

1. CNAME `media.tudominio.com` → `f004.backblazeb2.com`, con el proxy naranja activado.
2. Transform Rule que reescriba `/file/VividGrem/...` correctamente.
3. Servir las URLs firmadas con ese host.

Beneficios: egress $0, TLS y CDN global, y las descargas repetidas se sirven desde el
edge (mucho más rápido para tus usuarios en México que ir a un datacenter de B2 en EE. UU.).

⚠️ Detalle importante: las URLs firmadas llevan un `?Authorization=<token>` distinto cada
vez, así que **rompen la caché del CDN** (cada URL es una entrada nueva). Si llegas a
tener tráfico real, la solución es que el Worker sirva `/media/<key>` con una URL estable,
valide el permiso y haga `fetch` a B2 con `cf: { cacheEverything: true }`. Ahí sí los
bytes pasan por el Worker, pero se cachean en el edge y consumes una petición por MISS.
Es optimización prematura hoy; anótalo para cuando haga falta.

---

## 7. Orden de ejecución

| # | Paso | Dónde |
|---|---|---|
| 1 | **Rotar la application key** de B2 (la actual está quemada) | Panel de Backblaze |
| 2 | Crear la key nueva restringida al bucket `VividGrem` | Panel de Backblaze |
| 3 | `npx wrangler secret put B2_KEY_ID` / `B2_APPLICATION_KEY` | Terminal |
| 4 | Añadir `B2_BUCKET_ID` y `B2_BUCKET_NAME` a `[vars]` de `wrangler.toml` (no son secretos) | Repo |
| 5 | Implementar los 3 endpoints en el Worker | Repo |
| 6 | `WorkerStorageProvider` + cambiar `StorageModule` | Repo |
| 7 | Borrar `BuildConfigSecrets.kt` y arreglar el `.gitignore` | Repo |
| 8 | Limpiar el historial de Git (`git filter-repo`) | Local |

Los pasos 1–3 los tienes que hacer tú desde los paneles; del 4 al 7 los puedo
implementar yo.

---

## 8. Comparativa de las tres rutas

| | Claves en el APK (hoy) | Cloud Functions | **Worker (propuesta)** |
|---|---|---|---|
| Coste | $0 | Requiere plan Blaze (tarjeta) | **$0** |
| Claves expuestas | ❌ Sí, en el APK y en Git | ✅ No | ✅ **No** |
| Revocar acceso a un usuario | Imposible sin republicar | ✅ | ✅ |
| Egress B2 | De pago | De pago | **Gratis vía Bandwidth Alliance** |
| Auth de Firebase | — | Integrada | **Ya implementada en tu Worker** |
| Latencia añadida | 0 | ~200–800 ms (arranque en frío) | **~10–50 ms (edge)** |
| Trabajo pendiente | — | Desplegar función + tarjeta | **~250 líneas de JS + 1 clase Kotlin** |
