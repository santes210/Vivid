# Cómo compilar Vivid SIN computadora (usando GitHub)

Esta es la forma más fácil y gratuita de compilar tu app Android sin tener Android Studio ni computadora potente.

---

## Paso 1: Crea cuenta en GitHub

1. Ve a: [https://github.com](https://github.com)
2. Haz clic en **Sign up** (Crear cuenta)
3. Crea tu cuenta gratis

---

## Paso 2: Crea un nuevo repositorio

1. Una vez dentro de GitHub, haz clic en el botón verde **"New"** (arriba a la izquierda)
2. Ponle nombre al repositorio: `vivid-app`
3. Marca la opción **"Add a README file"**
4. Haz clic en **"Create repository"**

---

## Paso 3: Sube tu proyecto

### Opción A (Más fácil - Usando GitHub Desktop o web)

1. En tu repositorio recién creado, haz clic en **"uploading an existing file"**
2. Arrastra **toda la carpeta** `vivid-app` (la que te di)
3. O sube los archivos uno por uno
4. Haz clic en **"Commit changes"**

### Opción B (Recomendada)

Usa la app **GitHub** para celular (disponible en Play Store) o simplemente sube los archivos desde la web.

---

## Paso 4: Activar la compilación automática

Ya incluí el archivo `.github/workflows/build.yml` en el proyecto.

Este archivo hace que GitHub compile automáticamente tu app cada vez que subas cambios.

---

## Paso 5: Compilar la app

1. Ve a tu repositorio en GitHub
2. Haz clic en la pestaña **"Actions"** (arriba)
3. Verás el workflow llamado **"Build Vivid APK"**
4. Haz clic en **"Run workflow"** → **"Run workflow"** (botón verde)

¡GitHub empezará a compilar tu app!

---

## Paso 6: Descargar el APK

Cuando termine la compilación (tarda 3-8 minutos):

1. Haz clic en el workflow que se ejecutó
2. Baja hasta la sección **"Artifacts"**
3. Haz clic en **"vivid-debug-apk"**
4. Se descargará el archivo `app-debug.apk`

¡Listo! Ya tienes tu app instalable.

---

## Notas importantes

- **Primera vez**: Tarda un poco más (descarga Gradle)
- **Siguientes compilaciones**: Son más rápidas
- El APK se llama `app-debug.apk`
- Puedes instalarlo directamente en tu teléfono Android

---

## ¿Quieres compilar en Release (versión final)?

Cambia en el archivo `.github/workflows/build.yml` la línea:

```yaml
if: github.ref == 'refs/heads/main'
```

O simplemente ejecuta manualmente el workflow.

---

**¿Necesitas ayuda con algún paso?** Dime en qué parte te atoras.