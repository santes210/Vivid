import Foundation

/// Renueva URLs temporales B2 usando el storageKey persistente de SQLite.
/// Se usa al hidratar caché y al recuperar conectividad.
final class SignedURLRefreshService {
    static let shared = SignedURLRefreshService()
    private init() {}

    func refresh(posts: [PostUI]) async -> [PostUI] {
        var output: [PostUI] = []
        let storage = MediaStorageRepository()
        for post in posts {
            guard !post.storageKey.isEmpty, let signed = try? await storage.signedDownloadURL(for: post.storageKey) else { output.append(post); continue }
            output.append(PostUI(id: post.id, userId: post.userId, username: post.username, userProfilePicture: post.userProfilePicture, imageUrl: post.isVideo ? post.imageUrl : signed, caption: post.caption, likesCount: post.likesCount, commentsCount: post.commentsCount, timestamp: post.timestamp, isLiked: post.isLiked, isVideo: post.isVideo, videoUrl: post.isVideo ? signed : post.videoUrl, thumbnailUrl: post.thumbnailUrl, storageKey: post.storageKey))
        }
        return output
    }

    func refresh(reels: [ReelUI]) async -> [ReelUI] {
        var output: [ReelUI] = []; let storage = MediaStorageRepository()
        for reel in reels {
            guard !reel.storageKey.isEmpty, let signed = try? await storage.signedDownloadURL(for: reel.storageKey) else { output.append(reel); continue }
            output.append(ReelUI(id: reel.id, userId: reel.userId, username: reel.username, userAvatar: reel.userAvatar, videoUrl: signed, thumbnailUrl: reel.thumbnailUrl, caption: reel.caption, likes: reel.likes, commentsCount: reel.commentsCount, timestamp: reel.timestamp, isLiked: reel.isLiked, storageKey: reel.storageKey))
        }
        return output
    }
}
