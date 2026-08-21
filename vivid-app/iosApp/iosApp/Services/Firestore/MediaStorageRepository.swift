import Foundation
import FirebaseStorage

struct UploadedMedia: Equatable {
    let url: String
    let storageKey: String
    let contentType: String
}

/// Sube el archivo local capturado o elegido a Firebase Storage y devuelve la
/// URL que se guarda en el mismo documento Firestore que Android consume.
final class MediaStorageRepository {
    private let storage: Storage

    init(storage: Storage = Storage.storage()) { self.storage = storage }

    func upload(localURL: URL, path: String, contentType: String? = nil) async throws -> UploadedMedia {
        guard !path.isEmpty else { throw FirebaseRepositoryError.invalidInput("La ruta de Storage está vacía.") }
        let reference = storage.reference().child(path)
        let metadata = StorageMetadata()
        metadata.contentType = contentType ?? Self.contentType(for: localURL)
        _ = try await reference.putFileAsync(from: localURL, metadata: metadata)
        let remoteURL = try await reference.downloadURLAsync()
        return UploadedMedia(url: remoteURL.absoluteString, storageKey: path, contentType: metadata.contentType ?? "application/octet-stream")
    }

    func delete(path: String) async throws {
        guard !path.isEmpty else { return }
        try await FirebaseAsync.write { completion in
            storage.reference().child(path).delete(completion: completion)
        }
    }

    static func contentType(for url: URL) -> String {
        switch url.pathExtension.lowercased() {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "heic": return "image/heic"
        case "mp4": return "video/mp4"
        case "mov": return "video/quicktime"
        default: return "application/octet-stream"
        }
    }
}
