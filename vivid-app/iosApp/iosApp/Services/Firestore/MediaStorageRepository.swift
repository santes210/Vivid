import CryptoKit
import Foundation
import FirebaseAuth

struct UploadedMedia: Equatable {
    let url: String
    let storageKey: String
    let contentType: String
}

/// Cliente del mismo broker Cloudflare → Backblaze B2 que utiliza Android.
/// No usa Firebase Storage y nunca expone credenciales B2: autentica cada
/// petición al Worker con el ID token del usuario de Firebase.
final class MediaStorageRepository {
    private let workerBaseURL: URL
    private let auth: Auth
    private let session: URLSession

    init(workerBaseURL: URL? = nil, auth: Auth = Auth.auth(), session: URLSession = .shared) {
        let configuredURL = workerBaseURL ?? URL(string: Bundle.main.object(forInfoDictionaryKey: "VIVID_WORKER_URL") as? String ?? "")
        guard let configuredURL, configuredURL.scheme == "https" else {
            fatalError("VIVID_WORKER_URL no está configurada. Configúrala al compilar la app iOS.")
        }
        self.workerBaseURL = configuredURL
        self.auth = auth
        self.session = session
    }

    func upload(localURL: URL, path: String, contentType: String? = nil) async throws -> UploadedMedia {
        guard !path.isEmpty else { throw FirebaseRepositoryError.invalidInput("La ruta remota está vacía.") }
        let size = try fileSize(localURL)
        guard size > 0 else { throw FirebaseRepositoryError.invalidInput("El archivo está vacío.") }
        let type = contentType ?? Self.contentType(for: localURL)

        let ticket = try await workerRequest(path: "storage/upload-url", body: ["key": path, "contentType": type, "sizeBytes": size])
        guard let uploadURLString = ticket["uploadUrl"] as? String,
              let uploadURL = URL(string: uploadURLString),
              let uploadToken = ticket["uploadAuthToken"] as? String else {
            throw FirebaseRepositoryError.missingValue
        }
        let remoteKey = (ticket["remoteKey"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? path
        let sha1 = try sha1Hex(of: localURL)
        var request = URLRequest(url: uploadURL)
        request.httpMethod = "POST"
        request.setValue(uploadToken, forHTTPHeaderField: "Authorization")
        request.setValue(Self.b2EncodedFileName(remoteKey), forHTTPHeaderField: "X-Bz-File-Name")
        request.setValue(type, forHTTPHeaderField: "Content-Type")
        request.setValue(sha1, forHTTPHeaderField: "X-Bz-Content-Sha1")
        let (_, response) = try await session.upload(for: request, fromFile: localURL)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            throw FirebaseRepositoryError.invalidInput("Backblaze rechazó la subida.")
        }

        // Registra cuota/tamaño real en el Worker antes de publicar metadata.
        _ = try await workerRequest(path: "storage/complete", body: ["key": remoteKey, "uploadId": "\(sha1):\(size)", "sizeBytes": size, "contentType": type])
        let signed = try await workerRequest(path: "storage/sign", body: ["key": remoteKey, "ttlSec": 604_800])
        guard let signedURL = signed["signedUrl"] as? String, !signedURL.isEmpty else { throw FirebaseRepositoryError.missingValue }
        return UploadedMedia(url: signedURL, storageKey: remoteKey, contentType: type)
    }

    func signedDownloadURL(for path: String, ttlSeconds: Int = 604_800) async throws -> String {
        let result = try await workerRequest(path: "storage/sign", body: ["key": path, "ttlSec": min(max(ttlSeconds, 1), 604_800)])
        guard let url = result["signedUrl"] as? String else { throw FirebaseRepositoryError.missingValue }
        return url
    }

    func delete(path: String) async throws {
        guard !path.isEmpty else { return }
        _ = try await workerRequest(path: "storage/delete", body: ["key": path])
    }

    private func workerRequest(path: String, body: [String: Any]) async throws -> [String: Any] {
        let token = try await idToken()
        let endpoint = workerBaseURL.appendingPathComponent(path)
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FirebaseRepositoryError.missingValue }
        let json = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        guard 200..<300 ~= http.statusCode else {
            throw FirebaseRepositoryError.invalidInput((json["error"] as? String) ?? "El Worker de almacenamiento no respondió correctamente.")
        }
        return json
    }

    private func idToken() async throws -> String {
        guard let user = auth.currentUser else { throw FirebaseRepositoryError.unauthenticated }
        return try await withCheckedThrowingContinuation { continuation in
            user.getIDTokenForcingRefresh(false) { token, error in
                if let error { continuation.resume(throwing: error) }
                else if let token { continuation.resume(returning: token) }
                else { continuation.resume(throwing: FirebaseRepositoryError.missingValue) }
            }
        }
    }

    private func fileSize(_ url: URL) throws -> Int { try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0 }

    private func sha1Hex(of url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hash = Insecure.SHA1()
        while true {
            let data = try handle.read(upToCount: 1_048_576) ?? Data()
            if data.isEmpty { break }
            hash.update(data: data)
        }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static func b2EncodedFileName(_ key: String) -> String {
        key.addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? key
    }

    static func contentType(for url: URL) -> String {
        switch url.pathExtension.lowercased() {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "webp": return "image/webp"
        case "mp4": return "video/mp4"
        case "m4a": return "audio/mp4"
        case "mp3": return "audio/mpeg"
        default: return "application/octet-stream"
        }
    }
}
