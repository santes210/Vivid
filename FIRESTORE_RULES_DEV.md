# Reglas Firestore de desarrollo para que Vivid funcione

Estas reglas están pensadas para **hacer funcionar la app actual** con:

- seguir / dejar de seguir
- comentar publicaciones
- likes
- stories
- chats
- reels

## Regla recomendada para esta versión

Copia y pega esto en **Firebase Console → Firestore Database → Rules**:

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() {
      return request.auth != null;
    }

    function socialCountersOnly() {
      return signedIn()
        && request.resource.data.diff(resource.data).changedKeys().hasOnly([
          'followersCount',
          'followingCount',
          'likesCount',
          'commentsCount',
          'updatedAt'
        ]);
    }

    match /users/{userId} {
      allow read: if signedIn();
      allow create: if signedIn() && request.auth.uid == userId;
      allow update: if signedIn() && (
        request.auth.uid == userId || socialCountersOnly()
      );

      match /followers/{docId} {
        allow read: if signedIn();
        allow create, delete: if signedIn() && docId == request.auth.uid;
      }

      match /following/{docId} {
        allow read: if signedIn();
        allow create, delete: if signedIn() && request.auth.uid == userId;
      }
    }

    match /posts/{postId} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.userId == request.auth.uid;
      allow update: if signedIn() && (
        resource.data.userId == request.auth.uid ||
        request.resource.data.diff(resource.data).changedKeys().hasOnly([
          'likesCount',
          'commentsCount'
        ])
      );
      allow delete: if signedIn() && resource.data.userId == request.auth.uid;

      match /comments/{commentId} {
        allow read: if signedIn();
        allow create: if signedIn() && request.resource.data.userId == request.auth.uid;
        allow update, delete: if signedIn() && resource.data.userId == request.auth.uid;
      }
    }

    match /stories/{storyId} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.userId == request.auth.uid;
      allow update, delete: if signedIn() && resource.data.userId == request.auth.uid;
    }

    match /reels/{reelId} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.userId == request.auth.uid;
      allow update: if signedIn() && (
        resource.data.userId == request.auth.uid ||
        request.resource.data.diff(resource.data).changedKeys().hasOnly(['likes'])
      );
      allow delete: if signedIn() && resource.data.userId == request.auth.uid;
    }

    match /chats/{chatId} {
      allow read: if signedIn() && request.auth.uid in resource.data.participants;
      allow create: if signedIn() && request.auth.uid in request.resource.data.participants;
      allow update: if signedIn() && request.auth.uid in resource.data.participants;

      match /messages/{messageId} {
        allow read: if signedIn()
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;

        allow create: if signedIn()
          && request.resource.data.senderId == request.auth.uid
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;

        allow update, delete: if signedIn()
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;
      }
    }
  }
}
```

---

## Por qué te salía “permisos insuficientes”

### Seguir personas
Tu app al seguir a alguien hace estas operaciones:

- crea `users/{miUid}/following/{otroUid}`
- crea `users/{otroUid}/followers/{miUid}`
- actualiza `users/{miUid}.followingCount`
- actualiza `users/{otroUid}.followersCount`

Si tus reglas solo dejaban editar **tu propio documento** de usuario, Firestore bloqueaba la parte de:

- actualizar `followersCount` del otro usuario

Por eso te fallaba el follow.

### Comentar publicaciones
Tu app al comentar hace esto:

- crea `posts/{postId}/comments/{commentId}`
- actualiza `posts/{postId}.commentsCount`

Si no existían reglas para la subcolección `comments`, o si el `post` solo lo podía actualizar el dueño, Firestore lo bloqueaba.

Por eso ni siquiera el dueño podía comentar si la regla no contemplaba `comments` y `commentsCount`.

---

## Archivo listo en el proyecto
También te dejé esta misma regla en:

```text
firestore.rules
```

---

## Importante
Estas reglas son **de desarrollo / compatibilidad** para que la app actual funcione.

Más adelante se pueden endurecer, pero primero conviene dejar estable:

- follow
- comments
- likes
- stories
- chats

Si quieres, después te preparo una **versión más segura** de reglas y también adapto el código para que no necesite permisos tan amplios.
