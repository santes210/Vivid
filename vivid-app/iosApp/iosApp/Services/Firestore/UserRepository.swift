import FirebaseFirestore

/// Acceso en tiempo real a `/users`. El llamador conserva el registro devuelto
/// y llama `remove()` al desaparecer la pantalla o al cerrar sesión.
final class UserRepository {
    private let db: Firestore

    init(db: Firestore = Firestore.firestore()) {
        self.db = db
    }

    @discardableResult
    func observeUser(
        id userId: String,
        onChange: @escaping (Result<VividUser?, Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("users").document(userId).addSnapshotListener { snapshot, error in
            if let error { onChange(.failure(error)); return }
            onChange(.success(snapshot.flatMap(VividUser.init(document:))))
        }
    }

    @discardableResult
    func observeUsers(
        ids: [String],
        onChange: @escaping (Result<[VividUser], Error>) -> Void
    ) -> ListenerRegistration? {
        let uniqueIDs = Array(Set(ids.filter { !$0.isEmpty }))
        guard !uniqueIDs.isEmpty else { onChange(.success([])); return nil }
        // Firestore limita `in` a 10 valores; los consumidores grandes deben
        // dividir la consulta o usar observeUser para cada perfil.
        precondition(uniqueIDs.count <= 10, "Firestore admite un máximo de 10 IDs en una consulta in")
        return db.collection("users")
            .whereField(FieldPath.documentID(), in: uniqueIDs)
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(VividUser.init(document:)) ?? []))
            }
    }
}
