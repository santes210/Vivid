# Vivid - APK en GitHub Actions

Esta versión incluye las correcciones de compilación y además cambia varias pantallas para dejar de usar datos demo.

## Errores de build corregidos

- Memoria insuficiente de Gradle en GitHub Actions.
- Iconos Compose no disponibles en `material-icons-core`.
- Imports faltantes de `await()` e `Image`.

## Funciones reales agregadas

- Mensajes reales con Firestore.
- Lista de chats real, sin chats demo.
- Buscar usuarios reales en `users`.
- Botón **Mensaje** para iniciar conversación con otra persona.
- Perfil con contadores reales iniciando en 0:
  - 0 posts
  - 0 seguidores
  - 0 seguidos
- Publicaciones del perfil reales, sin grid demo.
- Stories sin demos.
- Reels sin demos.
- Estados vacíos más limpios con Material 3.

## Cómo generar el APK

1. Descomprime este ZIP.
2. Sube/reemplaza los archivos en tu repositorio GitHub.
3. Haz commit y push a `main` o `master`.
4. Ve a **Actions → Build Vivid APK**.
5. Descarga el artifact `vivid-debug-apk`.

## Importante

Para que mensajes, búsqueda, stories y reels funcionen dentro de la app, Firebase Firestore debe tener reglas que permitan acceso a usuarios autenticados. Incluí el archivo:

```text
FIRESTORE_RULES_DEV.md
```

con reglas de desarrollo para probar rápido.
