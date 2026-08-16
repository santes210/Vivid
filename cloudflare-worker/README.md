# Vivid Push Worker (sin plan Blaze)

Envía notificaciones FCM desde Cloudflare después de validar cada acción contra Firebase Auth y Firestore. La app nunca decide libremente el destinatario ni el texto.

## Tipos soportados

- Likes y comentarios de posts y reels.
- Nuevos seguidores de cuentas públicas.
- Solicitudes para cuentas privadas.
- Mensajes de texto, imagen, voz y respuestas a stories.

El Worker respeta `notifyLikesComments`, `notifyNewFollowers` y `notifyDirectMessages`, evita auto-notificaciones, deduplica eventos y elimina tokens rechazados por FCM.

## 1. Service account

Usa una service account dedicada al Worker. Necesita:

- Leer los documentos que el Worker valida y los tokens FCM.
- Crear, actualizar y borrar documentos en `_pushNotifications`.
- Borrar tokens inválidos en `users/{uid}/fcmTokens`.
- El permiso `cloudmessaging.messages.create` para FCM HTTP v1.

No incluyas el JSON en Git ni en el APK. Confirma también que **Firebase Cloud Messaging API (HTTP v1)** esté habilitada en Google Cloud.

## 2. Configurar Cloudflare

```bash
cd cloudflare-worker
npm install
npx wrangler login
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
npm run deploy
```

Pega el JSON completo de la service account cuando Wrangler lo solicite. Si cambia el proyecto, actualiza `FIREBASE_PROJECT_ID` en `wrangler.toml`.

Comprueba el despliegue:

```bash
curl https://vivid-push.<tu-subdominio>.workers.dev/health
```

## 3. Configurar Android

La URL se inyecta durante el build, sin credenciales:

```bash
cd vivid-app
./gradlew assembleDebug \
  -PvividPushWorkerUrl=https://vivid-push.<tu-subdominio>.workers.dev
```

También puedes definir la variable de entorno:

```bash
export VIVID_PUSH_WORKER_URL=https://vivid-push.<tu-subdominio>.workers.dev
```

Para GitHub Actions crea una variable de repositorio llamada `VIVID_PUSH_WORKER_URL`. Los APK compilados sin URL siguen funcionando, pero registran una advertencia y no solicitan pushes al Worker.

## 4. Prueba recomendada

1. Instala un APK configurado en dos dispositivos o perfiles Android.
2. Inicia sesión con usuarios diferentes.
3. Confirma que ambos tienen documentos en `users/{uid}/fcmTokens`.
4. Cierra la app receptora.
5. Prueba mensaje, follow/solicitud, like y comentario en post y reel.
6. Desactiva cada preferencia desde Ajustes y confirma que el Worker responde con `skipped: "preference"`.

## Seguridad

`POST /notify` exige un Firebase ID token válido. Para cada tipo, el Worker lee la acción recién creada y comprueba que pertenece al UID autenticado. Los datos `targetUid`, IDs y payload enviados por Android se consideran no confiables hasta completar esa validación.

La colección `_pushNotifications` es interna y no está autorizada por las reglas móviles; la service account accede a ella mediante IAM. Sus documentos sirven para evitar duplicados. Conviene aplicar una limpieza periódica a marcadores antiguos si el volumen crece.
