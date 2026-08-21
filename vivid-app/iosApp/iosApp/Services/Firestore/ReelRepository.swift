import FirebaseFirestore

/// Fuente en tiempo real de `/reels`, con las mismas claves que Android:
/// `likes`, `comments`, `timestamp`, `isPrivate` y URLs de vídeo/thumbnail.
final class ReelRepository {
    private let db: Firestore

    init(db: Firestore = Firestore.firestore()) { self.db = db }

    @discardableResult
    func observePublicReels(
        limit: Int = 50,
        onChange: @escaping (Result<[FirestoreReel], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("reels")
            .whereField("isPrivate", isEqualTo: false)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestoreReel.init(document:)) ?? []))
            }
    }

    @discardableResult
    func observeReels(
        byUserId userId: String,
        limit: Int = 50,
        onChange: @escaping (Result<[FirestoreReel], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("reels")
            .whereField("userId", isEqualTo: userId)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestoreReel.init(document:)) ?? []))
            }
    }
}
