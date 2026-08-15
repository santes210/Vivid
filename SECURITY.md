# Seguridad — Vivid

Guía operativa para el incidente de fuga de claves de Backblaze B2
(agosto 2026) y para mantener el proyecto sin secretos en el repo.

## Qué pasó

- Las claves de B2 estuvieron commitadas en
  `vivid-app/app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt`
  (commit `ea24dea` en adelante) en un repo **público**.
- El `.gitignore` tenía la ruta mal (`app/src/main/...` en vez de
  `vivid-app/app/src/main/...`), así que el archivo nunca quedó ignorado.
- El workflow `build.yml` subía el APK debug (con las claves dentro) como
  artefacto descargable en cada push.

Cualquiera de las dos vías es suficiente para dar las claves por
**comprometidas**: hay que rotarlas sí o sí.

## 1. Rotar las claves en Backblaze B2 (hacerlo ANTES de purgar)

1. Entrá a <https://secure.backblaze.com/app_keys.htm>.
2. **Create a Key** con:
   - Name: `vivid-app-2026` (o `vivid-cloud-function`).
   - Restringida al bucket `VividGrem`.
   - Permisos: `listFiles, readFiles, writeFiles, deleteFiles`
     (el modo directo usa authorize/upload/list/delete/download-authorization;
     la Cloud Function usa el mismo set).
3. Guardá `keyID` y `applicationKey` nuevos (solo se muestran una vez).
4. Distribuí las claves nuevas (ver sección 2 y 3).
5. **Recién después** de verificar que todo funciona, borrá la key vieja
   (`0044482642d8bb00000000005`) con el botón **Delete**.

Alternativa por CLI:

```bash
pip install b2
b2 authorize-account <masterKeyID> <masterApplicationKey>
b2 create-key --bucket VividGrem vivid-app-2026 listFiles,readFiles,writeFiles,deleteFiles
# ... al final:
b2 delete-key 0044482642d8bb00000000005
```

## 2. GitHub Actions secrets

Las claves ya no se commitear: `app/build.gradle.kts` las lee de variables
de entorno. En el repo: **Settings → Secrets and variables → Actions**, crear:

| Secret              | Valor                                       |
| ------------------- | ------------------------------------------- |
| `B2_KEY_ID`         | keyID nuevo de B2                           |
| `B2_APPLICATION_KEY`| applicationKey nuevo de B2                  |
| `B2_BUCKET_ID`      | `94c488b2a624f22d98eb0b10` (bucket ID)      |
| `B2_BUCKET_NAME`    | `VividGrem`                                 |

Para builds locales, usá `local.properties` (gitignored):

```properties
b2.keyId=...
b2.applicationKey=...
b2.bucketId=94c488b2a624f22d98eb0b10
b2.bucketName=VividGrem
```

o variables de entorno / `-Pb2KeyId=...`. Ver `BuildConfigSecrets.kt.example`.

## 3. Cloud Function (modo seguro)

La función de `/cloud-function` ahora **exige** `Authorization: Bearer <idToken>`
(Firebase Auth), valida que la key viva bajo `<tipo>/<uid>/...` del usuario y
tiene CORS restringido. Configurá las claves nuevas en Firebase y desplegá:

```bash
firebase functions:config:set \
  b2.key_id="..." \
  b2.application_key="..." \
  b2.bucket_id="94c488b2a624f22d98eb0b10" \
  b2.bucket_name="VividGrem" \
  cors.origins="https://TU-WEB.web.app"

cd cloud-function && firebase deploy --only functions --project verigram-c58a6
```

Después, migrá el APK a Cloud Function mode: en `StorageModule.kt` cambiá la
implementación a `CloudFunctionsStorageProvider(BuildConfig.CF_BASE_URL)`
(y definí `cf.baseUrl` en `local.properties` / `CF_BASE_URL` en CI). Una vez
migrado, las claves B2 ya no viajan en el APK y podés borrar los secrets de
GitHub Actions.

### App Check (opcional, recomendado)

1. En Firebase Console → App Check, registrá tu app Android (Play Integrity).
2. `firebase functions:config:set appcheck.enforce=true` y redesplegá.
3. Agregá el SDK de App Check al APK para que envíe el header
   `X-Firebase-AppCheck` en cada llamada.

Nota: con la validación por uid, `signDownload` solo firma contenido del
propio usuario. Para que el feed reproduzca contenido de OTROS usuarios en
modo CF, guardá la URL firmada (o la pública) en el documento de Firestore al
subir, y dejá que las reglas de Firestore controlen quién la lee.

## 4. Purgar el historial de git

Ya con las claves rotadas (la purga no sirve si la key sigue viva):

```bash
bash scripts/purge-secrets.sh \
  'K0043ske+MzlEoRWXQtmJ18opgnipXQ' \
  '0044482642d8bb00000000005' \
  '4482642d8bb0' \
  '94c488b2a624f22d98eb0b10' \
  --push
```

El script elimina `BuildConfigSecrets.kt` de todos los commits, scrubbea los
literales (archivos y mensajes de commit), verifica que no quede nada y hace
force-push de `main` y de los tags.

Después del force-push:

- Cerrá los PRs abiertos (sus refs pueden seguir exponiendo commits viejos).
- Avisá a los forks: el historial viejo sigue vivo en cada fork.
- Pedí a GitHub Support la purga de vistas cacheadas si querés el borrado
  completo: <https://support.github.com>.

## 5. Otras decisiones tomadas

- `.gitignore` corregido a `**/BuildConfigSecrets.kt` (verificado con
  `git check-ignore`).
- `BuildConfigSecrets.kt` fuera del repo; queda
  `BuildConfigSecrets.kt.example` como plantilla.
- `build.yml` ya no sube el APK debug como artefacto público.
- ⚠️ `build-apk.yml` **todavía** sube el APK release firmado como artefacto.
  Mientras el APK lleve claves B2 embebidas (modo directo), ese artefacto es
  otra vía de filtración: migrá a Cloud Function o quitá ese paso.
- `google-services.json` es config de cliente Firebase (público por diseño);
  igualmente restringí la API key con App Check para que no se abuse.
