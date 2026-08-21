import Foundation
import SQLite3

/// Caché SQLite transaccional para feed/reels y cola offline. A diferencia de
/// UserDefaults, soporta cientos de filas, migraciones y escrituras atómicas.
final class LocalCacheStore {
    static let shared = LocalCacheStore()
    private let db: OpaquePointer?
    private let queue = DispatchQueue(label: "com.vivid.sqlite", qos: .utility)
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private struct CachedPost: Codable {
        let id, userId, username, userProfilePicture, imageUrl, caption, videoUrl, thumbnailUrl: String
        let likesCount, commentsCount: Int
        let timestamp: Int64
        let isVideo: Bool
        var storageKey: String? = nil
    }
    private struct CachedReel: Codable {
        let id, userId, username, userAvatar, videoUrl, thumbnailUrl, caption: String
        let likes, commentsCount: Int
        let timestamp: Int64
        var storageKey: String? = nil
    }
    struct PendingOperation: Codable, Identifiable {
        let id: String
        let kind: String
        let payload: [String: String]
        let createdAt: Int64
    }

    private init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("Vivid", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var pointer: OpaquePointer?
        if sqlite3_open_v2(directory.appendingPathComponent("vivid.sqlite3").path, &pointer, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil) != SQLITE_OK {
            assertionFailure("No se pudo abrir vivid.sqlite3")
        }
        db = pointer
        migrate()
    }

    deinit { sqlite3_close(db) }

    private func migrate() {
        execute("PRAGMA journal_mode=WAL;")
        execute("PRAGMA foreign_keys=ON;")
        execute("CREATE TABLE IF NOT EXISTS cache_items (kind TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL, updated_at INTEGER NOT NULL, json BLOB NOT NULL, PRIMARY KEY(kind,id));")
        execute("CREATE INDEX IF NOT EXISTS idx_cache_order ON cache_items(kind, position);")
        execute("CREATE TABLE IF NOT EXISTS pending_operations (id TEXT PRIMARY KEY, kind TEXT NOT NULL, created_at INTEGER NOT NULL, json BLOB NOT NULL);")
        execute("PRAGMA user_version=1;")
    }

    func savePosts(_ posts: [PostUI]) {
        let values = posts.map { CachedPost(id: $0.id, userId: $0.userId, username: $0.username, userProfilePicture: $0.userProfilePicture, imageUrl: $0.imageUrl, caption: $0.caption, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl, likesCount: $0.likesCount, commentsCount: $0.commentsCount, timestamp: $0.timestamp, isVideo: $0.isVideo, storageKey: $0.storageKey) }
        save(values, kind: "post", ids: values.map(\.id))
    }

    func loadPosts() -> [PostUI] {
        load(CachedPost.self, kind: "post").map {
            PostUI(id: $0.id, userId: $0.userId, username: $0.username, userProfilePicture: $0.userProfilePicture, imageUrl: $0.imageUrl, caption: $0.caption, likesCount: $0.likesCount, commentsCount: $0.commentsCount, timestamp: $0.timestamp, isLiked: false, isVideo: $0.isVideo, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl, storageKey: $0.storageKey ?? "")
        }
    }

    func saveReels(_ reels: [ReelUI]) {
        let values = reels.map { CachedReel(id: $0.id, userId: $0.userId, username: $0.username, userAvatar: $0.userAvatar, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl, caption: $0.caption, likes: $0.likes, commentsCount: $0.commentsCount, timestamp: $0.timestamp, storageKey: $0.storageKey) }
        save(values, kind: "reel", ids: values.map(\.id))
    }

    func loadReels() -> [ReelUI] {
        load(CachedReel.self, kind: "reel").map { ReelUI(id: $0.id, userId: $0.userId, username: $0.username, userAvatar: $0.userAvatar, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl, caption: $0.caption, likes: $0.likes, commentsCount: $0.commentsCount, timestamp: $0.timestamp, storageKey: $0.storageKey ?? "") }
    }

    func enqueue(kind: String, payload: [String: String]) {
        let operation = PendingOperation(id: UUID().uuidString, kind: kind, payload: payload, createdAt: Int64(Date().timeIntervalSince1970 * 1_000))
        guard let data = try? encoder.encode(operation) else { return }
        queue.sync {
            var statement: OpaquePointer?
            sqlite3_prepare_v2(db, "INSERT OR REPLACE INTO pending_operations(id,kind,created_at,json) VALUES(?,?,?,?);", -1, &statement, nil)
            bind(operation.id, to: 1, in: statement); bind(kind, to: 2, in: statement)
            sqlite3_bind_int64(statement, 3, operation.createdAt); bind(data, to: 4, in: statement)
            sqlite3_step(statement); sqlite3_finalize(statement)
        }
    }

    func pendingOperations() -> [PendingOperation] {
        queue.sync {
            var result: [PendingOperation] = []; var statement: OpaquePointer?
            sqlite3_prepare_v2(db, "SELECT json FROM pending_operations ORDER BY created_at;", -1, &statement, nil)
            while sqlite3_step(statement) == SQLITE_ROW, let data = blob(statement, column: 0), let value = try? decoder.decode(PendingOperation.self, from: data) { result.append(value) }
            sqlite3_finalize(statement); return result
        }
    }

    func removeOperation(id: String) { queue.sync { execute("DELETE FROM pending_operations WHERE id=?;", values: [id]) } }

    private func save<T: Encodable>(_ values: [T], kind: String, ids: [String]) {
        queue.sync {
            execute("BEGIN IMMEDIATE;"); execute("DELETE FROM cache_items WHERE kind=?;", values: [kind])
            let now = Int64(Date().timeIntervalSince1970 * 1_000)
            for (index, value) in values.enumerated() {
                guard let data = try? encoder.encode(value) else { continue }
                var statement: OpaquePointer?
                sqlite3_prepare_v2(db, "INSERT INTO cache_items(kind,id,position,updated_at,json) VALUES(?,?,?,?,?);", -1, &statement, nil)
                bind(kind, to: 1, in: statement); bind(ids[index], to: 2, in: statement)
                sqlite3_bind_int64(statement, 3, Int64(index)); sqlite3_bind_int64(statement, 4, now); bind(data, to: 5, in: statement)
                sqlite3_step(statement); sqlite3_finalize(statement)
            }
            execute("COMMIT;")
        }
    }

    private func load<T: Decodable>(_ type: T.Type, kind: String) -> [T] {
        queue.sync {
            var result: [T] = []; var statement: OpaquePointer?
            sqlite3_prepare_v2(db, "SELECT json FROM cache_items WHERE kind=? ORDER BY position;", -1, &statement, nil)
            bind(kind, to: 1, in: statement)
            while sqlite3_step(statement) == SQLITE_ROW, let data = blob(statement, column: 0), let value = try? decoder.decode(type, from: data) { result.append(value) }
            sqlite3_finalize(statement); return result
        }
    }

    private func execute(_ sql: String, values: [String] = []) {
        var statement: OpaquePointer?; sqlite3_prepare_v2(db, sql, -1, &statement, nil)
        for (index, value) in values.enumerated() { bind(value, to: Int32(index + 1), in: statement) }
        sqlite3_step(statement); sqlite3_finalize(statement)
    }
    private func bind(_ value: String, to index: Int32, in statement: OpaquePointer?) { sqlite3_bind_text(statement, index, value, -1, transient) }
    private func bind(_ value: Data, to index: Int32, in statement: OpaquePointer?) { value.withUnsafeBytes { sqlite3_bind_blob(statement, index, $0.baseAddress, Int32(value.count), transient) } }
    private func blob(_ statement: OpaquePointer?, column: Int32) -> Data? {
        guard let bytes = sqlite3_column_blob(statement, column) else { return nil }
        return Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, column)))
    }
}
