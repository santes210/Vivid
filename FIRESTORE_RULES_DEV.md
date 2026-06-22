# Reglas Firestore de desarrollo para que Vivid funcione

Estas reglas permiten en la versión actual:

- seguir / dejar de seguir
- solicitudes de seguimiento para cuentas privadas
- aceptar / rechazar solicitudes
- comentarios
- likes
- stories
- chats y borrar mensajes
- reels

## Pega esto en Firebase Console → Firestore Database → Rules

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
        allow create, delete: if signedIn() && (
          request.auth.uid == userId ||
          request.auth.uid == docId
        );
      }

      match /following/{docId} {
        allow read: if signedIn();
        allow create, delete: if signedIn() && (
          request.auth.uid == userId ||
          request.auth.uid == docId
        );
      }

      match /followRequests/{docId} {
        allow read: if signedIn() && (
          request.auth.uid == userId ||
          request.auth.uid == docId
        );
        allow create, delete: if signedIn() && (
          request.auth.uid == userId ||
          request.auth.uid == docId
        );
      }

      match /sentFollowRequests/{docId} {
        allow read: if signedIn() && request.auth.uid == userId;
        allow create, delete: if signedIn() && (
          request.auth.uid == userId ||
          request.auth.uid == docId
        );
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

## Nota
Si no publicas estas reglas nuevas, las solicitudes privadas, aceptar/rechazar y el borrado de mensajes pueden fallar por permisos.
