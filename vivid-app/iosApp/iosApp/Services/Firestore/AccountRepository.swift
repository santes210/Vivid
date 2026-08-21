import FirebaseAuth
import FirebaseFirestore
import Foundation

/// Elimina contenido remoto y sus objetos B2 antes de borrar perfil y usuario
/// de Firebase Auth. Si Auth exige login reciente, el error se muestra al usuario.
final class AccountRepository {
    private let db = Firestore.firestore()
    private let auth = Auth.auth()
    private let storage = MediaStorageRepository()

    func deleteCurrentAccount() async throws {
        guard let user = auth.currentUser else { throw FirebaseRepositoryError.unauthenticated }
        let uid = user.uid
        for collection in ["posts", "reels", "stories"] {
            let snapshot = try await FirebaseAsync.value { completion in self.db.collection(collection).whereField("userId", isEqualTo: uid).getDocuments(completion: completion) }
            for document in snapshot.documents {
                if let key = document.data()["storageKey"] as? String, !key.isEmpty { try? await storage.delete(path: key) }
                try await document.reference.deleteAsync()
            }
        }
        try await db.collection("users").document(uid).deleteAsync()
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            user.delete { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            }
        }
    }
}
