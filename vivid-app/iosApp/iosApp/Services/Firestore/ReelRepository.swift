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

    @discardableResult
    func observeReels(byUserId userId: String, limit: Int = 50, onChange: @escaping (Result<[FirestoreReel], Error>) -> Void) -> ListenerRegistration {
        db.collection("reels").whereField("userId", isEqualTo: userId).order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in self.reels(snapshot, error, onChange) }
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
