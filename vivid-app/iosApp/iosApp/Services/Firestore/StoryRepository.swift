import Foundation
import FirebaseFirestore

/// Fuente en tiempo real para stories activas. `now` se captura por suscripción;
/// para que una story caduque sin cambios remotos, vuelve a suscribirte al entrar
/// en foreground o programa una actualización de UI al llegar a `expiresAt`.
final class StoryRepository {
    private let db: Firestore

    init(db: Firestore = Firestore.firestore()) { self.db = db }

    @discardableResult
    func observePublicActiveStories(
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000),
        onChange: @escaping (Result<[FirestoreStory], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("stories")
            .whereField("isPrivate", isEqualTo: false)
            .whereField("expiresAt", isGreaterThan: now)
            .order(by: "expiresAt")
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestoreStory.init(document:)) ?? []))
            }
    }

    @discardableResult
    func observeActiveStories(
        byUserId userId: String,
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1_000),
        onChange: @escaping (Result<[FirestoreStory], Error>) -> Void
    ) -> ListenerRegistration {
        db.collection("stories")
            .whereField("userId", isEqualTo: userId)
            .whereField("expiresAt", isGreaterThan: now)
            .order(by: "expiresAt")
            .addSnapshotListener { snapshot, error in
                if let error { onChange(.failure(error)); return }
                onChange(.success(snapshot?.documents.compactMap(FirestoreStory.init(document:)) ?? []))
            }
    }
}
