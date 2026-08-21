import Foundation
import FirebaseAuth
import FirebaseFirestore

struct StoryViewer: Identifiable {
    let id: String
    let username: String
    let avatarURL: String
    let viewedAt: Int64
}

final class StoryRepository {
    private let db: Firestore
    private let auth: Auth
    private let dayMilliseconds: Int64 = 24 * 60 * 60 * 1_000

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) { self.db = db; self.auth = auth }

    @discardableResult
    func observePublicActiveStories(now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000), onChange: @escaping (Result<[FirestoreStory], Error>) -> Void) -> ListenerRegistration {
        db.collection("stories").whereField("isPrivate", isEqualTo: false).whereField("audience", isEqualTo: "public").whereField("expiresAt", isGreaterThan: now).order(by: "expiresAt")
            .addSnapshotListener { snapshot, error in self.stories(snapshot, error, onChange) }
    }

    func fetchCloseFriendsStories(ownerIds: [String], now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000)) async throws -> [FirestoreStory] {
        let ids = Array(Set(ownerIds))
        var result: [FirestoreStory] = []
        for offset in stride(from: 0, to: ids.count, by: 10) {
            let chunk = Array(ids[offset..<min(offset + 10, ids.count)])
            guard !chunk.isEmpty else { continue }
            let query = db.collection("stories").whereField("userId", in: chunk).whereField("audience", isEqualTo: "close_friends").whereField("expiresAt", isGreaterThan: now).order(by: "expiresAt")
            let snapshot = try await FirebaseAsync.value { completion in query.getDocuments(completion: completion) }
            result.append(contentsOf: snapshot.documents.compactMap { FirestoreStory(document: $0) })
        }
        return result
    }

    @discardableResult
    func observeActiveStories(byUserId userId: String, now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000), onChange: @escaping (Result<[FirestoreStory], Error>) -> Void) -> ListenerRegistration {
        db.collection("stories").whereField("userId", isEqualTo: userId).whereField("expiresAt", isGreaterThan: now).order(by: "expiresAt")
            .addSnapshotListener { snapshot, error in self.stories(snapshot, error, onChange) }
    }

    func createStory(media: UploadedMedia, isVideo: Bool, caption: String, user: VividUser, thumbnailURL: String = "", audience: String = "public") async throws -> String {
        guard let uid = auth.currentUser?.uid, uid == user.id else { throw FirebaseRepositoryError.unauthenticated }
        let ref = db.collection("stories").document()
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        try await ref.setDataAsync([
            "userId": uid, "username": user.username, "avatarUrl": user.avatarURL, "avatarBase64": user.avatarBase64,
            "userAvatar": user.avatarURL, "mediaUrl": isVideo ? "" : media.url, "videoUrl": isVideo ? media.url : "",
            "thumbnailUrl": thumbnailURL.isEmpty ? media.url : thumbnailURL, "storageKey": media.storageKey,
            "type": isVideo ? "video" : "photo", "caption": caption, "createdAt": now, "expiresAt": now + dayMilliseconds,
            "isPrivate": user.isPrivate, "audience": audience == "close_friends" ? "close_friends" : "public", "viewersCount": 0
        ])
        return ref.documentID
    }

    func markViewed(storyId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let story = db.collection("stories").document(storyId)
        let view = story.collection("viewers").document(uid)
        try await FirebaseAsync.write { completion in
            db.runTransaction({ transaction, _ in
                let storySnapshot = try? transaction.getDocument(story)
                guard storySnapshot?.data()?["userId"] as? String != uid else { return nil }
                let exists = (try? transaction.getDocument(view).exists) ?? false
                guard !exists else { return nil }
                transaction.setData(["uid": uid, "viewedAt": Int64(Date().timeIntervalSince1970 * 1_000)], forDocument: view)
                transaction.updateData(["viewersCount": FieldValue.increment(Int64(1))], forDocument: story)
                return nil
            }, completion: { _, error in completion(error) })
        }
    }

    func fetchViewers(storyId: String) async throws -> [StoryViewer] {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let story = try await FirebaseAsync.value { completion in db.collection("stories").document(storyId).getDocument(completion: completion) }
        guard story.data()?["userId"] as? String == uid else { throw FirebaseRepositoryError.invalidInput("Solo el autor puede ver esta lista.") }
        let snapshot = try await FirebaseAsync.value { completion in db.collection("stories").document(storyId).collection("viewers").order(by: "viewedAt", descending: true).getDocuments(completion: completion) }
        var result: [StoryViewer] = []
        for document in snapshot.documents {
            let viewerId = document.documentID
            let profile = try? await FirebaseAsync.value { completion in db.collection("users").document(viewerId).getDocument(completion: completion) }
            result.append(StoryViewer(id: viewerId, username: profile?.data()?["username"] as? String ?? "usuario", avatarURL: profile?.data()?["avatarUrl"] as? String ?? "", viewedAt: document.data()["viewedAt"] as? Int64 ?? 0))
        }
        return result
    }

    private func stories(_ snapshot: QuerySnapshot?, _ error: Error?, _ onChange: (Result<[FirestoreStory], Error>) -> Void) {
        if let error { onChange(.failure(error)); return }
        onChange(.success(snapshot?.documents.compactMap { FirestoreStory(document: $0) } ?? []))
    }
}
