# Vivid — Revisión técnica y mejoras propuestas

Fecha: 2026-08-16 · Rama: `arena/01a00c4e-vivid`

Alcance revisado: `.github/workflows/*`, `vivid-app/` (Gradle + app Android),
`cloudflare-worker/`, `cloud-function/`, `firestore.rules`, `.gitignore` y el
contenido versionado del repositorio.

---

## 0. Resumen ejecutivo

| Prioridad | Tema | Impacto |
|---|---|---|
| 🔴 P0 | Claves de Backblaze B2 en texto plano y **versionadas** | Cualquiera puede leer/escribir/borrar tu bucket |
| 🔴 P0 | `.gitignore` no cubre la ruta real → el archivo de secretos sí está en Git | El ignore da falsa sensación de seguridad |
| 🟠 P1 | Basura versionada: `.sdkman/`, `.bashrc`, `.zshrc`, `.gradle/`, `.kotlin/`, `*.patch` | Repo sucio, cachés corruptas en CI |
| 🟠 P1 | Dos workflows compilan en cada push a `main` (debug + release) | ~2× minutos de Actions, colas |
| 🟠 P1 | Build serializado a propósito (`--no-daemon --max-workers=1`, `clean`, sin incremental) | Builds de 20–40 min evitables |
| 🟡 P2 | Cero tests, `abortOnError=false`, `checkReleaseBuilds=false` | CI en verde no significa nada |
| 🟡 P2 | Release sin R8 (`isMinifyEnabled = false`) | APK grande y sin ofuscar |
| 🟡 P2 | El APK solo existe 30 días como artefacto; no hay GitHub Release ni tags | No hay historial de versiones descargables |
| 🟡 P2 | `deploy-functions.yml` interpola el secreto dentro de comillas simples en `bash` | Fallo/inyección si el JSON contiene `'` |
| 🔵 P3 | Toolchain atrasada (AGP 8.7.3, Kotlin 2.0.21, media3 1.4.1), sin Dependabot | Deuda técnica creciente |

---

## 1. Seguridad (P0 — hacer antes que nada)

### 1.1 Credenciales de Backblaze B2 embebidas y en el historial de Git

`vivid-app/app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt` contiene:

```
B2_KEY_ID          = "0044482642d8bb00000000005"
B2_APPLICATION_KEY = "K0043ske+MzlEoRWXQtmJ18opgnipXQ"
B2_BUCKET_ID       = "94c488b2a624f22d98eb0b10"
```

El propio archivo documenta que fue una decisión consciente, pero el riesgo real es
mayor de lo que dice el comentario:

- No es solo "si alguien mira el repo": el **APK** lleva las claves dentro. Cualquiera
  que descargue el artefacto y ejecute `apktool`/`jadx` las obtiene en 2 minutos.
- Con esa `applicationKey` se puede **borrar todo el bucket** o subir contenido
  arbitrario y hacerte pagar el ancho de banda.
- Los bots que escanean GitHub encuentran claves nuevas en minutos, no en días.

**Acciones:**

1. **Rotar la clave en Backblaze ya** (crear application key nueva, revocar la actual).
   Rotar es obligatorio aunque borres el archivo: ya está en el historial de Git.
2. Migrar a modo servidor, que ya está medio hecho en el repo:
   `CloudFunctionsStorageProvider` + `/cloud-function` existen; `StorageModule` es
   quien elige el proveedor directo. Cambiar ahí y desplegar la función deja las
   claves solo en el servidor.
3. Mientras tanto, si necesitas seguir en modo directo, al menos **sacar los valores
   del código** e inyectarlos por `buildConfigField` desde secrets de Actions
   (`B2_KEY_ID`, `B2_APPLICATION_KEY`, …), igual que ya haces con
   `VIVID_PUSH_WORKER_URL`. Sigue siendo extraíble del APK, pero deja de estar en Git.
4. Limitar la application key en B2 a un solo bucket y, si es posible, solo a
   `writeFiles`/`readFiles` (no `deleteFiles`, no `listBuckets`).
5. Opcional pero recomendable: limpiar el historial con `git filter-repo` o
   `bfg` una vez rotadas las claves.

### 1.2 El `.gitignore` apunta a la ruta equivocada

```gitignore
app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt
```

La ruta real es `vivid-app/app/src/main/...`. Como el patrón tiene barras, Git lo
ancla a la raíz del repo → **no coincide y el archivo está rastreado**. Corrección:

```gitignore
**/di/BuildConfigSecrets.kt
```

Lo mismo pasa con `/build` y `/app/build`: `**/build/` ya lo cubre, los otros dos sobran.

### 1.3 `google-services.json` duplicado

Está en `vivid-app/app/google-services.json` (necesario para compilar) y también en
`uploads/google-services.json` (copia suelta). No contiene secretos críticos, pero
expone `project_id`, `api_key` y `mobilesdk_app_id`. Como mínimo borra la copia de
`uploads/`. Lo ideal: inyectarlo en CI desde un secret base64
(`GOOGLE_SERVICES_JSON`) y sacarlo del repo.

### 1.4 Endurecer los workflows

- Fijar las actions por SHA en vez de por tag flotante (`uses: actions/checkout@<sha>`),
  o al menos activar Dependabot para `github-actions`.
- `deploy-functions.yml` no declara `permissions:` → hereda el token con permisos por
  defecto del repo. Añadir `permissions: contents: read`.
- Añadir un escaneo de secretos (`gitleaks`) y CodeQL para Kotlin/JS.

---

## 2. Higiene del repositorio (P1)

Actualmente hay 282 archivos versionados, y una parte no debería estar:

| Ruta | Qué es | Acción |
|---|---|---|
| `.sdkman/` (37 archivos) | Instalación local de SDKMAN de tu máquina | Borrar del repo + ignorar |
| `.bashrc`, `.zshrc`, `.wget-hsts` | Dotfiles del entorno personal | Borrar + ignorar |
| `vivid-app/.gradle/` | Caché de Gradle, incluidos `.class`, `.lock`, `.bin` | Borrar del repo (el ignore `.gradle/` ya existe, pero llegaron antes) |
| `vivid-app/.kotlin/sessions/*.salive` | Sesiones del compilador Kotlin | Borrar |
| `*.patch` (5 en la raíz) | Parches históricos de arreglos de build | Mover a `docs/history/` o borrar; el historial de Git ya los contiene |
| `uploads/Screenshot_*.png`, `images/vivid_icon.png` (1.7 MB) | Capturas y assets sueltos | Mover a `docs/` o reducir; el icono de 1.7 MB no se usa en la app (los mipmaps ya están en `res/`) |
| `Vivid/` (carpeta vacía) | Residuo | Borrar |

Los archivos de `.gradle/` versionados son especialmente dañinos: pueden colisionar
con la caché que restaura `gradle/actions/setup-gradle@v4` y provocar builds raros.

Comando de limpieza (no borra del disco, solo desindexa lo que ya está ignorado):

```bash
git rm -r --cached .sdkman .bashrc .zshrc .wget-hsts \
  vivid-app/.gradle vivid-app/.kotlin uploads/google-services.json
```

También falta un **`README.md`** en la raíz: qué es Vivid, cómo compilar, qué secrets
y variables hay que configurar en Actions. Hoy esa información solo vive en comentarios
dispersos dentro de los YAML.

---

## 3. GitHub Actions (P1)

### 3.1 Trabajo duplicado en cada push

`build.yml` (debug) y `build-apk.yml` (release) se disparan **los dos** en `push` a
`main`. Compilas la app dos veces por commit. Reparto recomendado:

- `build.yml` → solo `pull_request` + `workflow_dispatch`. Que sea el "check" rápido:
  `assembleDebug` + `test` + `lint`.
- `build-apk.yml` → solo `push: tags: ['v*']` + `workflow_dispatch`. El release se
  hace cuando etiquetas una versión, no en cada commit.

### 3.2 Falta `concurrency`

Sin esto, si haces 3 pushes seguidos se encolan 3 builds de 30 minutos:

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

(en el workflow de release conviene `cancel-in-progress: false`).

### 3.3 El build está configurado para ser lento

En `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.workers.max=1
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=false
ksp.incremental=false
```

Esto se puso para arreglar OOM ("Gradle daemon disappeared"), pero es exagerado para
el runner actual: `ubuntu-latest` tiene **4 vCPU y 16 GB de RAM**. Con 1 GB de heap
estás forzando GC constante, que es otra causa clásica de builds eternos.

Además, el workflow exporta `GRADLE_OPTS=-Xmx3g …`, que contradice el `gradle.properties`
(el valor del archivo gana para el proceso de build). Hay dos fuentes de verdad en
conflicto.

Propuesta:

```properties
org.gradle.jvmargs=-Xmx5g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.incremental=true
```

y quitar `--no-daemon --max-workers=1` de la línea de comandos, y quitar `clean`
(no tiene sentido: el runner es efímero y `clean` **invalida la caché** que acabas
de restaurar con `setup-gradle`). Si te preocupa la memoria, sube en pasos
(3g → 5g) verificando.

Ahorro estimado: de 25–40 min a 8–12 min en build en frío, y 3–5 min con caché caliente.

### 3.4 Inconsistencias y redundancias entre los dos workflows

- `setup-java@v5` en uno y `@v4` en el otro. Unificar.
- El bloque de `sdkmanager` está duplicado; `platforms;android-35` lo descarga AGP
  solo. Lo único realmente necesario es `build-tools;35.0.0` para `apksigner`.
- La validación de `VIVID_PUSH_WORKER_URL` está copiada dos veces con reglas distintas.
- Todo esto se resuelve extrayendo un **composite action** en
  `.github/actions/setup-android/action.yml` y usándolo desde ambos workflows.

### 3.5 Falta calidad: tests y lint nunca se ejecutan

No hay **ni un solo test** en `vivid-app/app/src/test` ni `androidTest` (solo las
dependencias declaradas). Y en `build.gradle.kts`:

```kotlin
lint {
    abortOnError = false
    checkReleaseBuilds = false
}
```

Con eso, el CI en verde solo garantiza "compila". Propuesta mínima:

- Job `check`: `./gradlew testDebugUnitTest lintDebug` con `actions/upload-artifact`
  del reporte HTML y `dorny/test-reporter` para ver los fallos en el PR.
- Volver a `abortOnError = true` con un `lint-baseline.xml` generado, para que los
  problemas actuales queden congelados pero los nuevos rompan el build.
- Tests de migración de Room con `MigrationTestHelper` (los esquemas JSON ya se
  exportan; hoy se suben como artefacto y nadie los usa). Esto evita el crash clásico
  "Room cannot verify the data integrity" en usuarios reales.
- Tests de `firestore.rules` con el emulador de Firebase (`@firebase/rules-unit-testing`).
  Son 19 KB de reglas de seguridad sin ninguna prueba automática.

### 3.6 Distribución del APK

Hoy el APK solo vive 30 días como artefacto de Actions, y hay que estar logueado en
GitHub para descargarlo. Mejoras:

- Publicar una **GitHub Release** al etiquetar (`softprops/action-gh-release`), con
  el APK adjunto y changelog autogenerado. Requiere `permissions: contents: write`.
- Añadir `checksums.txt` (SHA-256) junto al APK.
- Generar también el **AAB** (`bundleRelease`) si algún día publicas en Play.
- Publicar el fingerprint del certificado de firma en el README para que los usuarios
  puedan verificar el APK.
- Opcional: distribución con Firebase App Distribution para testers.

### 3.7 `versionCode` frágil

Se deriva de `GITHUB_RUN_NUMBER`, pero hay **dos** workflows con contadores
independientes: el número de un release puede quedar por debajo de otro anterior, y
Android rechaza downgrades de `versionCode`. Con releases por tag conviene derivar
`versionCode` y `versionName` del tag semver (`v2.2.0` → `20200`), o mantener el
contador de un único workflow.

### 3.8 `deploy-functions.yml`

```yaml
run: echo '${{ secrets.FIREBASE_SERVICE_ACCOUNT_JSON }}' > "${GOOGLE_APPLICATION_CREDENTIALS}"
```

Problemas:

- Interpolación directa de un secreto dentro de comillas simples en bash: si el JSON
  contiene un `'` el comando se rompe o ejecuta lo que venga detrás. Correcto:
  pasarlo por `env:` y usar `printf '%s' "$FIREBASE_SERVICE_ACCOUNT_JSON" > …`.
- Falta `permissions:`, `timeout-minutes` y `concurrency`.
- `npm install -g firebase-tools@latest` no es reproducible: un día se rompe solo.
  Fijar versión.
- Mejor aún: migrar a **OIDC / Workload Identity Federation**
  (`google-github-actions/auth`) y eliminar la service-account key del repo de secrets.
- El `--project verigram-c58a6` está hardcodeado; ya existe `.firebaserc`, úsalo.

### 3.9 `deploy-cloudflare-worker.yml`

Está bastante bien (valida secrets, no filtra nada). Mejoras menores:

- Ejecutar `npm run check` (`wrangler deploy --dry-run`) antes del deploy real.
- El `wrangler secret put` se ejecuta en cada deploy aunque el secreto no haya
  cambiado; no es un error, pero puede hacerse condicional.
- Añadir `concurrency` para no pisar dos deploys simultáneos.
- Añadir un smoke test post-deploy: `curl -f https://.../health` (el Worker ya expone
  ese endpoint).
- `compatibility_date = "2026-08-16"` es de hoy: correcto, pero conviene fijarlo y no
  moverlo sin probar.

---

## 4. Configuración de la app Android (P2)

- **R8 desactivado en release** (`isMinifyEnabled = false`). Activar
  `isMinifyEnabled = true` + `isShrinkResources = true` puede recortar el APK un
  30–50 % (tienes Compose, media3, CameraX, Firebase, OkHttp, transcoder). Hay que
  probar con cuidado y añadir reglas para Room/Hilt/Firebase, pero
  `proguard-rules.pro` está prácticamente vacío hoy.
- **Splits por ABI** o AAB: con media3 + transcoder llevas librerías nativas para 4
  arquitecturas en un solo APK.
- `composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }` es **configuración
  muerta**: con Kotlin 2.0 y el plugin `kotlin-compose` ese valor se ignora. Se puede
  borrar.
- `configurations.all { resolutionStrategy.force("com.squareup:javapoet:1.13.0") }` y
  `hilt { enableAggregatingTask = false }` son parches de versiones antiguas; conviene
  revisar si siguen siendo necesarios al actualizar (el segundo ralentiza Hilt).
- `buildConfigField` de `CF_BASE_URL` y `PUSH_WORKER_URL` está repetido tres veces
  (defaultConfig + debug + release). Con dejarlo en `defaultConfig` basta.
- `CF_BASE_URL_VALUE` sigue siendo el placeholder `https://us-central1-TU_PROYECTO...`.
  O se completa o se elimina esa ruta de código.
- Versiones atrasadas: AGP 8.7.3, Kotlin 2.0.21, media3 1.4.1, CameraX 1.3.4,
  Compose BOM 2025.04. Actualizar en un PR aparte, no junto a otros cambios.
- `targetSdk = 35`: Play exigirá 36 en 2026; conviene planificarlo.
- Falta `dependabot.yml` para `gradle`, `npm` (worker y cloud-function) y
  `github-actions`.
- Archivos monolíticos: `FeedScreen.kt` (100 KB), `ChatScreen.kt` (60 KB),
  `SettingsScreen.kt` (56 KB). Dividirlos acelera la compilación incremental y hace el
  código mantenible.

---

## 5. Plan sugerido por fases

**Fase 1 — hoy (seguridad):**
1. Rotar la application key de Backblaze.
2. Arreglar `.gitignore` y desindexar `BuildConfigSecrets.kt`, `.sdkman/`, dotfiles,
   `.gradle/`, `.kotlin/`, `uploads/google-services.json`.
3. Inyectar las claves B2 por secrets de Actions (paso intermedio) o migrar a la
   Cloud Function (definitivo).

**Fase 2 — CI (mismo día):**
4. `concurrency` en los 4 workflows.
5. Separar disparadores: debug en PR, release en tag.
6. Subir memoria, quitar `clean`/`--no-daemon`/`--max-workers=1`, activar caché.
7. Composite action para el setup de Android.
8. Arreglar la interpolación del secreto en `deploy-functions.yml`.

**Fase 3 — calidad:**
9. Job de `test` + `lint` con baseline.
10. GitHub Release automática con APK + SHA-256.
11. Dependabot + gitleaks + CodeQL.

**Fase 4 — app:**
12. Activar R8 y medir tamaño.
13. Tests de migración de Room y de `firestore.rules`.
14. Actualizar toolchain.
