import Foundation
import FirebaseFirestore

/// Modelos nativos que reflejan el esquema Firestore que ya consume Android.
/// Las fechas se conservan como epoch en milisegundos porque así están guardadas
/// actualmente por la app Android.
struct VividUser: Identifiable, Equatable {
    let id: String
    let username: String
    let displayName: String
    let bio: String
    let avatarURL: String
    let avatarBase64: String
    let email: String
    let followersCount: Int
    let followingCount: Int
    let postsCount: Int
    let isPrivate: Bool

    init?(document: DocumentSnapshot) {
        guard document.exists else { return nil }
        let data = document.data() ?? [:]
        id = document.documentID
        username = data.string("username") ?? "usuario"
        displayName = data.string("displayName") ?? username
        bio = data.string("bio") ?? ""
        avatarURL = data.string("avatarUrl") ?? ""
        avatarBase64 = data.string("avatarBase64") ?? ""
        email = data.string("email") ?? ""
        followersCount = data.int("followersCount")
        followingCount = data.int("followingCount")
        postsCount = data.int("postsCount")
        isPrivate = data.bool("isPrivate")
    }
}

struct FirestorePost: Identifiable, Equatable {
    let id: String
    let userId: String
    let username: String
    let userProfilePicture: String
    let userProfilePictureBase64: String
    let imageURL: String
    let imageBase64: String
    let storageKey: String
    let videoURL: String
    let thumbnailURL: String
    let isVideo: Bool
    let caption: String
    let likesCount: Int
    let commentsCount: Int
    let timestamp: Int64
    let isPrivate: Bool
    let hashtags: [String]

    init?(document: DocumentSnapshot) {
        let data = document.data() ?? [:]
        guard let userId = data.string("userId"), !userId.isEmpty else { return nil }
        id = document.documentID
        self.userId = userId
        username = data.string("username") ?? "usuario"
        userProfilePicture = data.string("userProfilePicture") ?? data.string("avatarUrl") ?? ""
        userProfilePictureBase64 = data.string("userProfilePictureBase64") ?? data.string("avatarBase64") ?? ""
        imageURL = data.string("imageUrl") ?? ""
        imageBase64 = data.string("imageBase64") ?? ""
        storageKey = data.string("storageKey") ?? ""
        videoURL = data.string("videoUrl") ?? ""
        thumbnailURL = data.string("thumbnailUrl") ?? ""
        isVideo = data.bool("isVideo")
        caption = data.string("caption") ?? ""
        likesCount = data.int("likesCount")
        commentsCount = data.int("commentsCount")
        timestamp = data.int64("timestamp")
        isPrivate = data.bool("isPrivate")
        hashtags = data.stringArray("hashtags")
    }
}

struct FirestoreStory: Identifiable, Equatable {
    let id: String
    let userId: String
    let username: String
    let avatarURL: String
    let avatarBase64: String
    let mediaURL: String
    let mediaBase64: String
    let videoURL: String
    let thumbnailURL: String
    let type: String
    let caption: String
    let createdAt: Int64
    let expiresAt: Int64
    let isPrivate: Bool
    let storageKey: String
    let viewersCount: Int

    init?(document: DocumentSnapshot) {
        let data = document.data() ?? [:]
        guard let userId = data.string("userId"), !userId.isEmpty else { return nil }
        id = document.documentID
        self.userId = userId
        username = data.string("username") ?? "usuario"
        avatarURL = data.string("avatarUrl") ?? data.string("userAvatar") ?? ""
        avatarBase64 = data.string("avatarBase64") ?? ""
        mediaURL = data.string("mediaUrl") ?? ""
        mediaBase64 = data.string("mediaBase64") ?? ""
        videoURL = data.string("videoUrl") ?? ""
        thumbnailURL = data.string("thumbnailUrl") ?? ""
        type = data.string("type") ?? "photo"
        caption = data.string("caption") ?? ""
        createdAt = data.int64("createdAt")
        expiresAt = data.int64("expiresAt")
        isPrivate = data.bool("isPrivate")
        storageKey = data.string("storageKey") ?? ""
        viewersCount = data.int("viewersCount")
    }
}

struct FirestoreReel: Identifiable, Equatable {
    let id: String
    let userId: String
    let username: String
    let userAvatar: String
    let videoURL: String
    let thumbnailURL: String
    let storageKey: String
    let caption: String
    let likes: Int
    let commentsCount: Int
    let timestamp: Int64
    let isPrivate: Bool

    init?(document: DocumentSnapshot) {
        let data = document.data() ?? [:]
        guard let userId = data.string("userId"), !userId.isEmpty else { return nil }
        id = document.documentID
        self.userId = userId
        username = data.string("username") ?? "usuario"
        userAvatar = data.string("userAvatar") ?? data.string("avatarUrl") ?? ""
        videoURL = data.string("videoUrl") ?? ""
        thumbnailURL = data.string("thumbnailUrl") ?? ""
        storageKey = data.string("storageKey") ?? ""
        caption = data.string("caption") ?? ""
        likes = data.int("likes")
        commentsCount = data.int("comments")
        timestamp = data.int64("timestamp")
        isPrivate = data.bool("isPrivate")
    }
}

struct FirestoreChat: Identifiable, Equatable {
    let id: String
    let participants: [String]
    let participantNames: [String: String]
    let participantAvatars: [String: String]
    let participantAvatarBase64s: [String: String]
    let lastMessage: String
    let lastTimestamp: Int64
    let unreadCounts: [String: Int]

    init?(document: DocumentSnapshot) {
        let data = document.data() ?? [:]
        let participants = data.stringArray("participants")
        guard participants.count == 2 else { return nil }
        id = document.documentID
        self.participants = participants
        participantNames = data.stringDictionary("participantNames")
        participantAvatars = data.stringDictionary("participantAvatars")
        participantAvatarBase64s = data.stringDictionary("participantAvatarBase64s")
        lastMessage = data.string("lastMessage") ?? ""
        lastTimestamp = data.int64("lastTimestamp")
        unreadCounts = data.intDictionary("unreadCounts")
    }

    func otherParticipant(for currentUserId: String) -> String? {
        participants.first { $0 != currentUserId }
    }
}

struct FirestoreMessage: Identifiable, Equatable {
    let id: String
    let text: String
    let senderId: String
    let receiverId: String
    let timestamp: Int64
    let isRead: Bool
    let isDelivered: Bool
    let reaction: String
    let type: String
    let imageURL: String
    let imageKey: String
    let voiceURL: String
    let voiceKey: String
    let voiceDurationMs: Int64
    let replyToStoryId: String
    let lastEditedAt: Int64

    init?(document: DocumentSnapshot) {
        let data = document.data() ?? [:]
        guard let senderId = data.string("senderId"), !senderId.isEmpty else { return nil }
        id = document.documentID
        text = data.string("text") ?? ""
        self.senderId = senderId
        receiverId = data.string("receiverId") ?? ""
        timestamp = data.int64("timestamp")
        isRead = data.bool("isRead")
        isDelivered = data.bool("isDelivered")
        reaction = data.string("reaction") ?? ""
        type = data.string("type") ?? "text"
        imageURL = data.string("imageUrl") ?? ""
        imageKey = data.string("imageKey") ?? ""
        voiceURL = data.string("voiceUrl") ?? ""
        voiceKey = data.string("voiceKey") ?? ""
        voiceDurationMs = data.int64("voiceDurationMs")
        replyToStoryId = data.string("replyToStoryId") ?? ""
        lastEditedAt = data.int64("lastEditedAt")
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? { self[key] as? String }
    func bool(_ key: String) -> Bool { self[key] as? Bool ?? false }
    func int(_ key: String) -> Int { Int(int64(key)) }
    func int64(_ key: String) -> Int64 {
        if let value = self[key] as? Int64 { return value }
        if let value = self[key] as? Int { return Int64(value) }
        if let value = self[key] as? NSNumber { return value.int64Value }
        if let value = self[key] as? Timestamp { return Int64(value.dateValue().timeIntervalSince1970 * 1_000) }
        return 0
    }
    func stringArray(_ key: String) -> [String] { self[key] as? [String] ?? [] }
    func stringDictionary(_ key: String) -> [String: String] { self[key] as? [String: String] ?? [:] }
    func intDictionary(_ key: String) -> [String: Int] {
        guard let values = self[key] as? [String: Any] else { return [:] }
        return values.reduce(into: [:]) { result, item in
            if let number = item.value as? NSNumber { result[item.key] = number.intValue }
            else if let value = item.value as? Int { result[item.key] = value }
        }
    }
}
