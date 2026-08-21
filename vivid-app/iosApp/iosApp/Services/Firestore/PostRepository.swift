import FirebaseFirestore

/// Observa posts sin polling. Las consultas públicas incluyen `isPrivate == false`
/// para que Firestore pueda autorizar la query con las reglas de Vivid.
final class PostRepository {
    private let db: Firestore

    init(db: Firestore = Firestore.firestore()) { self.db = db }

    @discardableResult
    func observePublicFeed(
        limit: Int = 50,
        onChange: @escaping (Result<[FirestorePost], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("posts")
            .whereField("isPrivate", isEqualTo: false)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestorePost.init(document:)) ?? []))
            }
    }

    @discardableResult
    func observePosts(
        byUserId userId: String,
        limit: Int = 50,
        onChange: @escaping (Result<[FirestorePost], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("posts")
            .whereField("userId", isEqualTo: userId)
            .order(by: "timestamp", descending: true)
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestorePost.init(document:)) ?? []))
            }
    }

    @discardableResult
    func observePost(
        id: String,
        onChange: @escaping (Result<FirestorePost?, Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("posts").document(id).addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(snapshot.flatMap(FirestorePost.init(document:))))
        }
    }
}
