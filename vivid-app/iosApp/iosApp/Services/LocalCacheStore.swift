import Foundation

/// Caché local equivalente a Room en Android: persiste el último feed,
/// reels y chats para arranque offline.
final class LocalCacheStore {
    static let shared = LocalCacheStore()
    private let defaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    struct CachedPost: Codable {
        let id: String
        let userId: String
        let username: String
        let userProfilePicture: String
        let imageUrl: String
        let caption: String
        let likesCount: Int
        let commentsCount: Int
        let timestamp: Int64
        let isVideo: Bool
        let videoUrl: String
        let thumbnailUrl: String
    }

    struct CachedReel: Codable {
        let id: String
        let userId: String
        let username: String
        let userAvatar: String
        let videoUrl: String
        let thumbnailUrl: String
        let caption: String
        let likes: Int
        let commentsCount: Int
    }

    func savePosts(_ posts: [PostUI]) {
        let cached = posts.map {
            CachedPost(id: $0.id, userId: $0.userId, username: $0.username, userProfilePicture: $0.userProfilePicture, imageUrl: $0.imageUrl, caption: $0.caption, likesCount: $0.likesCount, commentsCount: $0.commentsCount, timestamp: $0.timestamp, isVideo: $0.isVideo, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl)
        }
        if let data = try? encoder.encode(cached) { defaults.set(data, forKey: "cache.posts") }
    }

    func loadPosts() -> [PostUI] {
        guard let data = defaults.data(forKey: "cache.posts"),
              let cached = try? decoder.decode([CachedPost].self, from: data) else { return [] }
        return cached.map {
            PostUI(id: $0.id, userId: $0.userId, username: $0.username, userProfilePicture: $0.userProfilePicture, imageUrl: $0.imageUrl, caption: $0.caption, likesCount: $0.likesCount, commentsCount: $0.commentsCount, timestamp: $0.timestamp, isLiked: false, isVideo: $0.isVideo, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl)
        }
    }

    func saveReels(_ reels: [ReelUI]) {
        let cached = reels.map {
            CachedReel(id: $0.id, userId: $0.userId, username: $0.username, userAvatar: $0.userAvatar, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl, caption: $0.caption, likes: $0.likes, commentsCount: $0.commentsCount)
        }
        if let data = try? encoder.encode(cached) { defaults.set(data, forKey: "cache.reels") }
    }

    func loadReels() -> [ReelUI] {
        guard let data = defaults.data(forKey: "cache.reels"),
              let cached = try? decoder.decode([CachedReel].self, from: data) else { return [] }
        return cached.map {
            ReelUI(id: $0.id, userId: $0.userId, username: $0.username, userAvatar: $0.userAvatar, videoUrl: $0.videoUrl, thumbnailUrl: $0.thumbnailUrl, caption: $0.caption, likes: $0.likes, commentsCount: $0.commentsCount)
        }
    }
}
