/**
 * Tests de seguridad de firestore.rules contra el emulador de Firestore.
 *
 * Se ejecutan en CI con:
 *   firebase emulators:exec --only firestore --project demo-vivid-rules "npm test"
 *
 * Cubren los puntos críticos de privacidad e integridad:
 *   - Acceso no autenticado denegado.
 *   - Privacidad de cuentas privadas (posts/stories/reels).
 *   - Contadores que solo se mueven de a 1 (nadie escribe likesCount=999999).
 *   - Chats: solo participantes; mensajes solo del emisor.
 *   - Cada regla está probada en positivo (permitido) y en negativo (denegado)
 *     para que un cambio accidental se detecte en el PR.
 */
import { test, describe, before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";

const PROJECT_ID = "demo-vivid-rules";

let env;

before(async () => {
  const rules = await readFile(new URL("../firestore.rules", import.meta.url), "utf8");
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { host: "127.0.0.1", port: 8080, rules }
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

/** Crea un usuario directamente (sin pasar por las reglas). */
async function seedUser(uid, data = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`users/${uid}`).set({
      displayName: uid,
      isPrivate: false,
      followersCount: 0,
      followingCount: 0,
      ...data
    });
  });
}

/** Crea un post directamente. */
async function seedPost(postId, data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`posts/${postId}`).set({
      userId: "alice",
      isPrivate: false,
      likesCount: 0,
      commentsCount: 0,
      caption: "hola",
      ...data
    });
  });
}

// ---------------------------------------------------------------------------
// Acceso no autenticado
// ---------------------------------------------------------------------------

describe("acceso no autenticado", () => {
  test("no puede leer usuarios", async () => {
    await seedUser("alice");
    const db = env.unauthenticatedContext().firestore();
    await assertFails(db.doc("users/alice").get());
  });

  test("no puede leer posts ni escribir", async () => {
    await seedUser("alice");
    await seedPost("p1");
    const db = env.unauthenticatedContext().firestore();
    await assertFails(db.doc("posts/p1").get());
    await assertFails(db.doc("posts/p2").set({
      userId: "x", isPrivate: false, likesCount: 0, commentsCount: 0
    }));
  });
});

// ---------------------------------------------------------------------------
// users
// ---------------------------------------------------------------------------

describe("users", () => {
  test("cada usuario solo crea su propio documento", async () => {
    const alice = env.authenticatedContext("alice").firestore();
    await assertSucceeds(alice.doc("users/alice").set({
      displayName: "Alice", isPrivate: false, followersCount: 0, followingCount: 0
    }));
    await assertFails(alice.doc("users/bob").set({
      displayName: "Bob", isPrivate: false, followersCount: 0, followingCount: 0
    }));
  });

  test("cualquier autenticado puede leer perfiles", async () => {
    await seedUser("alice");
    const bob = env.authenticatedContext("bob").firestore();
    await assertSucceeds(bob.doc("users/alice").get());
  });

  test("el dueño edita su perfil libremente", async () => {
    await seedUser("alice", { displayName: "Alice" });
    const alice = env.authenticatedContext("alice").firestore();
    await assertSucceeds(alice.doc("users/alice").update({ displayName: "Alicia" }));
  });

  test("un tercero solo mueve contadores de a 1", async () => {
    await seedUser("alice");
    const bob = env.authenticatedContext("bob").firestore();
    const doc = bob.doc("users/alice");

    await assertSucceeds(doc.update({ followersCount: 1 }));
    // +2 de golpe está prohibido (evita likes/seguidores inflados).
    await assertFails(doc.update({ followersCount: 3 }));
    // Y tampoco puede tocar otros campos del perfil ajeno.
    await assertFails(doc.update({ displayName: "hackeado" }));
  });

  test("solo el dueño borra su cuenta", async () => {
    await seedUser("alice");
    const alice = env.authenticatedContext("alice").firestore();
    const bob = env.authenticatedContext("bob").firestore();
    await assertSucceeds(alice.doc("users/alice").delete());
    await seedUser("alice");
    await assertFails(bob.doc("users/alice").delete());
  });
});

// ---------------------------------------------------------------------------
// Posts: privacidad y contadores
// ---------------------------------------------------------------------------

describe("posts", () => {
  test("cuenta privada: solo el autor y sus seguidores leen", async () => {
    await seedUser("alice", { isPrivate: true });
    await seedPost("p1", { isPrivate: true });

    // Seguidor aceptado → puede leer.
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("users/alice/followers/bob").set({ timestamp: 1 });
    });
    const bob = env.authenticatedContext("bob").firestore();
    await assertSucceeds(bob.doc("posts/p1").get());

    // No seguidor → denegado.
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(carol.doc("posts/p1").get());

    // El autor siempre puede.
    const alice = env.authenticatedContext("alice").firestore();
    await assertSucceeds(alice.doc("posts/p1").get());
  });

  test("cuenta pública: cualquier autenticado lee", async () => {
    await seedUser("alice");
    await seedPost("p1");
    const bob = env.authenticatedContext("bob").firestore();
    await assertSucceeds(bob.doc("posts/p1").get());
  });

  test("crear post exige ser el dueño y contadores en cero", async () => {
    await seedUser("alice");
    const alice = env.authenticatedContext("alice").firestore();
    const bob = env.authenticatedContext("bob").firestore();

    await assertSucceeds(alice.doc("posts/mi-post").set({
      userId: "alice", isPrivate: false, likesCount: 0, commentsCount: 0
    }));
    // userId distinto del auth.uid → denegado.
    await assertFails(alice.doc("posts/post-robo").set({
      userId: "bob", isPrivate: false, likesCount: 0, commentsCount: 0
    }));
    // Un post no puede nacer con likes pre-cargados.
    await assertFails(alice.doc("posts/post-fake-likes").set({
      userId: "alice", isPrivate: false, likesCount: 100, commentsCount: 0
    }));
    // Otro usuario no puede publicar en nombre de alice.
    await assertFails(bob.doc("posts/suplantacion").set({
      userId: "alice", isPrivate: false, likesCount: 0, commentsCount: 0
    }));
  });

  test("tercero: likesCount solo de a 1", async () => {
    await seedUser("alice");
    await seedPost("p1", { likesCount: 5 });
    const bob = env.authenticatedContext("bob").firestore();
    const doc = bob.doc("posts/p1");

    await assertSucceeds(doc.update({ likesCount: 6 }));   // 5 → 6: +1 ok
    await assertFails(doc.update({ likesCount: 10 }));     // 6 → 10: salto prohibido
    // No puede bajar el contador de otro ni tocar el caption.
    await assertFails(doc.update({ likesCount: 4 }));
    await assertFails(doc.update({ caption: "modificado" }));
  });

  test("borrar post: solo el autor", async () => {
    await seedUser("alice");
    await seedPost("p1");
    const alice = env.authenticatedContext("alice").firestore();
    const bob = env.authenticatedContext("bob").firestore();
    await assertFails(bob.doc("posts/p1").delete());
    await assertSucceeds(alice.doc("posts/p1").delete());
  });

  test("los likes (collection group) solo se leen autenticado", async () => {
    await seedUser("alice");
    await seedPost("p1");
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("posts/p1/likes/bob").set({ userId: "bob", timestamp: 1 });
    });
    const bob = env.authenticatedContext("bob").firestore();
    await assertSucceeds(bob.collectionGroup("likes").where("userId", "==", "bob").get());
    await assertFails(env.unauthenticatedContext().firestore().collectionGroup("likes").get());
  });
});

// ---------------------------------------------------------------------------
// Posts: audiencia por publicación (visibility: public | friends)
// ---------------------------------------------------------------------------

describe("posts: audiencia por publicación", () => {
  test("post 'friends': un no-seguidor no puede leerlo por get", async () => {
    await seedUser("alice");
    await seedPost("pFriends", { visibility: "friends", caption: "#vivid solo amigos" });
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(carol.doc("posts/pFriends").get());
  });

  test("post 'friends': el autor y un seguidor sí pueden", async () => {
    await seedUser("alice");
    await seedPost("pFriends", { visibility: "friends" });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("users/alice/followers/bob").set({ timestamp: 1 });
    });
    const alice = env.authenticatedContext("alice").firestore();
    const bob = env.authenticatedContext("bob").firestore();
    await assertSucceeds(alice.doc("posts/pFriends").get());
    await assertSucceeds(bob.doc("posts/pFriends").get());
  });

  test("post público (y legacy sin el campo) se lee igual que antes", async () => {
    await seedUser("alice");
    await seedPost("pPublic", { visibility: "public" });
    await seedPost("pLegacy");
    const carol = env.authenticatedContext("carol").firestore();
    await assertSucceeds(carol.doc("posts/pPublic").get());
    await assertSucceeds(carol.doc("posts/pLegacy").get());
  });

  test("la query pública (list) no cambia: el filtro 'friends' es del cliente", async () => {
    await seedUser("alice");
    await seedPost("p1", { visibility: "public" });
    await seedPost("p2", { visibility: "friends" });
    const carol = env.authenticatedContext("carol").firestore();
    // where() (SDK cliente), no whereEqualTo() (eso es de firebase-admin).
    await assertSucceeds(
      carol.collection("posts").where("isPrivate", "==", false).get()
    );
  });

  test("los comentarios de un post 'friends' tampoco se leen sin seguir", async () => {
    await seedUser("alice");
    await seedPost("pFriends", { visibility: "friends" });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("posts/pFriends/comments/c1").set({
        userId: "alice", text: "hola", likesCount: 0
      });
    });
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(carol.doc("posts/pFriends/comments/c1").get());
  });
});

// ---------------------------------------------------------------------------
// Stories y Reels: contadores y privacidad
// ---------------------------------------------------------------------------

describe("stories y reels", () => {
  test("story: viewersCount solo incrementa de a 1", async () => {
    await seedUser("alice");
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("stories/s1").set({
        userId: "alice", isPrivate: false, viewersCount: 3
      });
    });
    const bob = env.authenticatedContext("bob").firestore();
    const doc = bob.doc("stories/s1");
    await assertSucceeds(doc.update({ viewersCount: 4 }));  // 3 → 4: +1 ok
    await assertFails(doc.update({ viewersCount: 9 }));     // 4 → 9: salto prohibido
  });

  test("reel: likes/comments solo de a 1 y métricas que solo crecen", async () => {
    await seedUser("alice");
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("reels/r1").set({
        userId: "alice", isPrivate: false, likes: 2, comments: 1,
        viewsCount: 10, completedViews: 5, totalWatchTimeSec: 60
      });
    });
    const bob = env.authenticatedContext("bob").firestore();
    const doc = bob.doc("reels/r1");

    await assertSucceeds(doc.update({ likes: 3 }));
    await assertFails(doc.update({ likes: 5 }));
    await assertSucceeds(doc.update({ viewsCount: 11 }));
    await assertFails(doc.update({ viewsCount: 20 }));
    // El watch time solo puede crecer (no retroceder).
    await assertSucceeds(doc.update({ totalWatchTimeSec: 90 }));
    await assertFails(doc.update({ totalWatchTimeSec: 30 }));
  });

  test("reel privado: no seguidor no puede leer ni interactuar", async () => {
    await seedUser("alice", { isPrivate: true });
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("reels/r1").set({
        userId: "alice", isPrivate: true, likes: 0, comments: 0
      });
    });
    const bob = env.authenticatedContext("bob").firestore();
    await assertFails(bob.doc("reels/r1").get());
    await assertFails(bob.doc("reels/r1").update({ likes: 1 }));
  });
});

// ---------------------------------------------------------------------------
// Chats
// ---------------------------------------------------------------------------

describe("chats", () => {
  test("crear chat: exactamente 2 participantes e incluirse", async () => {
    await seedUser("alice");
    await seedUser("bob");
    const alice = env.authenticatedContext("alice").firestore();

    await assertSucceeds(alice.doc("chats/ab").set({
      participants: ["alice", "bob"], createdAt: 1
    }));
    // 3 participantes → no.
    await assertFails(alice.doc("chats/abc").set({
      participants: ["alice", "bob", "carol"], createdAt: 1
    }));
    // Crear un chat en el que no participas → no.
    await assertFails(alice.doc("chats/bc").set({
      participants: ["bob", "carol"], createdAt: 1
    }));
  });

  test("solo los participantes leen el chat", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("chats/ab").set({ participants: ["alice", "bob"] });
    });
    const alice = env.authenticatedContext("alice").firestore();
    const carol = env.authenticatedContext("carol").firestore();
    await assertSucceeds(alice.doc("chats/ab").get());
    await assertFails(carol.doc("chats/ab").get());
  });

  test("listar chats: whereArrayContains solo del propio uid", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("chats/ab").set({ participants: ["alice", "bob"] });
      await ctx.firestore().doc("chats/ac").set({ participants: ["alice", "carol"] });
    });
    const alice = env.authenticatedContext("alice").firestore();
    const carol = env.authenticatedContext("carol").firestore();
    const snap = await assertSucceeds(
      alice.collection("chats").where("participants", "array-contains", "alice").get()
    );
    assert.equal(snap.size, 2);
    // Consultar los chats de otra persona está prohibido aunque el filtro
    // coincida con documentos reales.
    await assertFails(
      carol.collection("chats").where("participants", "array-contains", "alice").get()
    );
  });

  test("un participante no puede alterar la lista de participantes", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("chats/ab").set({ participants: ["alice", "bob"] });
    });
    const alice = env.authenticatedContext("alice").firestore();
    const doc = alice.doc("chats/ab");

    await assertFails(doc.update({ participants: ["alice", "bob", "carol"] }));
    await assertFails(doc.update({ participants: ["alice"] }));
    // Metadata sí puede cambiar (la lista se mantiene).
    await assertSucceeds(doc.update({ lastMessage: "hola", participants: ["alice", "bob"] }));
  });

  test("mensajes: solo el emisor, receptor participante y tipo permitido", async () => {
    await seedUser("alice");
    await seedUser("bob");
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("chats/ab").set({ participants: ["alice", "bob"] });
    });
    const alice = env.authenticatedContext("alice").firestore();
    const bob = env.authenticatedContext("bob").firestore();
    const carol = env.authenticatedContext("carol").firestore();

    const base = { senderId: "alice", receiverId: "bob", type: "text", text: "hola" };
    await assertSucceeds(alice.doc("chats/ab/messages/m1").set(base));
    // Emisor distinto del autenticado → no.
    await assertFails(alice.doc("chats/ab/messages/m2").set({ ...base, senderId: "bob" }));
    // Tipo fuera de la allowlist → no.
    await assertFails(alice.doc("chats/ab/messages/m3").set({ ...base, type: "malware" }));
    // No participante → no puede escribir.
    await assertFails(carol.doc("chats/ab/messages/m4").set({ ...base, senderId: "carol" }));
    // El receptor puede escribir su propio mensaje (emisor correcto).
    await assertSucceeds(bob.doc("chats/ab/messages/m5").set({
      senderId: "bob", receiverId: "alice", type: "text", text: "responde"
    }));
  });

  test("mensajes: update solo de acuses/reacción, delete solo del emisor", async () => {
    await seedUser("alice");
    await seedUser("bob");
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc("chats/ab").set({ participants: ["alice", "bob"] });
      await ctx.firestore().doc("chats/ab/messages/m1").set({
        senderId: "alice", receiverId: "bob", type: "text", text: "hola",
        isRead: false, isDelivered: false
      });
    });
    const alice = env.authenticatedContext("alice").firestore();
    const bob = env.authenticatedContext("bob").firestore();
    const doc = alice.doc("chats/ab/messages/m1");

    // Acuses y reacción → sí.
    await assertSucceeds(alice.doc("chats/ab/messages/m1").update({ isRead: true }));
    await assertSucceeds(bob.doc("chats/ab/messages/m1").update({ reaction: "❤️" }));
    // Reescribir el texto SIN lastEditedAt → no.
    await assertFails(doc.update({ text: "reescrito" }));
    // El receptor no puede editar el texto aunque mande lastEditedAt.
    await assertFails(bob.doc("chats/ab/messages/m1").update({
      text: "hack", lastEditedAt: Date.now()
    }));
    // El emisor sí puede editar texto + lastEditedAt.
    await assertSucceeds(doc.update({ text: "editado", lastEditedAt: 1 }));
    // Borrar: solo el emisor.
    await assertFails(bob.doc("chats/ab/messages/m1").delete());
    await assertSucceeds(alice.doc("chats/ab/messages/m1").delete());
  });

  // Flujo real de UNA CONVERSACIÓN NUEVA (cuenta que nunca chateó con
  // la otra): ChatRepository hace transaction.get(chats/{chatId}) ANTES
  // de crear el documento. Si la regla de lectura no tolerara el
  // documento inexistente, ese get daría PERMISSION_DENIED y el primer
  // mensaje de cualquier chat nuevo jamás se enviaría.
  test("conversación nueva: el participante puede leer un chat que no existe", async () => {
    await seedUser("alice");
    await seedUser("bob");
    const alice = env.authenticatedContext("alice").firestore();

    const snap = await assertSucceeds(alice.doc("chats/alice_bob").get());
    assert.equal(snap.exists, false);

    // El otro participante (uid al final del id) también puede sondear.
    const bob = env.authenticatedContext("bob").firestore();
    const bobSnap = await assertSucceeds(bob.doc("chats/alice_bob").get());
    assert.equal(bobSnap.exists, false);

    // Un extraño NO puede sondear chats ajenos (el id no incluye su uid).
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(carol.doc("chats/alice_bob").get());

    // Ni siquiera el propio usuario puede sondear un id en el que no sale.
    await assertFails(alice.doc("chats/bob_carol").get());

    // Sin sesión tampoco.
    await assertFails(env.unauthenticatedContext().firestore().doc("chats/alice_bob").get());
  });

  test("conversación nueva: chat + primer mensaje en la misma transacción", async () => {
    await seedUser("alice");
    await seedUser("bob");
    const alice = env.authenticatedContext("alice").firestore();

    // Reproduce persistOutgoingMessage/ensureChatExists: get → set(chat) →
    // set(mensaje). La regla del mensaje usa getAfter(chats/{chatId}), que
    // debe ver el chat creado dentro de la MISMA transacción.
    await assertSucceeds(alice.runTransaction(async (tx) => {
      const chatRef = alice.doc("chats/alice_bob");
      const chatSnap = await tx.get(chatRef);
      if (!chatSnap.exists) {
        tx.set(chatRef, {
          participants: ["alice", "bob"],
          createdAt: 1,
          updatedAt: 1,
          unreadCounts: { alice: 0, bob: 1 }
        });
      }
      tx.set(alice.doc("chats/alice_bob/messages/m1"), {
        senderId: "alice",
        receiverId: "bob",
        type: "text",
        text: "hola",
        timestamp: 1,
        isRead: false,
        isDelivered: false
      });
    }));

    // Un extraño no puede leer el chat recién creado ni escuchar mensajes.
    const carol = env.authenticatedContext("carol").firestore();
    await assertFails(carol.doc("chats/alice_bob").get());
    await assertFails(
      carol.collection("chats/alice_bob/messages").orderBy("timestamp", "asc").get()
    );
  });

  test("conversación nueva: preview posterior del remitente (update del chat)", async () => {
    await seedUser("alice");
    await seedUser("bob");
    const alice = env.authenticatedContext("alice").firestore();

    await assertSucceeds(alice.doc("chats/alice_bob").set({
      participants: ["alice", "bob"], createdAt: 1
    }));
    // persistOutgoingMessage actualiza el preview con field paths.
    await assertSucceeds(alice.doc("chats/alice_bob").update({
      lastMessage: "hola",
      "unreadCounts.alice": 0,
      "unreadCounts.bob": 1,
      updatedAt: 2
    }));
  });
});
