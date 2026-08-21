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
    func observeMessages(chatId: String, limit: Int = 300, onChange: @escaping (Result<[FirestoreMessage], Error>) -> Void) -> ListenerRegistration {
        db.collection("chats").document(chatId).collection("messages").order(by: "timestamp").limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap { FirestoreMessage(document: $0) } ?? []))
            }
    }

    static func id(firstUserId: String, secondUserId: String) -> String { [firstUserId, secondUserId].sorted().joined(separator: "_") }

    func sendMessage(receiverId: String, receiverName: String, receiverAvatar: String = "", text: String, senderName: String, senderAvatar: String = "", replyToStoryId: String = "") async throws -> String {
        guard let senderId = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty, !receiverId.isEmpty, receiverId != senderId else { throw FirebaseRepositoryError.invalidInput("No se puede enviar este mensaje.") }
        let chatId = Self.id(firstUserId: senderId, secondUserId: receiverId)
        let chat = db.collection("chats").document(chatId)
        let message = chat.collection("messages").document()
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let type = replyToStoryId.isEmpty ? "text" : "story_reply"
        let preview = type == "text" ? body : "↳ Respondió a tu story"
        let batch = db.batch()
        batch.setData([
            "participants": [senderId, receiverId], "participantNames": [senderId: senderName, receiverId: receiverName],
            "participantAvatars": [senderId: senderAvatar, receiverId: receiverAvatar], "lastMessage": preview,
            "lastMessageType": type, "lastMessageSenderId": senderId, "lastTimestamp": now,
            "updatedAt": now, "createdAt": now, "unreadCounts": [senderId: 0, receiverId: 1]
        ], forDocument: chat, merge: true)
        batch.setData(["senderId": senderId, "receiverId": receiverId, "text": body, "timestamp": now, "type": type, "isRead": false, "isDelivered": false, "replyToStoryId": replyToStoryId], forDocument: message)
        try await batch.commitAsync()
        return chatId
    }

    func markMessagesRead(chatId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let chat = db.collection("chats").document(chatId)
        // La lista visible ya está limitada; los mensajes históricos se actualizan
        // individualmente al abrirse en lotes de hasta 500.
        let messages = try await FirebaseAsync.value { completion in chat.collection("messages").whereField("receiverId", isEqualTo: uid).whereField("isRead", isEqualTo: false).limit(to: 450).getDocuments(completion: completion) }
        let batch = db.batch()
        messages.documents.forEach { batch.updateData(["isRead": true], forDocument: $0.reference) }
        batch.setData(["unreadCounts": [uid: 0]], forDocument: chat, merge: true)
        try await batch.commitAsync()
    }
}
