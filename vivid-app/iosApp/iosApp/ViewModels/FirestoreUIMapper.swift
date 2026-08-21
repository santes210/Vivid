import Foundation

extension User {
    var asFirestoreUser: VividUser {
        // Se usa solo como payload de creación; el perfil real sigue escuchándose
        // desde /users/{uid} en AppState.
        VividUser(id: uid, username: username, displayName: displayName, bio: bio, avatarURL: avatarUrl, avatarBase64: avatarBase64, email: email, followersCount: followersCount, followingCount: followingCount, postsCount: postsCount, isPrivate: isPrivate)
    }
}

extension FirestorePost {
    func asUI() -> PostUI {
        PostUI(id: id, userId: userId, username: username, userProfilePicture: userProfilePicture, imageUrl: imageURL, caption: caption, likesCount: likesCount, commentsCount: commentsCount, timestamp: timestamp, isLiked: false, isVideo: isVideo, videoUrl: videoURL, thumbnailUrl: thumbnailURL, storageKey: storageKey)
    }
}

extension FirestoreReel {
    func asUI() -> ReelUI {
        ReelUI(id: id, userId: userId, username: username, userAvatar: userAvatar, videoUrl: videoURL, thumbnailUrl: thumbnailURL, caption: caption, likes: likes, commentsCount: commentsCount, timestamp: timestamp, storageKey: storageKey)
    }
}

extension Array where Element == FirestoreStory {
    func groupedForUI() -> [StoryGroupUI] {
        Dictionary(grouping: self, by: \.userId).values.map { stories in
            let ordered = stories.sorted { $0.createdAt < $1.createdAt }
            guard let first = ordered.first else { fatalError("A story group cannot be empty") }
            return StoryGroupUI(id: first.userId, userId: first.userId, username: first.username, avatarUrl: first.avatarURL, stories: ordered.map { StoryUI(id: $0.id, mediaUrl: $0.videoURL.isEmpty ? $0.mediaURL : $0.videoURL, type: $0.type, caption: $0.caption, createdAt: $0.createdAt) })
        }.sorted { ($0.stories.first?.createdAt ?? 0) > ($1.stories.first?.createdAt ?? 0) }
    }
}
