# Cómo crear el Client ID de tipo Android para Google Sign-In

Este Client ID es **obligatorio** para que Google reconozca tu app Android (paquete + firma).

## 1. Obtén tu SHA-1 (en tu computadora)

Ejecuta esto **en tu PC** donde tienes Android Studio (no aquí en la terminal del agente).

### Debug (para pruebas locales - recomendado empezar por aquí)

**Linux / macOS:**
```bash
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android
```

**Windows (abre CMD o PowerShell):**
```cmd
keytool -list -v -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android
```

Busca esta línea y **copia todo el valor**:

```
SHA1: A1:B2:C3:D4:E5:F6:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF
```

### Release (cuando publiques la app)

```bash
keytool -list -v -alias TU_ALIAS \
  -keystore /ruta/completa/a/tu-release-key.jks \
  -storepass TU_PASSWORD
```

---

## 2. Crea el Client ID Android en Google Cloud Console

1. Abre este enlace directo a tu proyecto:
   → **https://console.cloud.google.com/apis/credentials?project=verigram-c58a6**

2. Haz clic en:
   **+ CREATE CREDENTIALS** → **OAuth client ID**

3. En **Application type** selecciona:
   **Android**

4. Rellena exactamente así:

   - **Name**: `Vivid Android Debug`  
     (o `Vivid Android Release` si es para producción)

   - **Package name**:  
     `com.vivid.app`

   - **SHA-1 certificate fingerprint**:  
     Pega aquí el SHA-1 que copiaste en el paso 1.

5. Haz clic en **Create**.

---

## 3. ¿Qué hago con este Client ID?

**No necesitas copiar este Client ID** en ningún lado del código.

Este Client ID de tipo **Android** sirve solo para que Google autorice tu app.

Lo único que sí necesitas poner en el código es el **Client ID de tipo Web application** (ese va en `GOOGLE_WEB_CLIENT_ID`).

---

## 4. También necesitas el Client ID Web (para el código)

Si aún no lo tienes:

1. En la misma página de Credentials, crea otro:
   - **+ CREATE CREDENTIALS** → **OAuth client ID**
   - Tipo: **Web application**
   - Nombre: `Vivid Web Client`
   - Authorized JavaScript origins:
     - `http://localhost`
     - `https://verigram-c58a6.firebaseapp.com`
2. Crea y **copia** ese Client ID (el que termina en `.apps.googleusercontent.com`).

Ese es el que vas a usar como valor de `GOOGLE_WEB_CLIENT_ID`.

---

## Pasos finales para probar

1. Crea el Client ID Android (con tu SHA-1).
2. Crea el Client ID Web y copia su valor.
3. Pon el **Web Client ID** en tu proyecto:
   - Opción rápida: edita temporalmente `vivid-app/app/build.gradle.kts`
   - Mejor: usa `local.properties` o GitHub Secret.
4. Compila e instala la app.
5. Prueba el botón **"Continuar con Google"**.

---

## Errores comunes

| Error que ves en la app     | Qué significa                     | Solución |
|-----------------------------|-----------------------------------|----------|
| `ApiException: 10`          | SHA-1 no registrado               | Crea el Client ID Android con el SHA-1 correcto |
| `ApiException: 12500`       | Firma o paquete no autorizado     | Verifica package `com.vivid.app` + SHA-1 |
| Funciona en emulador pero no en teléfono | Usando debug SHA-1 en release | Crea Client ID Android también para tu release key |

---

## ¿Quieres que prepare el código para leer `local.properties` automáticamente?

Dime "sí" y agrego el código para que puedas poner:

```
GOOGLE_WEB_CLIENT_ID=1234567890-abc123def456.apps.googleusercontent.com
```

en `vivid-app/local.properties` y funcione sin tocar el `build.gradle.kts` cada vez.

También puedo actualizar el workflow de GitHub Actions para que use el secret automáticamente.