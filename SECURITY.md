# Vivid — Seguridad y privacidad

Este documento resume los cambios de seguridad aplicados y, sobre todo, **lo
que hay que hacer después de mergear el PR** para que el pipeline siga
funcionando.

---

## 1. `google-services.json` ya no está versionado

**Qué cambió:** el archivo `vivid-app/app/google-services.json` salió del
repositorio (`.gitignore` + `git rm`) y ahora se inyecta en CI desde el
secret de GitHub `GOOGLE_SERVICES_JSON_BASE64`. Un repo público ya no
expone la API key ni los client IDs de la app.

**Qué hacer (IMPORTANTE, antes de mergear o justo después):**

1. En tu máquina, genera el base64 del JSON real:
   ```bash
   base64 -w0 vivid-app/app/google-services.json
   ```
   (si ya lo borraste, descárgalo otra vez de Firebase Console →
   Project settings → Your apps → app de Android → *google-services.json*).

2. GitHub → Settings → Secrets and variables → Actions → **New repository
   secret**:
   - Name: `GOOGLE_SERVICES_JSON_BASE64`
   - Value: el base64 del paso 1 (una sola línea, sin saltos).

3. En local, para compilar necesitarás el archivo de nuevo:
   ```bash
   echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 --decode > vivid-app/app/google-services.json
   ```
   (o descárgalo de Firebase Console). Está en `.gitignore`, así que no se
   volverá a commitear.

> Nota: los PRs de forks no tienen acceso a secrets; el workflow de build
> (`build.yml`) escribe un **placeholder** en ese caso para que el APK debug
> compile igualmente (Firebase no funcionará en runtime con el placeholder).
> El workflow de release (`build-apk.yml`) **falla** si falta el secret a
> propósito: un APK de producción no debe llevar credenciales placeholder.

## 2. Backups de Android: la caché ya no se sube a la nube

`android:allowBackup="true"` se mantiene (para no romper la migración de
preferencias), pero las reglas ahora **excluyen** lo sensible:

- `backup_rules.xml` (Android ≤ 11) y `data_extraction_rules.xml`
  (Android 12+): solo se respalda `sharedpref` (ajustes de tema,
  notificaciones, etc.).
- **Excluidos siempre:** la base de datos Room (`vivid_database`, que cachea
  chats/mensajes) y la caché de media. La base local es una caché de
  Firestore: al iniciar sesión en un dispositivo nuevo se vuelve a poblar.
- No hay DataStore en uso (las preferencias usan SharedPreferences), pero si
  algún día se añade, habrá que excluirla también.

## 3. Tráfico HTTPS obligatorio + cert pinning opcional

- Nueva `network_security_config.xml` (conectada en el manifest):
  `cleartextTrafficPermitted="false"` → **todo** el tráfico (Firebase, B2,
  Worker, media) debe ser HTTPS o el sistema lo rechaza.
- **Cert pinning (defensa en profundidad, OPCIONAL):** el dominio del Worker
  es dinámico y sus certificados rotan, por eso NO se hardcodea ningún pin.
  Para activarlo:
  1. Genera los pins del host del Worker (o de tu dominio propio, p. ej.
     `media.tudominio.com` si usas `MEDIA_BASE_URL`):
     ```bash
     echo | openssl s_client -servername vivid-push.<cuenta>.workers.dev \
       -connect vivid-push.<cuenta>.workers.dev:443 2>/dev/null \
       | openssl x509 -pubkey -noout \
       | openssl pkey -pubin -outform der \
       | openssl dgst -sha256 -binary \
       | openssl enc -base64
     ```
     Repítelo con el certificado de respaldo (los CA suelen tener 2) y guarda
     **los dos** pins.
  2. Pásalos al build:
     ```bash
     ./gradlew assembleRelease -PvividWorkerPin='sha256/PRIMARIO;sha256/RESPALDO'
     ```
     o define la variable de Actions `VIVID_WORKER_PIN` con el mismo formato.
  3. ⚠️ Si un pin deja de coincidir (rotación de certificados) **la app pierde
     conexión** hasta que se actualice el pin. Es recomendable solo con
     dominio propio y un plan de rotación (pins de respaldo).

## 4. `default_web_client_id` ya no está duplicado a mano

El string hardcodeado en `strings.xml` se eliminó. El plugin google-services
lo genera automáticamente desde el `oauth_client` (client_type 3) del
`google-services.json` inyectado. `AuthScreen` lo lee por nombre con
`Resources.getIdentifier()` para no romper builds con placeholder.

## 5. Worker: límites de tamaño y cuotas por usuario

`cloudflare-worker/src/storage.js` ahora valida **antes de entregar el ticket
de B2** (`/storage/upload-url`) y **después de la subida** (`/storage/complete`,
nuevo endpoint, verifica el tamaño real contra B2):

- **Límite por tipo MIME:** imágenes ≤ 15 MB, GIF ≤ 20 MB, vídeo MP4 ≤ 300 MB,
  audio ≤ 30–50 MB, techo absoluto 512 MB.
- **Cuota por usuario y namespace** (ledger en Firestore `_storageUsage`):
  posts 1.5 GB, reels 3 GB, stories 1 GB, avatars 200 MB, chat_images 1 GB,
  chat_voice 500 MB, resto 2 GB. `delete` descuenta; `complete` es idempotente
  por `uploadId` (la app manda `sha1:tamaño`, así que reintentar no duplica).
- La app envía `sizeBytes` real (`File.length()`) y llama a `/storage/complete`
  tras subir; si el archivo excede los límites reales, lo borra.

Ajustes opcionales vía vars del Worker (wrangler.toml):
`UPLOAD_QUOTA_BYTES` (cuota global por usuario) y `UPLOAD_MAX_BYTES` (techo
absoluto por archivo).

> Límites conocidos: el Worker nunca ve los bytes (se suben directo a B2),
> así que la verificación de "magic bytes" del MIME real no es posible sin
> enrutar el tráfico por el Worker. La allowlist de contentType + límites +
> cuotas es la defensa práctica; para un backstop duro del bucket puedes
> añadir una regla de lifecycle en B2 (p. ej. borrar archivos > 512 MB).

## 6. Tests de reglas de Firestore en CI

- `firestore-tests/rules.test.js`: ~25 tests contra el **emulador** de
  Firestore (privacidad de cuentas privadas, contadores de a 1, participantes
  de chats, emisor de mensajes, etc.).
- Se ejecutan en:
  - `.github/workflows/firestore-rules-tests.yml` (cada PR y push a main que
    toque `firestore.rules`).
  - `deploy-functions.yml` → el job de deploy **espera** a que los tests
    pasen antes de publicar las reglas.
- En local:
  ```bash
  cd firestore-tests
  npm ci
  npx firebase emulators:exec --only firestore --project demo-vivid-rules "npm test"
  ```
  (requiere Java 11+ para el emulador).

## Tests del Worker en CI

`cloudflare-worker/test/storage.test.js` (17 tests con Node puro, sin red):
validación de tamaño/uploadId, límites, cuotas y las rutas
upload-url/complete/delete con fetch mockeado. Se ejecuta en
`deploy-cloudflare-worker.yml` (job `test`, en PRs y antes de desplegar).
