import FirebaseAuth
import FirebaseFirestore

final class ChatRepository {
    private let db: Firestore
    private let auth: Auth

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) { self.db = db; self.auth = auth }

    @discardableResult
    func observeChats(forUserId userId: String, limit: Int = 100, onChange: @escaping (Result<[FirestoreChat], Error>) -> Void) -> ListenerRegistration {
        db.collection("chats").whereField("participants", arrayContains: userId).order(by: "lastTimestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap { FirestoreChat(document: $0) } ?? []))
            }
    }

    @discardableResult
    func observeMessages(chatId: String, limit: Int = 50, onChange: @escaping (Result<[FirestoreMessage], Error>) -> Void) -> ListenerRegistration {
        // Se piden los más recientes en orden descendente y se invierten para UI.
        db.collection("chats").document(chatId).collection("messages").order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                let messages = snapshot?.documents.compactMap { FirestoreMessage(document: $0) } ?? []
                onChange(.success(Array(messages.reversed())))
            }
    }

    func fetchOlderMessages(chatId: String, before timestamp: Int64, limit: Int = 50) async throws -> [FirestoreMessage] {
        let query = db.collection("chats").document(chatId).collection("messages")
            .order(by: "timestamp", descending: true).whereField("timestamp", isLessThan: timestamp).limit(to: limit)
        let snapshot = try await FirebaseAsync.value { completion in query.getDocuments(completion: completion) }
        return Array(snapshot.documents.compactMap { FirestoreMessage(document: $0) }.reversed())
    }

    func fetchChat(id: String) async throws -> FirestoreChat? {
        let snapshot = try await FirebaseAsync.value { completion in db.collection("chats").document(id).getDocument(completion: completion) }
        return FirestoreChat(document: snapshot)
    }

    static func id(firstUserId: String, secondUserId: String) -> String { [firstUserId, secondUserId].sorted().joined(separator: "_") }

    func sendMessage(
        receiverId: String,
        receiverName: String,
        receiverAvatar: String = "",
        text: String,
        senderName: String,
        senderAvatar: String = "",
        replyToStoryId: String = "",
        image: UploadedMedia? = nil,
        voice: UploadedMedia? = nil,
        voiceDurationMs: Int64 = 0
    ) async throws -> String {
        guard let senderId = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !receiverId.isEmpty, receiverId != senderId else { throw FirebaseRepositoryError.invalidInput("No se puede enviar este mensaje.") }
        let type: String
        if image != nil { type = "image" }
        else if voice != nil { type = "voice" }
        else if !replyToStoryId.isEmpty { type = "story_reply" }
        else { type = "text" }
        if type == "text" && body.isEmpty { throw FirebaseRepositoryError.invalidInput("No se puede enviar este mensaje.") }
        let chatId = Self.id(firstUserId: senderId, secondUserId: receiverId)
        let chat = db.collection("chats").document(chatId)
        let message = chat.collection("messages").document()
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let preview: String
        switch type {
        case "image": preview = "📷 Foto"
        case "voice": preview = "🎤 Nota de voz"
        case "story_reply": preview = "↳ Respondió a tu story"
        default: preview = body
        }
        var payload: [String: Any] = ["senderId": senderId, "receiverId": receiverId, "text": body, "timestamp": now, "type": type, "isRead": false, "isDelivered": false, "replyToStoryId": replyToStoryId]
        if let image {
            payload["imageUrl"] = image.url
            payload["imageKey"] = image.storageKey
        }
        if let voice {
            payload["voiceUrl"] = voice.url
            payload["voiceKey"] = voice.storageKey
            payload["voiceDurationMs"] = voiceDurationMs
        }
        let batch = db.batch()
        batch.setData([
            "participants": [senderId, receiverId], "participantNames": [senderId: senderName, receiverId: receiverName],
            "participantAvatars": [senderId: senderAvatar, receiverId: receiverAvatar], "lastMessage": preview,
            "lastMessageType": type, "lastMessageSenderId": senderId, "lastTimestamp": now,
            "updatedAt": now, "createdAt": now, "unreadCounts": [senderId: 0, receiverId: 1]
        ], forDocument: chat, merge: true)
        batch.setData(payload, forDocument: message)
        try await batch.commitAsync()
        return chatId
    }

    func markMessagesDelivered(chatId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let messages = try await FirebaseAsync.value { completion in
            db.collection("chats").document(chatId).collection("messages")
                .whereField("receiverId", isEqualTo: uid).whereField("isDelivered", isEqualTo: false).limit(to: 450).getDocuments(completion: completion)
        }
        guard !messages.documents.isEmpty else { return }
        let batch = db.batch(); messages.documents.forEach { batch.updateData(["isDelivered": true], forDocument: $0.reference) }
        try await batch.commitAsync()
    }

    func markMessagesRead(chatId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let chat = db.collection("chats").document(chatId)
        let messages = try await FirebaseAsync.value { completion in chat.collection("messages").whereField("receiverId", isEqualTo: uid).whereField("isRead", isEqualTo: false).limit(to: 450).getDocuments(completion: completion) }
        let batch = db.batch()
        messages.documents.forEach { batch.updateData(["isRead": true, "isDelivered": true], forDocument: $0.reference) }
        batch.setData(["unreadCounts": [uid: 0]], forDocument: chat, merge: true)
        try await batch.commitAsync()
    }

    func react(chatId: String, messageId: String, reaction: String) async throws {
        guard auth.currentUser != nil else { throw FirebaseRepositoryError.unauthenticated }
        try await db.collection("chats").document(chatId).collection("messages").document(messageId)
            .updateDataAsync(["reaction": reaction])
    }

    func editMessage(chatId: String, messageId: String, text: String) async throws {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { throw FirebaseRepositoryError.invalidInput("El mensaje está vacío.") }
        try await db.collection("chats").document(chatId).collection("messages").document(messageId)
            .updateDataAsync(["text": body, "lastEditedAt": Int64(Date().timeIntervalSince1970 * 1_000)])
    }

    func deleteMessage(chatId: String, messageId: String) async throws {
        try await db.collection("chats").document(chatId).collection("messages").document(messageId).deleteAsync()
    }
}
