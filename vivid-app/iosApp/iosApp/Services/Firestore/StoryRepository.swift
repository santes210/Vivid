import Foundation
import FirebaseAuth
import FirebaseFirestore

final class StoryRepository {
    private let db: Firestore
    private let auth: Auth
    private let dayMilliseconds: Int64 = 24 * 60 * 60 * 1_000

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) { self.db = db; self.auth = auth }

    @discardableResult
    func observePublicActiveStories(now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000), onChange: @escaping (Result<[FirestoreStory], Error>) -> Void) -> ListenerRegistration {
        db.collection("stories").whereField("isPrivate", isEqualTo: false).whereField("expiresAt", isGreaterThan: now).order(by: "expiresAt")
            .addSnapshotListener { snapshot, error in self.stories(snapshot, error, onChange) }
    }

    @discardableResult
    func observeActiveStories(byUserId userId: String, now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000), onChange: @escaping (Result<[FirestoreStory], Error>) -> Void) -> ListenerRegistration {
        db.collection("stories").whereField("userId", isEqualTo: userId).whereField("expiresAt", isGreaterThan: now).order(by: "expiresAt")
            .addSnapshotListener { snapshot, error in self.stories(snapshot, error, onChange) }
    }

    func createStory(media: UploadedMedia, isVideo: Bool, caption: String, user: VividUser, thumbnailURL: String = "") async throws -> String {
        guard let uid = auth.currentUser?.uid, uid == user.id else { throw FirebaseRepositoryError.unauthenticated }
        let ref = db.collection("stories").document()
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        try await ref.setDataAsync([
            "userId": uid, "username": user.username, "avatarUrl": user.avatarURL, "avatarBase64": user.avatarBase64,
            "userAvatar": user.avatarURL, "mediaUrl": isVideo ? "" : media.url, "videoUrl": isVideo ? media.url : "",
            "thumbnailUrl": thumbnailURL.isEmpty ? media.url : thumbnailURL, "storageKey": media.storageKey,
            "type": isVideo ? "video" : "photo", "caption": caption, "createdAt": now, "expiresAt": now + dayMilliseconds,
            "isPrivate": user.isPrivate, "viewersCount": 0
        ])
        return ref.documentID
    }

    func markViewed(storyId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let story = db.collection("stories").document(storyId)
        let view = story.collection("viewers").document(uid)
        try await FirebaseAsync.write { completion in
            db.runTransaction({ transaction, _ in
                let exists = (try? transaction.getDocument(view).exists) ?? false
                guard !exists else { return nil }
                transaction.setData(["uid": uid, "viewedAt": Int64(Date().timeIntervalSince1970 * 1_000)], forDocument: view)
                transaction.updateData(["viewersCount": FieldValue.increment(Int64(1))], forDocument: story)
                return nil
            }, completion: { _, error in completion(error) })
        }
    }

    private func stories(_ snapshot: QuerySnapshot?, _ error: Error?, _ onChange: (Result<[FirestoreStory], Error>) -> Void) {
        if let error { onChange(.failure(error)); return }
        onChange(.success(snapshot?.documents.compactMap { FirestoreStory(document: $0) } ?? []))
    }
}
