# Cómo activar Google Sign-In en Vivid

## Estado actual
El código ya está preparado correctamente (después de los fixes de build):

- Usa `BuildConfig.GOOGLE_WEB_CLIENT_ID`
- Si no está configurado, muestra un mensaje claro y deshabilita el botón
- El botón "Continuar con Google" funciona con `GoogleSignInOptions + FirebaseAuth`

**El problema:** `google-services.json` tiene `"oauth_client": []` vacío.  
Necesitas un **Web OAuth Client ID** real de Google.

---

## Pasos para hacerlo funcionar (5-10 min)

### 1. Abre la consola de Google

Ve aquí (proyecto actual del repo):
→ https://console.cloud.google.com/apis/credentials?project=verigram-c58a6

O a través de Firebase:
→ https://console.firebase.google.com/project/verigram-c58a6/authentication/providers

### 2. Crea un "Web application" OAuth Client ID (IMPORTANTE)

1. Haz clic en **+ CREATE CREDENTIALS** → **OAuth client ID**
2. Tipo de aplicación: **Web application**
3. Nombre: `Vivid Android Web Client`
4. En **Authorized JavaScript origins** agrega:
   - `http://localhost`
   - `https://verigram-c58a6.firebaseapp.com`
5. Crea.
6. **Copia el valor de "Client ID"**  
   (se ve así: `1234567890-abc123def456.apps.googleusercontent.com`)

Este es el valor que necesitas.

### 3. (Obligatorio) Registra el SHA-1 de tu Android

1. Crea **otro** Client ID:
   - Tipo: **Android**
   - Package name: `com.vivid.app`
   - SHA-1 fingerprint:

**Obtén tu SHA-1 de debug:**

```bash
# Linux / macOS
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android

# Windows (CMD)
keytool -list -v -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android
```

Copia la línea que dice `SHA1:` (ej: `A1:B2:C3:...`)

Pégala al crear el Client ID de tipo Android.

### 4. Pon el valor en la app

#### Opción más fácil para probar YA (local)

Edita temporalmente este archivo:

**`vivid-app/app/build.gradle.kts`**

Busca esta línea:

```kotlin
val GOOGLE_WEB_CLIENT_ID_VALUE = System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""
```

Cámbiala por:

```kotlin
val GOOGLE_WEB_CLIENT_ID_VALUE = "TU_WEB_CLIENT_ID_AQUI.apps.googleusercontent.com"
```

Guarda, compila y prueba.

#### Opción recomendada (no hardcodear)

Crea el archivo `vivid-app/local.properties` (este archivo **no se sube a git**):

```
GOOGLE_WEB_CLIENT_ID=TU_WEB_CLIENT_ID_AQUI.apps.googleusercontent.com
```

Luego modifica `build.gradle.kts` para leerlo (puedo hacerlo yo si quieres).

#### Opción para GitHub Actions (para que el APK de CI también funcione)

1. Ve a tu repo → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret**
   - Name: `GOOGLE_WEB_CLIENT_ID`
   - Value: `TU_WEB_CLIENT_ID_AQUI.apps.googleusercontent.com`

### 5. (Recomendado) Actualizar el workflow

Puedo agregar automáticamente esto al workflow para que use el secret.

---

## Probar la app

1. Compila el APK (local o GitHub Actions)
2. Instálalo
3. Abre la pantalla de login
4. Presiona **"Continuar con Google"**

Deberías ver el selector de cuentas de Google.

---

## Errores comunes y soluciones

| Error                              | Causa probable                          | Solución |
|------------------------------------|-----------------------------------------|----------|
| "Google Sign-In no está configurado" | Falta el `GOOGLE_WEB_CLIENT_ID`        | Pon el valor como se indica arriba |
| ApiException: 10                   | SHA-1 no registrado                     | Agrega el SHA-1 en Google Cloud |
| ApiException: 12500                | SHA-1 incorrecto o proyecto equivocado  | Verifica project y SHA-1 |
| No aparece el botón de Google      | BuildConfig vacío                       | Recompila después de poner el ID |

---

## ¿Quieres que haga los cambios automáticos?

Dime **sí** y yo:

- A. Agrego soporte para leer `local.properties` de forma limpia
- B. Actualizo `.github/workflows/build.yml` para que use el secret `GOOGLE_WEB_CLIENT_ID`
- C. Agrego un paso en el README con instrucciones rápidas

¿Cuál opción quieres primero?
