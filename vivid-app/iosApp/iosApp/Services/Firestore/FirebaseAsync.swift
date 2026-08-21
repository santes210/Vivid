import Foundation
import FirebaseFirestore

/// Puentes pequeños para las APIs callback de Firebase. Mantienen los
/// repositorios en async/await sin añadir dependencias externas.
enum FirebaseAsync {
    static func write(_ operation: (@escaping (Error?) -> Void) -> Void) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            operation { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
    }

    static func value<T>(_ operation: (@escaping (T?, Error?) -> Void) -> Void) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            operation { value, error in
                if let error { continuation.resume(throwing: error) }
                else if let value { continuation.resume(returning: value) }
                else { continuation.resume(throwing: FirebaseRepositoryError.missingValue) }
            }
        }
    }
}

enum FirebaseRepositoryError: LocalizedError {
    case unauthenticated
    case invalidInput(String)
    case missingValue

    var errorDescription: String? {
        switch self {
        case .unauthenticated: return "Necesitas iniciar sesión para realizar esta acción."
        case .invalidInput(let message): return message
        case .missingValue: return "Firebase no devolvió el resultado esperado."
        }
    }
}

extension DocumentReference {
    func setDataAsync(_ data: [String: Any], merge: Bool = false) async throws {
        try await FirebaseAsync.write { completion in setData(data, merge: merge, completion: completion) }
    }

    func updateDataAsync(_ fields: [AnyHashable: Any]) async throws {
        try await FirebaseAsync.write { completion in updateData(fields, completion: completion) }
    }

    func deleteAsync() async throws {
        try await FirebaseAsync.write { completion in delete(completion: completion) }
    }
}

extension WriteBatch {
    func commitAsync() async throws {
        try await FirebaseAsync.write { completion in commit(completion: completion) }
    }
}
