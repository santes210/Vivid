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
    // Reescribir el texto del mensaje → no.
    await assertFails(doc.update({ text: "reescrito" }));
    // Borrar: solo el emisor.
    await assertFails(bob.doc("chats/ab/messages/m1").delete());
    await assertSucceeds(alice.doc("chats/ab/messages/m1").delete());
  });
});
