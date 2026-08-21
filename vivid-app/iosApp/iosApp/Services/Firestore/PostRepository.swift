import FirebaseAuth
import FirebaseFirestore

final class PostRepository {
    private let db: Firestore
    private let auth: Auth

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) {
        self.db = db
        self.auth = auth
    }

    @discardableResult
    func observePublicFeed(limit: Int = 50, onChange: @escaping (Result<[FirestorePost], Error>) -> Void) -> ListenerRegistration {
        db.collection("posts").whereField("isPrivate", isEqualTo: false).order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in self.posts(snapshot, error, onChange) }
    }

    @discardableResult
    func observePosts(byUserId userId: String, limit: Int = 50, onChange: @escaping (Result<[FirestorePost], Error>) -> Void) -> ListenerRegistration {
        db.collection("posts").whereField("userId", isEqualTo: userId).order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in self.posts(snapshot, error, onChange) }
    }

    func fetchPost(id: String) async throws -> FirestorePost? {
        let snap = try await FirebaseAsync.value { completion in db.collection("posts").document(id).getDocument(completion: completion) }
        return FirestorePost(document: snap)
    }

    func observeFollowingFeed(userIds: [String], limit: Int = 50, onChange: @escaping (Result<[FirestorePost], Error>) -> Void) -> ListenerRegistration? {
        let ids = Array(Set(userIds)).prefix(10)
        guard !ids.isEmpty else { onChange(.success([])); return nil }
        return db.collection("posts").whereField("userId", in: Array(ids)).order(by: "timestamp", descending: true).limit(to: limit)
            .addSnapshotListener { snapshot, error in self.posts(snapshot, error, onChange) }
    }

    @discardableResult
    func observePost(id: String, onChange: @escaping (Result<FirestorePost?, Error>) -> Void) -> ListenerRegistration {
        db.collection("posts").document(id).addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(snapshot.flatMap { FirestorePost(document: $0) }))
        }
    }

    @discardableResult
    func observeComments(postId: String, onChange: @escaping (Result<[FirestoreComment], Error>) -> Void) -> ListenerRegistration {
        db.collection("posts").document(postId).collection("comments").order(by: "timestamp")
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap { FirestoreComment(document: $0) } ?? []))
            }
    }

    func toggleLike(postId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let post = db.collection("posts").document(postId)
        let like = post.collection("likes").document(uid)
        try await FirebaseAsync.write { completion in
            db.runTransaction({ transaction, errorPointer in
                let liked = (try? transaction.getDocument(like).exists) ?? false
                if liked { transaction.deleteDocument(like); transaction.updateData(["likesCount": FieldValue.increment(Int64(-1))], forDocument: post) }
                else { transaction.setData(["userId": uid, "timestamp": Int64(Date().timeIntervalSince1970 * 1_000)], forDocument: like); transaction.updateData(["likesCount": FieldValue.increment(Int64(1))], forDocument: post) }
                return nil
            }, completion: { _, error in completion(error) })
        }
    }

    func addComment(postId: String, text: String, username: String, avatarURL: String = "") async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { throw FirebaseRepositoryError.invalidInput("El comentario está vacío.") }
        let post = db.collection("posts").document(postId)
        let comment = post.collection("comments").document()
        let batch = db.batch()
        batch.setData(["userId": uid, "username": username, "text": body, "avatarUrl": avatarURL, "timestamp": Int64(Date().timeIntervalSince1970 * 1_000), "likesCount": 0], forDocument: comment)
        batch.updateData(["commentsCount": FieldValue.increment(Int64(1))], forDocument: post)
        try await batch.commitAsync()
    }

    func createPost(media: UploadedMedia, isVideo: Bool, caption: String, user: VividUser, thumbnailURL: String = "") async throws -> String {
        guard let uid = auth.currentUser?.uid, uid == user.id else { throw FirebaseRepositoryError.unauthenticated }
        let ref = db.collection("posts").document()
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let hashtags = caption.split(whereSeparator: { $0.isWhitespace }).filter { $0.hasPrefix("#") }.map { String($0.dropFirst()).lowercased() }
        try await ref.setDataAsync([
            "userId": uid, "username": user.username, "userProfilePicture": user.avatarURL,
            "userProfilePictureBase64": user.avatarBase64, "imageUrl": isVideo ? "" : media.url,
            "videoUrl": isVideo ? media.url : "", "thumbnailUrl": thumbnailURL,
            "storageKey": media.storageKey, "isVideo": isVideo, "caption": caption,
            "hashtags": hashtags, "likesCount": 0, "commentsCount": 0, "timestamp": now,
            "isPrivate": user.isPrivate
        ])
        return ref.documentID
    }

    private func posts(_ snapshot: QuerySnapshot?, _ error: Error?, _ onChange: (Result<[FirestorePost], Error>) -> Void) {
        if let error { onChange(.failure(error)); return }
        onChange(.success(snapshot?.documents.compactMap { FirestorePost(document: $0) } ?? []))
    }
}
