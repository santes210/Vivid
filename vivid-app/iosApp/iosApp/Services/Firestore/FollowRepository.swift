import FirebaseAuth
import FirebaseFirestore

struct FollowRelationshipState: Equatable {
    var isFollowing = false
    var hasPendingRequest = false
    var isTargetPrivate = false
    var isBlocked = false
}

enum FollowActionResult {
    case followed, unfollowed, requested, requestCancelled
}

struct SocialUserPreview: Identifiable, Equatable {
    let id: String
    let username: String
    let displayName: String
    let avatarURL: String
}

final class FollowRepository {
    private let db: Firestore
    private let auth: Auth

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) {
        self.db = db
        self.auth = auth
    }

    private var currentUserId: String { auth.currentUser?.uid ?? "" }

    func getRelationshipState(targetUserId: String) async throws -> FollowRelationshipState {
        guard !currentUserId.isEmpty, !targetUserId.isEmpty, targetUserId != currentUserId else {
            return FollowRelationshipState()
        }
        let currentSnap = try await getDocument(db.collection("users").document(currentUserId))
        let targetSnap = try await getDocument(db.collection("users").document(targetUserId))
        let following = try await getDocument(db.collection("users").document(currentUserId).collection("following").document(targetUserId))
        let pending = try await getDocument(db.collection("users").document(targetUserId).collection("followRequests").document(currentUserId))
        let blocked = (currentSnap.data()?["blockedUsers"] as? [String] ?? []).contains(targetUserId)
        return FollowRelationshipState(
            isFollowing: following.exists,
            hasPendingRequest: pending.exists,
            isTargetPrivate: targetSnap.data()?["isPrivate"] as? Bool ?? false,
            isBlocked: blocked
        )
    }

    func toggleFollow(targetUserId: String) async throws -> FollowActionResult {
        let state = try await getRelationshipState(targetUserId: targetUserId)
        if state.isFollowing {
            try await unfollowUser(targetUserId: targetUserId)
            return .unfollowed
        }
        if state.hasPendingRequest {
            try await cancelFollowRequest(targetUserId: targetUserId)
            return .requestCancelled
        }
        if state.isTargetPrivate {
            try await sendFollowRequest(targetUserId: targetUserId)
            return .requested
        }
        try await followUser(targetUserId: targetUserId)
        return .followed
    }

    func followUser(targetUserId: String) async throws {
        guard !currentUserId.isEmpty, targetUserId != currentUserId else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let batch = db.batch()
        batch.setData(["timestamp": now], forDocument: db.collection("users").document(currentUserId).collection("following").document(targetUserId))
        batch.setData(["timestamp": now], forDocument: db.collection("users").document(targetUserId).collection("followers").document(currentUserId))
        batch.setData(["uid": currentUserId, "updatedAt": now, "followingCount": FieldValue.increment(Int64(1))], forDocument: db.collection("users").document(currentUserId), merge: true)
        batch.setData(["uid": targetUserId, "updatedAt": now, "followersCount": FieldValue.increment(Int64(1))], forDocument: db.collection("users").document(targetUserId), merge: true)
        batch.deleteDocument(db.collection("users").document(targetUserId).collection("followRequests").document(currentUserId))
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("sentFollowRequests").document(targetUserId))
        try await batch.commitAsync()
    }

    func unfollowUser(targetUserId: String) async throws {
        guard !currentUserId.isEmpty, targetUserId != currentUserId else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let batch = db.batch()
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("following").document(targetUserId))
        batch.deleteDocument(db.collection("users").document(targetUserId).collection("followers").document(currentUserId))
        batch.setData(["uid": currentUserId, "updatedAt": now, "followingCount": FieldValue.increment(Int64(-1))], forDocument: db.collection("users").document(currentUserId), merge: true)
        batch.setData(["uid": targetUserId, "updatedAt": now, "followersCount": FieldValue.increment(Int64(-1))], forDocument: db.collection("users").document(targetUserId), merge: true)
        try await batch.commitAsync()
    }

    func sendFollowRequest(targetUserId: String) async throws {
        guard !currentUserId.isEmpty, targetUserId != currentUserId else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let me = try await getDocument(db.collection("users").document(currentUserId))
        let username = me.data()?["username"] as? String ?? "usuario"
        let displayName = me.data()?["displayName"] as? String ?? username
        let batch = db.batch()
        batch.setData([
            "requesterId": currentUserId, "username": username, "displayName": displayName,
            "avatarUrl": me.data()?["avatarUrl"] as? String ?? "", "timestamp": now
        ], forDocument: db.collection("users").document(targetUserId).collection("followRequests").document(currentUserId))
        batch.setData(["targetUserId": targetUserId, "timestamp": now], forDocument: db.collection("users").document(currentUserId).collection("sentFollowRequests").document(targetUserId))
        try await batch.commitAsync()
    }

    func cancelFollowRequest(targetUserId: String) async throws {
        let batch = db.batch()
        batch.deleteDocument(db.collection("users").document(targetUserId).collection("followRequests").document(currentUserId))
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("sentFollowRequests").document(targetUserId))
        try await batch.commitAsync()
    }

    func acceptFollowRequest(requesterId: String) async throws {
        guard !currentUserId.isEmpty, requesterId != currentUserId else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let batch = db.batch()
        batch.setData(["timestamp": now], forDocument: db.collection("users").document(requesterId).collection("following").document(currentUserId))
        batch.setData(["timestamp": now], forDocument: db.collection("users").document(currentUserId).collection("followers").document(requesterId))
        batch.setData(["followingCount": FieldValue.increment(Int64(1)), "updatedAt": now], forDocument: db.collection("users").document(requesterId), merge: true)
        batch.setData(["followersCount": FieldValue.increment(Int64(1)), "updatedAt": now], forDocument: db.collection("users").document(currentUserId), merge: true)
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("followRequests").document(requesterId))
        batch.deleteDocument(db.collection("users").document(requesterId).collection("sentFollowRequests").document(currentUserId))
        try await batch.commitAsync()
    }

    func rejectFollowRequest(requesterId: String) async throws {
        let batch = db.batch()
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("followRequests").document(requesterId))
        batch.deleteDocument(db.collection("users").document(requesterId).collection("sentFollowRequests").document(currentUserId))
        try await batch.commitAsync()
    }

    func observeFollowingIds(userId: String, onChange: @escaping (Result<[String], Error>) -> Void) -> ListenerRegistration {
        db.collection("users").document(userId).collection("following").addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(snapshot?.documents.map(\.documentID) ?? []))
        }
    }

    func observeFollowRequests(onChange: @escaping (Result<[SocialUserPreview], Error>) -> Void) -> ListenerRegistration? {
        guard !currentUserId.isEmpty else { onChange(.success([])); return nil }
        return db.collection("users").document(currentUserId).collection("followRequests").addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            let users = snapshot?.documents.map { doc in
                SocialUserPreview(
                    id: doc.data()["requesterId"] as? String ?? doc.documentID,
                    username: doc.data()["username"] as? String ?? "usuario",
                    displayName: doc.data()["displayName"] as? String ?? "Usuario",
                    avatarURL: doc.data()["avatarUrl"] as? String ?? ""
                )
            } ?? []
            onChange(.success(users))
        }
    }

    func observeFollowers(userId: String, onChange: @escaping (Result<[String], Error>) -> Void) -> ListenerRegistration {
        db.collection("users").document(userId).collection("followers").addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(snapshot?.documents.map(\.documentID) ?? []))
        }
    }

    func loadUserPreviews(ids: [String]) async -> [SocialUserPreview] {
        var result: [SocialUserPreview] = []
        for id in ids {
            guard let snap = try? await getDocument(db.collection("users").document(id)), snap.exists else { continue }
            let data = snap.data() ?? [:]
            result.append(SocialUserPreview(
                id: id,
                username: data["username"] as? String ?? "usuario",
                displayName: data["displayName"] as? String ?? "Usuario",
                avatarURL: data["avatarUrl"] as? String ?? ""
            ))
        }
        return result
    }

    func blockUser(targetUserId: String) async throws {
        guard !currentUserId.isEmpty, targetUserId != currentUserId else { return }
        let batch = db.batch()
        batch.setData(["blockedUsers": FieldValue.arrayUnion([targetUserId]), "updatedAt": Int64(Date().timeIntervalSince1970 * 1_000)], forDocument: db.collection("users").document(currentUserId), merge: true)
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("following").document(targetUserId))
        batch.deleteDocument(db.collection("users").document(currentUserId).collection("followers").document(targetUserId))
        batch.deleteDocument(db.collection("users").document(targetUserId).collection("following").document(currentUserId))
        batch.deleteDocument(db.collection("users").document(targetUserId).collection("followers").document(currentUserId))
        try await batch.commitAsync()
    }

    func unblockUser(targetUserId: String) async throws {
        try await db.collection("users").document(currentUserId).setDataAsync(["blockedUsers": FieldValue.arrayRemove([targetUserId])], merge: true)
    }

    func observeBlockedIds(onChange: @escaping (Result<Set<String>, Error>) -> Void) -> ListenerRegistration? {
        guard !currentUserId.isEmpty else { onChange(.success([])); return nil }
        return db.collection("users").document(currentUserId).addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            let ids = Set((snapshot?.data()?["blockedUsers"] as? [String] ?? []).filter { !$0.isEmpty })
            onChange(.success(ids))
        }
    }

    func setCloseFriend(userId: String, enabled: Bool) async throws {
        guard !currentUserId.isEmpty, userId != currentUserId else { return }
        let ref = db.collection("users").document(currentUserId).collection("closeFriends").document(userId)
        if enabled { try await ref.setDataAsync(["userId": userId, "createdAt": Int64(Date().timeIntervalSince1970 * 1_000)]) }
        else { try await ref.deleteAsync() }
    }

    func observeCloseFriendIds(onChange: @escaping (Result<Set<String>, Error>) -> Void) -> ListenerRegistration? {
        guard !currentUserId.isEmpty else { onChange(.success([])); return nil }
        return db.collection("users").document(currentUserId).collection("closeFriends").addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(Set(snapshot?.documents.map(\.documentID) ?? [])))
        }
    }

    func setPrivateAccount(_ isPrivate: Bool) async throws {
        guard !currentUserId.isEmpty else { throw FirebaseRepositoryError.unauthenticated }
        try await db.collection("users").document(currentUserId).setDataAsync(["isPrivate": isPrivate, "updatedAt": Int64(Date().timeIntervalSince1970 * 1_000)], merge: true)
    }

    private func getDocument(_ ref: DocumentReference) async throws -> DocumentSnapshot {
        try await FirebaseAsync.value { completion in ref.getDocument(completion: completion) }
    }
}
