# Musica libre de regalias

Coloca tus archivos MP3 aqui. Recomendados de fuentes libres:

## Sitios recomendados
- https://freemusicarchive.org/  (CC0 / CC-BY)
- https://incompetech.com/        (Kevin MacLeod, CC-BY)
- https://pixabay.com/music/      (Content ID free)
- https://soundcloud.com (filtrar por "no copyright")

## Formatos
- MP3 / OGG / M4A (nunca WAV: infla el APK)
- Duracion: 30s - 3min (seran looped o cortados)
- Tamano: < 5MB por track (limite de assets en Android)

Las pistas demo van en `.mp3`. Posts viejos que aún guardan `music/*.wav`
se remapean a `.mp3` en runtime (`MusicAssets.resolvePackedPath`).

## Naming convention
- background-1.mp3, background-2.mp3, ...
- O por mood: happy.mp3, sad.mp3, energetic.mp3

## Licencia
Incluye en la app un screen "Creditos de musica" donde listes
las canciones usadas y su autor + licencia CC-BY si aplica.
