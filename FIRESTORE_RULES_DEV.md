# Reglas Firestore de desarrollo para probar Vivid

Úsalas solo para pruebas. No son reglas finales de producción.

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() {
      return request.auth != null;
    }

    match /users/{userId} {
      allow read: if signedIn();
      allow create, update: if signedIn() && request.auth.uid == userId;

      match /followers/{docId} {
        allow read: if signedIn();
        allow write: if signedIn();
      }

      match /following/{docId} {
        allow read: if signedIn();
        allow write: if signedIn();
      }
    }

    match /posts/{postId} {
      allow read: if signedIn();
      allow create: if signedIn() && request.resource.data.userId == request.auth.uid;
      allow update, delete: if signedIn() && resource.data.userId == request.auth.uid;
    }

    match /chats/{chatId} {
      allow read, create, update: if signedIn()
        && request.auth.uid in request.resource.data.participants;

      allow read: if signedIn()
        && request.auth.uid in resource.data.participants;

      match /messages/{messageId} {
        allow read: if signedIn()
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;

        allow create: if signedIn()
          && request.resource.data.senderId == request.auth.uid
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;
      }
    }

    match /stories/{storyId} {
      allow read: if signedIn();
      allow create, update, delete: if signedIn()
        && request.resource.data.userId == request.auth.uid;
    }
  }
}
```

Si las reglas te dan problema con `request.resource.data.participants` en updates parciales de chats, para pruebas puedes simplificar temporalmente:

```js
match /{document=**} {
  allow read, write: if request.auth != null;
}
```

No uses esa regla abierta en producción.
