# Puesta en marcha: Backblaze B2 + Cloudflare Worker

**Solo con el móvil. No hace falta terminal, ni Termux, ni PC.**

Todo se hace desde el navegador del teléfono: tres páginas web
(Backblaze, GitHub y Cloudflare) y copiar/pegar dos claves.

Tiempo estimado: **15 minutos**.

---

## Antes de empezar: qué cambió

Las claves de Backblaze ya **no están en la app**. Ahora viven cifradas dentro del
Cloudflare Worker, que hace de portero:

```
   App Android                Worker (tiene las claves)          Backblaze B2
        │                              │                              │
        │─ "quiero subir esto" ───────►│                              │
        │   + su ID token de Firebase  │── autoriza con las claves ──►│
        │◄─ permiso temporal ──────────│                              │
        │                                                             │
        │══ el archivo sube DIRECTO a B2 (no pasa por Cloudflare) ═══►│
```

El archivo nunca atraviesa el Worker, así que no aplica el límite de 100 MB del
plan gratuito y no gastas ancho de banda de Cloudflare.

**GitHub Actions hace de terminal por ti**: el workflow despliega el Worker,
le guarda las claves dentro y comprueba que todo responda. Tú solo pegas las
claves en GitHub una vez.

> 💡 **Consejo para el móvil:** en Chrome, menú ⋮ → marca **"Versión para
> ordenador"** cuando entres a GitHub y Backblaze. Los paneles de configuración
> son bastante más cómodos así.

---

## PASO 1 — Rotar la clave de Backblaze (obligatorio)

⚠️ **La clave actual está quemada.** Estuvo en un repositorio y dentro de cada APK
publicado. Aunque ya la borramos del código, sigue en el historial de Git y en los
APK que ya repartiste. Hay que invalidarla.

Desde el navegador del móvil:

1. Entra a **[secure.backblaze.com](https://secure.backblaze.com)** e inicia sesión.
2. Menú lateral → **Application Keys**.
3. Busca la clave con ID `0044482642d8bb00000000005` → **Delete**.
   A partir de ahí la app antigua deja de funcionar (es justo lo que queremos).
4. Pulsa **Add a New Application Key**:

   | Campo | Valor |
   |---|---|
   | Name of Key | `vivid-worker` |
   | Allow access to Bucket(s) | **VividGrem** (solo ese, no "All") |
   | Type of Access | **Read and Write** |
   | Allow List All Bucket Names | ✅ marcado |

5. Pulsa **Create New Key**.

🔴 **Importantísimo:** Backblaze enseña la `applicationKey` **una sola vez**.
Cópiala inmediatamente y pégala en las notas del teléfono junto con el `keyID`.
Si cierras la pantalla sin copiarla, hay que crear otra clave.

Te quedas con dos valores:

```
keyID          → algo como 0044482642d8bb00000000006
applicationKey → algo como K004xxxxxxxxxxxxxxxxxxxxxxxxx
```

> **Nota:** el Worker intenta crear claves temporales limitadas a la carpeta de
> cada usuario (una defensa extra). Si tu clave no tiene el permiso `writeKeys`,
> lo detecta, deja un aviso en el log y sigue funcionando igual. No bloquea nada.

---

## PASO 2 — Pegar las claves en GitHub

Desde el navegador, en tu repositorio:

**Settings → Secrets and variables → Actions → pestaña `Secrets` →
`New repository secret`**

Crea estos dos (uno por uno):

| Name | Secret |
|---|---|
| `B2_KEY_ID` | el `keyID` del paso 1 |
| `B2_APPLICATION_KEY` | la `applicationKey` del paso 1 |

Comprueba que ya tengas también estos tres (si desplegaste el Worker antes, existen):

- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON`

<details>
<summary><b>¿No tienes el token de Cloudflare? Cómo sacarlo desde el móvil</b></summary>

1. Entra a [dash.cloudflare.com/profile/api-tokens](https://dash.cloudflare.com/profile/api-tokens).
2. **Create Token** → busca la plantilla **"Edit Cloudflare Workers"** → **Use template**.
3. Abajo, **Continue to summary** → **Create Token**.
4. Copia el token (también se muestra una sola vez) → pégalo en GitHub como
   `CLOUDFLARE_API_TOKEN`.
5. El `CLOUDFLARE_ACCOUNT_ID` está en la página principal de Cloudflare →
   **Workers & Pages** → barra lateral derecha, **Account ID**.

</details>

---

## PASO 3 — Desplegar el Worker (un botón)

En tu repo → pestaña **Actions** → workflow **Deploy Cloudflare Push Worker** →
botón **Run workflow** → **Run workflow**.

Espera 1-2 minutos. Al terminar, **la propia página te muestra un resumen** con la
URL del Worker, algo así:

```
## Worker desplegado

URL del Worker:

    https://vivid-push.TU-CUENTA.workers.dev

Almacenamiento B2: configurado correctamente
```

📋 **Copia esa URL**, la necesitas en el paso 4.

### Si el resumen dice "Almacenamiento B2: NO configurado"

Significa que los secrets `B2_KEY_ID` / `B2_APPLICATION_KEY` no llegaron.
Revisa que los nombres estén escritos **exactamente igual** (mayúsculas y guiones
bajos incluidos) y vuelve a lanzar el workflow.

---

## PASO 4 — Decirle a la app dónde está el Worker

En el repo → **Settings → Secrets and variables → Actions** → pestaña
**`Variables`** (ojo: *Variables*, no *Secrets*) → **New repository variable**:

| Name | Value |
|---|---|
| `VIVID_WORKER_URL` | `https://vivid-push.TU-CUENTA.workers.dev` |

Reglas: **con** `https://`, **sin** barra al final, **sin** `/notify` ni `/storage`.
Solo la URL base. El workflow lo verifica y falla con un mensaje claro si te
equivocas.

> Si ya tenías `VIVID_PUSH_WORKER_URL`, el build la sigue aceptando como
> respaldo. Aun así conviene crear la nueva: el Worker ya no es solo de
> notificaciones.

---

## PASO 5 — Compilar el APK y probar

**Actions** → **Build Signed Release APK** → **Run workflow**.

Cuando termine, baja el APK desde el apartado **Artifacts** (se descarga un `.zip`;
ábrelo con cualquier gestor de archivos de Android y saca el `.apk`).

Instálalo y comprueba:

- [ ] Inicias sesión correctamente
- [ ] Publicas una foto y **se ve** en el feed
- [ ] Subes un reel y **se reproduce**
- [ ] Mandas una imagen y una nota de voz por chat
- [ ] Publicas una historia
- [ ] Las publicaciones antiguas siguen viéndose

---

## Si algo falla: ver los logs sin terminal

Entra a **[dash.cloudflare.com](https://dash.cloudflare.com)** →
**Workers & Pages** → **vivid-push** → pestaña **Logs** → **Begin log stream**.

Deja esa pestaña abierta y usa la app en paralelo: los errores aparecen en vivo.

| Mensaje en el log | Qué significa | Solución |
|---|---|---|
| `B2_KEY_ID is not configured` | El secreto no llegó a Cloudflare | Repite los pasos 2 y 3 |
| `b2_authorize_account failed (401)` | Clave incorrecta o borrada | Revisa que copiaste bien la clave del paso 1 |
| `Missing Firebase ID token` | La app no mandó sesión | Cierra sesión y vuelve a entrar en la app |
| `Key does not belong to...` | La ruta no coincide con el usuario | Es el comportamiento correcto ante un abuso |
| `WORKER_URL no está configurada` | Falta el paso 4 | Crea la variable y recompila |

También puedes comprobar el estado en cualquier momento abriendo esta dirección
en el navegador del móvil:

```
https://vivid-push.TU-CUENTA.workers.dev/health
```

Debe responder: `{"ok":true,"service":"vivid-push","storage":true}`

---

## PASO 6 (opcional) — Egress gratis con dominio propio

Backblaze y Cloudflare son socios de la **Bandwidth Alliance**: el tráfico de
descarga desde B2 servido a través de Cloudflare **no se cobra**. Ahora mismo tu
app descarga directo de `f004.backblazeb2.com`, así que ese tráfico sí te cuenta
($0.01/GB pasada la cuota gratuita).

Necesitas un dominio (~$10/año) añadido a tu cuenta de Cloudflare. Todo se hace
desde el panel web:

1. Cloudflare → tu dominio → **DNS** → **Add record**:
   - Tipo: `CNAME`
   - Nombre: `media`
   - Destino: `f004.backblazeb2.com` (usa el host real que veas en tus URLs)
   - Proxy: **naranja activado** ← imprescindible; en gris no hay egress gratis
2. **Rules → Transform Rules → Rewrite URL**: reescribe la ruta entrante
   `/file/VividGrem/...` hacia el mismo path (Backblaze lo necesita tal cual).
3. En GitHub, edita `cloudflare-worker/wrangler.toml` (se puede editar desde la
   web de GitHub con el lápiz ✏️) y descomenta:
   ```toml
   MEDIA_BASE_URL = "https://media.tudominio.com"
   ```
4. Haz commit: el workflow lo despliega solo.

Ganas: egress $0, y tus usuarios en México descargan desde el datacenter de
Cloudflare más cercano en vez de ir hasta Estados Unidos.

> **Detalle técnico:** las URLs firmadas llevan un `?Authorization=` distinto cada
> vez, así que la caché del CDN no las reaprovecha. Para tráfico alto habría que
> servir las descargas a través del Worker con `cacheEverything`. Hoy es
> optimización prematura; queda anotado.

---

## PASO 7 — Limpiar el historial de Git (cuando tengas un PC)

Ya borramos las claves del código, pero **siguen visibles en los commits antiguos**.
Como las rotaste en el paso 1, esas claves ya no sirven para nada, así que **no es
urgente**. Cuando tengas acceso a una computadora:

```bash
pip install git-filter-repo
git filter-repo --path vivid-app/app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt --invert-paths
git push origin --force --all
```

⚠️ Reescribe el historial. Haz una copia del repo antes.

---

## Resumen de lo que quedó configurado

| Dónde | Qué | ¿Secreto? |
|---|---|---|
| Cloudflare Worker | `B2_KEY_ID`, `B2_APPLICATION_KEY` | 🔒 Sí |
| Cloudflare Worker | `FIREBASE_SERVICE_ACCOUNT_JSON` | 🔒 Sí |
| `wrangler.toml` | `B2_BUCKET_ID`, `B2_BUCKET_NAME` | No (inútiles sin la clave) |
| GitHub Variables | `VIVID_WORKER_URL` | No (es pública) |
| GitHub Secrets | Firma del APK, token de Cloudflare | 🔒 Sí |
| **Dentro del APK** | **nada** | ✅ |

---

## Apéndice: si algún día quieres usar Termux

No hace falta para nada de lo anterior, pero por si acaso: **Wrangler no funciona
bien en Termux**. Necesita `workerd`, el runtime de Cloudflare, que no publica
binarios para Android/ARM en Termux. Los comandos `wrangler deploy` y
`wrangler dev` fallan al arrancar.

Sí funcionan desde Termux, si alguna vez lo necesitas:

- `git` (`pkg install git`) para editar y hacer commits
- `curl` contra la API de Cloudflare, que es lo que Wrangler usa por debajo:

```bash
curl -X PUT \
  "https://api.cloudflare.com/client/v4/accounts/$ACCOUNT_ID/workers/scripts/vivid-push/secrets" \
  -H "Authorization: Bearer $CF_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"B2_KEY_ID","text":"TU_KEY_ID","type":"secret_text"}'
```

Pero repito: **con GitHub Actions no necesitas nada de esto**.
