# Vivid v2.1 - con credenciales embebidas

Este ZIP incluye TODO tu proyecto + las nuevas features + las
claves B2 directamente en el codigo.

## !! IMPORTANTE - MODO INSEGURO !!

Las claves B2 estan dentro del codigo. Si tu repo es publico,
quedan expuestas para siempre.

Asumiendo que aceptas este riesgo:

## Como compilar (con GitHub Actions)

### 1. Sube el ZIP a un repo en GitHub
```bash
# Descomprime, inicializa git, push
unzip Vivid-v2.1-completo.zip
cd vivid-app
git init
git add .
git commit -m "Vivid v2.1 con Reels + Stories + Material You 3"
git remote add origin https://github.com/TU_USUARIO/Vivid.git
git push -u origin main
```

### 2. Las GitHub Actions compilan automaticamente
- El workflow .github/workflows/build-apk.yml ya esta incluido
- No necesitas agregar secrets (las credenciales estan en el codigo)
- Ve a la pestana "Actions" en GitHub para ver el progreso

### 3. Descarga el APK
- Actions -> ultima corrida exitosa -> Artifacts -> vivid-apks.zip
- O si hiciste un tag v1.0.0, esta en Releases

## Que incluye

- Reels con video (compresion + watermark + miniatura + filtros)
- Stories con video (stickers + texto + expira 24h)
- Material You 3 completo
- Backblaze B2 via Cloud Functions (signed URLs)
- Analytics (views + watch-time)
- Push notifications

Lee SETUP_GUIDE_V2.md para detalles.

## Para hacerlo seguro en el futuro

Cuando migres a modo seguro:
1. Despliega Cloud Function: `firebase deploy --only functions`
2. Borra las claves de app/src/main/java/com/vivid/app/di/BuildConfigSecrets.kt
3. Usa GitHub Secrets con CF_BASE_URL
