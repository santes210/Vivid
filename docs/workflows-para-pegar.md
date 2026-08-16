# Workflows para copiar y pegar

El resto de los cambios ya esta subido al repositorio. **Solo faltan estos tres
archivos**, porque GitHub no permite que una app externa modifique la carpeta
`.github/workflows/` sin un permiso especial.

## Como aplicarlos desde el movil

Para cada archivo de abajo:

1. Abre el repo en el navegador y navega hasta el archivo
   (por ejemplo `.github/workflows/build.yml`).
2. Pulsa el lapiz **✏️** (arriba a la derecha) para editarlo.
3. Selecciona **todo** el contenido y borralo.
4. Copia el bloque de aqui abajo y pegalo.
5. Abajo del todo, boton verde **Commit changes**.

> 💡 En Chrome, menu ⋮ → **"Version para ordenador"** hace que el editor sea
> mucho mas manejable en pantalla pequena.
>
> ⚠️ Respeta la indentacion: YAML se rompe si cambian los espacios. Copiar el
> bloque entero de una vez evita ese problema.

## Que cambia en cada uno

| Archivo | Cambio |
|---|---|
| `build.yml` | `VIVID_PUSH_WORKER_URL` pasa a `VIVID_WORKER_URL` |
| `build-apk.yml` | Igual, y la validacion rechaza URLs con `/notify` o `/storage` |
| `deploy-cloudflare-worker.yml` | Sube las claves de B2 a Cloudflare, detecta la URL del Worker y la muestra en el resumen |

Los tres aceptan la variable antigua `VIVID_PUSH_WORKER_URL` como respaldo, asi
que nada se rompe mientras haces el cambio.

---

## `.github/workflows/build.yml`

Compila el APK de depuracion en cada push y pull request.

```yaml
name: Build Vivid APK

on:
  push:
    branches: [main, master]
  pull_request:
    branches: [main, master]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    name: Build Debug APK
    runs-on: ubuntu-latest
    timeout-minutes: 30

    env:
      # Refuerza la memoria aunque Gradle no alcance a leer gradle.properties.
      GRADLE_OPTS: -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -XX:+UseParallelGC"
      JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8

      # URL pública del Cloudflare Worker (push + broker de Backblaze B2).
      # Settings → Secrets and variables → Actions → Variables.
      VIVID_WORKER_URL: ${{ vars.VIVID_WORKER_URL || vars.VIVID_PUSH_WORKER_URL }}

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Validate Worker URL
        shell: bash
        run: |
          if [ -z "$VIVID_WORKER_URL" ]; then
            echo "::warning::No está configurada la variable VIVID_WORKER_URL."
            echo "::warning::El APK compilará, pero fallará al subir o ver archivos."
          elif [[ "$VIVID_WORKER_URL" != https://* ]]; then
            echo "::error::VIVID_WORKER_URL debe comenzar con https://"
            exit 1
          else
            echo "Cloudflare Worker configurado correctamente."
          fi

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: temurin

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Install Android SDK packages
        shell: bash
        run: |
          yes | sdkmanager --licenses >/dev/null || true
          sdkmanager \
            "platforms;android-35" \
            "build-tools;35.0.0" \
            "platform-tools"

      - name: Setup Gradle cache
        uses: gradle/actions/setup-gradle@v4

      - name: Build Debug APK
        working-directory: vivid-app
        shell: bash
        run: |
          chmod +x gradlew
          ./gradlew --version

          ./gradlew clean assembleDebug \
            --no-daemon \
            --max-workers=1 \
            --stacktrace

      - name: Upload APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: vivid-debug-apk
          path: vivid-app/app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
          retention-days: 30
```

---

## `.github/workflows/build-apk.yml`

Compila y firma el APK de release.

```yaml
name: Build Signed Release APK

on:
  push:
    branches: [main]
  workflow_dispatch:

# Solo permite leer el repositorio.
# El APK se descarga desde los artefactos de GitHub Actions.
permissions:
  contents: read

jobs:
  release:
    name: Build signed release APK
    runs-on: ubuntu-latest
    timeout-minutes: 45

    env:
      GRADLE_OPTS: -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -XX:+UseParallelGC"
      JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8

      # URL pública del Cloudflare Worker (push + broker de Backblaze B2).
      VIVID_WORKER_URL: ${{ vars.VIVID_WORKER_URL || vars.VIVID_PUSH_WORKER_URL }}

      # Gradle utiliza estas variables para firmar el APK.
      ANDROID_KEYSTORE_PATH: ${{ github.workspace }}/vivid-release.jks
      ANDROID_KEYSTORE_PASSWORD: ${{ secrets.VIVID_KEYSTORE_PASSWORD }}
      ANDROID_KEY_ALIAS: ${{ secrets.VIVID_KEY_ALIAS }}
      ANDROID_KEY_PASSWORD: ${{ secrets.VIVID_KEY_PASSWORD }}

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Validate Worker URL
        shell: bash
        run: |
          if [ -z "$VIVID_WORKER_URL" ]; then
            echo "::error::Falta la Repository Variable VIVID_WORKER_URL."
            echo "::error::Sin ella el APK no puede subir ni mostrar archivos."
            exit 1
          fi

          if [[ "$VIVID_WORKER_URL" != https://* ]]; then
            echo "::error::VIVID_WORKER_URL debe comenzar con https://"
            exit 1
          fi

          if [[ "$VIVID_WORKER_URL" == */notify || "$VIVID_WORKER_URL" == */storage/* ]]; then
            echo "::error::VIVID_WORKER_URL debe ser solo la URL base del Worker."
            exit 1
          fi

          echo "Cloudflare Worker configurado correctamente."

      - name: Setup JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Install Android SDK packages
        shell: bash
        run: |
          yes | sdkmanager --licenses >/dev/null || true

          sdkmanager \
            "platforms;android-35" \
            "build-tools;35.0.0" \
            "platform-tools"

      - name: Setup Gradle cache
        uses: gradle/actions/setup-gradle@v4

      - name: Restore release keystore
        shell: bash
        env:
          VIVID_KEYSTORE_BASE64: ${{ secrets.VIVID_KEYSTORE_BASE64 }}
        run: |
          if [ -z "$VIVID_KEYSTORE_BASE64" ] || \
             [ -z "$ANDROID_KEYSTORE_PASSWORD" ] || \
             [ -z "$ANDROID_KEY_ALIAS" ] || \
             [ -z "$ANDROID_KEY_PASSWORD" ]; then
            echo "::error::Faltan los secrets de firma:"
            echo "::error::VIVID_KEYSTORE_BASE64"
            echo "::error::VIVID_KEYSTORE_PASSWORD"
            echo "::error::VIVID_KEY_ALIAS"
            echo "::error::VIVID_KEY_PASSWORD"
            exit 1
          fi

          printf '%s' "$VIVID_KEYSTORE_BASE64" \
            | base64 --decode \
            > "$ANDROID_KEYSTORE_PATH"

          if [ ! -s "$ANDROID_KEYSTORE_PATH" ]; then
            echo "::error::El keystore decodificado está vacío."
            exit 1
          fi

          # Verifica el archivo, la contraseña y el alias.
          keytool -list \
            -keystore "$ANDROID_KEYSTORE_PATH" \
            -storepass "$ANDROID_KEYSTORE_PASSWORD" \
            -alias "$ANDROID_KEY_ALIAS" \
            >/dev/null

      - name: Build signed release APK
        working-directory: vivid-app
        shell: bash
        run: |
          chmod +x gradlew

          ./gradlew clean assembleRelease \
            --no-daemon \
            --max-workers=1 \
            --stacktrace

      - name: Verify APK signature
        id: apk
        shell: bash
        run: |
          APK_PATH="$(find vivid-app/app/build/outputs/apk/release \
            -maxdepth 1 \
            -type f \
            -name '*.apk' \
            ! -name '*unsigned*' \
            -print \
            -quit)"

          if [ -z "$APK_PATH" ]; then
            echo "::error::No se generó un APK release firmado."

            find vivid-app/app/build/outputs \
              -maxdepth 5 \
              -type f \
              -print || true

            exit 1
          fi

          "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify \
            --verbose \
            --print-certs \
            "$APK_PATH"

          mkdir -p release-artifacts

          OUTPUT_APK="release-artifacts/Vivid-${GITHUB_RUN_NUMBER}-release.apk"
          cp "$APK_PATH" "$OUTPUT_APK"

          echo "apk_path=$OUTPUT_APK" >> "$GITHUB_OUTPUT"

      - name: Upload signed release APK
        uses: actions/upload-artifact@v4
        with:
          name: vivid-signed-release-vc${{ github.run_number }}
          path: ${{ steps.apk.outputs.apk_path }}
          if-no-files-found: error
          retention-days: 30

      # Cada build genera los esquemas de Room.
      - name: Check Room schemas
        shell: bash
        run: |
          if [ -d "vivid-app/app/schemas" ]; then
            echo "Esquemas de Room generados:"
            find vivid-app/app/schemas -name '*.json' | sort
          else
            echo "::warning::No se encontraron esquemas de Room en vivid-app/app/schemas"
          fi

      - name: Upload Room schemas
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: room-schemas
          path: vivid-app/app/schemas/
          if-no-files-found: ignore
          retention-days: 30

      - name: Remove keystore
        if: always()
        shell: bash
        run: rm -f "$ANDROID_KEYSTORE_PATH"
```

---

## `.github/workflows/deploy-cloudflare-worker.yml`

Despliega el Worker, le guarda las claves de B2 y verifica /health.

```yaml
name: Deploy Cloudflare Push Worker

on:
  push:
    branches: [main]
    paths:
      - 'cloudflare-worker/**'
      - '.github/workflows/deploy-cloudflare-worker.yml'
  workflow_dispatch:

permissions:
  contents: read

jobs:
  deploy:
    name: Deploy push notification Worker
    runs-on: ubuntu-latest
    timeout-minutes: 15

    defaults:
      run:
        working-directory: cloudflare-worker

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: cloudflare-worker/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Validate required secrets
        shell: bash
        env:
          CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          CLOUDFLARE_ACCOUNT_ID: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          FIREBASE_SERVICE_ACCOUNT_JSON: ${{ secrets.FIREBASE_SERVICE_ACCOUNT_JSON }}
          B2_KEY_ID: ${{ secrets.B2_KEY_ID }}
          B2_APPLICATION_KEY: ${{ secrets.B2_APPLICATION_KEY }}
        run: |
          for name in \
            CLOUDFLARE_API_TOKEN \
            CLOUDFLARE_ACCOUNT_ID \
            FIREBASE_SERVICE_ACCOUNT_JSON \
            B2_KEY_ID \
            B2_APPLICATION_KEY
          do
            if [ -z "${!name}" ]; then
              echo "::error::Falta el Repository Secret $name"
              exit 1
            fi
          done

      # Primero crea o actualiza el Worker. El código no utiliza el secreto
      # hasta que recibe una petición, así que puede desplegarse antes.
      - name: Deploy Worker
        id: deploy
        env:
          CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          CLOUDFLARE_ACCOUNT_ID: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
        run: |
          # La salida de wrangler incluye la URL publica del Worker.
          npm run deploy 2>&1 | tee deploy.log

          URL="$(grep -oE 'https://[a-zA-Z0-9._-]+\.workers\.dev' deploy.log | head -1)"
          echo "worker_url=$URL" >> "$GITHUB_OUTPUT"

      # Guarda las credenciales dentro de Cloudflare como secretos cifrados.
      # Nunca se incorporan al repositorio ni al APK.
      - name: Upload Worker secrets
        env:
          CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          CLOUDFLARE_ACCOUNT_ID: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          FIREBASE_SERVICE_ACCOUNT_JSON: ${{ secrets.FIREBASE_SERVICE_ACCOUNT_JSON }}
          B2_KEY_ID: ${{ secrets.B2_KEY_ID }}
          B2_APPLICATION_KEY: ${{ secrets.B2_APPLICATION_KEY }}
        run: |
          printf '%s' "$FIREBASE_SERVICE_ACCOUNT_JSON" \
            | npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON

          # Claves de Backblaze B2. Solo existen en GitHub Secrets y dentro de
          # Cloudflare: nunca llegan al APK ni al repositorio.
          printf '%s' "$B2_KEY_ID" \
            | npx wrangler secret put B2_KEY_ID

          printf '%s' "$B2_APPLICATION_KEY" \
            | npx wrangler secret put B2_APPLICATION_KEY

      - name: Verificar /health y mostrar la URL
        env:
          WORKER_URL: ${{ steps.deploy.outputs.worker_url }}
        run: |
          if [ -z "$WORKER_URL" ]; then
            echo "::warning::No se pudo detectar la URL automaticamente."
            echo "Buscala en el paso 'Deploy Worker' de arriba."
            exit 0
          fi

          echo "Comprobando $WORKER_URL/health ..."
          # El Worker recien desplegado puede tardar unos segundos en propagarse.
          for intento in 1 2 3 4 5; do
            RESPUESTA="$(curl -fsS "$WORKER_URL/health" || true)"
            if [ -n "$RESPUESTA" ]; then break; fi
            echo "Reintentando en 5s (intento $intento/5)..."
            sleep 5
          done

          echo "Respuesta: $RESPUESTA"

          # Escribe un resumen visible en la pagina del workflow, comodo desde
          # el movil sin tener que abrir los logs.
          {
            echo "## Worker desplegado"
            echo ""
            echo "**URL del Worker:**"
            echo ""
            echo '```'
            echo "$WORKER_URL"
            echo '```'
            echo ""
          } >> "$GITHUB_STEP_SUMMARY"

          if echo "$RESPUESTA" | grep -q '"storage":true'; then
            {
              echo "Almacenamiento B2: **configurado correctamente**"
              echo ""
              echo "Siguiente paso: copia la URL de arriba en la variable"
              echo "\`VIVID_WORKER_URL\` (Settings -> Secrets and variables ->"
              echo "Actions -> Variables) y lanza el workflow del APK."
            } >> "$GITHUB_STEP_SUMMARY"
          else
            {
              echo "Almacenamiento B2: **NO configurado**"
              echo ""
              echo "Faltan los secrets \`B2_KEY_ID\` y/o \`B2_APPLICATION_KEY\`."
              echo "Anadelos en Settings -> Secrets and variables -> Actions."
            } >> "$GITHUB_STEP_SUMMARY"
            echo "::error::El Worker responde pero sin las claves de B2."
            exit 1
          fi
```

---

## Cuando termines

Ya con los tres archivos guardados, sigue la guia
[`configuracion-backblaze-cloudflare.md`](configuracion-backblaze-cloudflare.md)
desde el **PASO 1**.

Comprobacion rapida de que quedo bien: entra a la pestana **Actions** del repo.
Si algun archivo tiene un error de YAML, GitHub lo marca en rojo con el numero
de linea, y basta con volver a editarlo.
