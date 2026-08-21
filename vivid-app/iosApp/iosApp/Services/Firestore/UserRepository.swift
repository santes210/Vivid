import FirebaseAuth
import FirebaseFirestore

/// Acceso en tiempo real a `/users` y operaciones de perfil/búsqueda.
final class UserRepository {
    private let db: Firestore
    private let auth: Auth

    init(db: Firestore = Firestore.firestore(), auth: Auth = Auth.auth()) {
        self.db = db
        self.auth = auth
    }

    @discardableResult
    func observeUser(id userId: String, onChange: @escaping (Result<VividUser?, Error>) -> Void) -> ListenerRegistration {
        db.collection("users").document(userId).addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(snapshot.flatMap { VividUser(document: $0) }))
        }
    }

    /// Búsqueda prefijada indexada por `usernameLower`, igual que Android.
    @discardableResult
    func observeUsernameSearch(prefix: String, limit: Int = 30, onChange: @escaping (Result<[VividUser], Error>) -> Void) -> ListenerRegistration? {
        let normalized = prefix.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalized.isEmpty else { onChange(.success([])); return nil }
        return db.collection("users")
            .order(by: "usernameLower")
            .start(at: [normalized])
            .end(at: [normalized + "\u{f8ff}"])
            .limit(to: limit)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap { VividUser(document: $0) } ?? []))
            }
    }

    func updateProfile(username: String, displayName: String, bio: String, avatarURL: String? = nil) async throws {
        guard let uid = auth.currentUser?.uid else { throw FirebaseRepositoryError.unauthenticated }
        let trimmedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedUsername.isEmpty else { throw FirebaseRepositoryError.invalidInput("El username es obligatorio.") }
        var fields: [AnyHashable: Any] = [
            "username": trimmedUsername,
            "usernameLower": trimmedUsername.lowercased(),
            "displayName": displayName.trimmingCharacters(in: .whitespacesAndNewlines),
            "bio": bio.trimmingCharacters(in: .whitespacesAndNewlines),
            "updatedAt": Int64(Date().timeIntervalSince1970 * 1_000)
        ]
        if let avatarURL { fields["avatarUrl"] = avatarURL }
        try await db.collection("users").document(uid).updateDataAsync(fields)
    }
}
