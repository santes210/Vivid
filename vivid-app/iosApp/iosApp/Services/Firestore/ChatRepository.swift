import FirebaseFirestore

/// Repositorio de conversaciones y mensajes. Los listeners corresponden a
/// `/chats/{chatId}` y `/chats/{chatId}/messages` del cliente Android.
final class ChatRepository {
    private let db: Firestore

    init(db: Firestore = Firestore.firestore()) { self.db = db }

    @discardableResult
    func observeChats(
        forUserId userId: String,
        limit: Int = 100,
        onChange: @escaping (Result<[FirestoreChat], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("chats")
            .whereField("participants", arrayContains: userId)
            .order(by: "lastTimestamp", descending: true)
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestoreChat.init(document:)) ?? []))
            }
    }

    @discardableResult
    func observeMessages(
        chatId: String,
        limit: Int = 300,
        onChange: @escaping (Result<[FirestoreMessage], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("chats").document(chatId).collection("messages")
            .order(by: "timestamp", descending: false)
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestoreMessage.init(document:)) ?? []))
            }
    }
}
