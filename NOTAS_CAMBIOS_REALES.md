# Cambios reales agregados a Vivid

Esta versión ya elimina varios datos demo y deja la base lista para comportamiento tipo Instagram real.

## Mensajes reales

- La pantalla de mensajes ya no carga chats demo.
- Ahora escucha la colección Firestore `chats` en tiempo real.
- Desde Buscar puedes tocar **Mensaje** para abrir/iniciar un chat con otro usuario real.
- Los mensajes se guardan en:

```text
chats/{chatId}/messages/{messageId}
```

- Cada chat guarda metadata:

```text
chats/{chatId}
  participants: [uid1, uid2]
  participantNames: { uid1: "...", uid2: "..." }
  participantAvatars: { uid1: "...", uid2: "..." }
  lastMessage: "..."
  lastTimestamp: 123456789
```

## Perfiles en cero como Instagram real

Al registrar/iniciar sesión se crea/asegura el documento:

```text
users/{uid}
```

Con contadores iniciales:

```text
postsCount: 0
followersCount: 0
followingCount: 0
```

El perfil ya no muestra 245 posts, 12.4k seguidores ni 890 siguiendo. Si no hay publicaciones reales, muestra estado vacío.

## Buscar personas reales

La pantalla Buscar ya no usa usuarios demo. Busca en Firestore:

```text
users
```

por `usernameLower`.

## Stories y reels sin demos

- Se eliminaron las stories demo.
- Se eliminaron los reels demo.
- Stories escucha la colección:

```text
stories
```

con documentos activos que tengan:

```text
userId
username
avatarUrl
mediaUrl
expiresAt
```

- Reels escucha la colección:

```text
reels
```

con documentos que tengan:

```text
videoUrl
username
caption
likes
timestamp
```

Si no hay contenido real, muestra estado vacío en vez de demos.

## Importante sobre Firebase Rules

Para probar rápido en desarrollo, tus reglas de Firestore deben permitir a usuarios autenticados leer usuarios/chats/stories y escribir sus propios mensajes. Si tus reglas están cerradas, la app compila pero no podrá enviar mensajes ni leer usuarios.
