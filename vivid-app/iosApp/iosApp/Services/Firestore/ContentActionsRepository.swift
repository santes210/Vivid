import FirebaseAuth
import FirebaseFirestore

final class ContentActionsRepository {
    private let db: Firestore
    private let auth: Auth

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) {
        self.db = db
        self.auth = auth
    }

    func toggleSave(postId: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let ref = db.collection("users").document(uid).collection("savedPosts").document(postId)
        let snap = try await FirebaseAsync.value { completion in ref.getDocument(completion: completion) }
        if snap.exists {
            try await ref.deleteAsync()
        } else {
            try await ref.setDataAsync(["postId": postId, "savedAt": Int64(Date().timeIntervalSince1970 * 1_000)])
        }
    }

    func observeSavedPostIds(onChange: @escaping (Result<Set<String>, Error>) -> Void) -> ListenerRegistration? {
        guard let uid = auth.currentUser?.uid else { onChange(.success([])); return nil }
        return db.collection("users").document(uid).collection("savedPosts").addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(Set(snapshot?.documents.map(\.documentID) ?? [])))
        }
    }

    func report(targetType: String, targetId: String, reason: String) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let body = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { throw FirebaseRepositoryError.invalidInput("Describe el motivo del reporte.") }
        try await db.collection("reports").document().setDataAsync([
            "reporterId": uid,
            "targetType": targetType,
            "targetId": targetId,
            "reason": body,
            "status": "open",
            "createdAt": Int64(Date().timeIntervalSince1970 * 1_000)
        ])
    }
}
