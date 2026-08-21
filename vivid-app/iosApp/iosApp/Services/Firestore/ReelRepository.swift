import FirebaseAuth
import FirebaseFirestore

final class ReelRepository {
    private let db: Firestore
    private let auth: Auth

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) { self.db = db; self.auth = auth }

    @discardableResult
    func observePublicReels(limit: Int = 50, onChange: @escaping (Result<[FirestoreReel], Error>) -> Void) -> ListenerRegistration {
        db.collection("reels").whereField("isPrivate", isEqualTo: false).order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in self.reels(snapshot, error, onChange) }
    }

    func fetchPublicPage(before timestamp: Int64? = nil, limit: Int = 20) async throws -> [FirestoreReel] {
        var query: Query = db.collection("reels").whereField("isPrivate", isEqualTo: false).order(by: "timestamp", descending: true)
        if let timestamp { query = query.whereField("timestamp", isLessThan: timestamp) }
        let snapshot = try await FirebaseAsync.value { completion in query.limit(to: limit).getDocuments(completion: completion) }
        return snapshot.documents.compactMap { FirestoreReel(document: $0) }
    }

    func isLiked(reelId: String) async throws -> Bool {
        guard let uid = auth.currentUser?.uid else { return false }
        return try await FirebaseAsync.value { completion in db.collection("reels").document(reelId).collection("likes").document(uid).getDocument(completion: completion) }.exists
    }

    @discardableResult
    func observeReels(byUserId userId: String, limit: Int = 50, onChange: @escaping (Result<[FirestoreReel], Error>) -> Void) -> ListenerRegistration {
        db.collection("reels").whereField("userId", isEqualTo: userId).order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in self.reels(snapshot, error, onChange) }
    }

    func fetchReel(id: String) async throws -> FirestoreReel? {
        let snapshot = try await FirebaseAsync.value { completion in db.collection("reels").document(id).getDocument(completion: completion) }
        return FirestoreReel(document: snapshot)
    }

    func createReel(video: UploadedMedia, user: VividUser, caption: String, thumbnailURL: String = "") async throws -> String {
        guard let uid = auth.currentUser?.uid, uid == user.id else { throw FirebaseRepositoryError.unauthenticated }
        let ref = db.collection("reels").document()
        try await ref.setDataAsync([
            "userId": uid, "username": user.username, "userAvatar": user.avatarURL, "videoUrl": video.url,
            "thumbnailUrl": thumbnailURL, "storageKey": video.storageKey, "caption": caption, "likes": 0,
            "comments": 0, "timestamp": Int64(Date().timeIntervalSince1970 * 1_000), "isPrivate": user.isPrivate,
            "viewsCount": 0, "completedViews": 0, "totalWatchTimeSec": 0
        ])
        return ref.documentID
    }

    @discardableResult
    func observeComments(reelId: String, onChange: @escaping (Result<[FirestoreComment], Error>) -> Void) -> ListenerRegistration {
        db.collection("reels").document(reelId).collection("comments").order(by: "timestamp")
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap { FirestoreComment(document: $0) } ?? []))
            }
    }

    func addComment(reelId: String, text: String, username: String, avatarURL: String = "") async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { throw FirebaseRepositoryError.invalidInput("El comentario está vacío.") }
        let reel = db.collection("reels").document(reelId)
        let comment = reel.collection("comments").document()
        let batch = db.batch()
        batch.setData(["userId": uid, "username": username, "text": body, "avatarUrl": avatarURL, "timestamp": Int64(Date().timeIntervalSince1970 * 1_000), "likesCount": 0], forDocument: comment)
        batch.updateData(["comments": FieldValue.increment(Int64(1))], forDocument: reel)
        try await batch.commitAsync()
    }

    func toggleLike(reelId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let reel = db.collection("reels").document(reelId)
        let like = reel.collection("likes").document(uid)
        try await FirebaseAsync.write { completion in
            db.runTransaction({ transaction, _ in
                let liked = (try? transaction.getDocument(like).exists) ?? false
                if liked { transaction.deleteDocument(like); transaction.updateData(["likes": FieldValue.increment(Int64(-1))], forDocument: reel) }
                else { transaction.setData(["userId": uid], forDocument: like); transaction.updateData(["likes": FieldValue.increment(Int64(1))], forDocument: reel) }
                return nil
            }, completion: { _, error in completion(error) })
        }
    }

    private func reels(_ snapshot: QuerySnapshot?, _ error: Error?, _ onChange: (Result<[FirestoreReel], Error>) -> Void) {
        if let error { onChange(.failure(error)); return }
        onChange(.success(snapshot?.documents.compactMap { FirestoreReel(document: $0) } ?? []))
    }
}
