import SwiftUI
import Combine

/**
 * ViewModel del feed principal.
 * Gestiona la carga de posts y stories desde el repositorio compartido.
 */
@MainActor
class FeedViewModel: ObservableObject {
    @Published var posts: [PostUI] = []
    @Published var storyGroups: [StoryGroupUI] = []
    @Published var isLoading = false
    @Published var error: String? = nil

    private var currentPage = 0

    func loadFeed() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }

        // En producción: usar el SharedContainer para obtener datos reales
        // let container = SharedContainer.get()
        // let feedPosts = container.contentRepository.getFeedFlow()

        // Datos de ejemplo para el prototipo
        posts = generateSamplePosts()
        storyGroups = generateSampleStories()
    }

    func refresh() async {
        currentPage = 0
        await loadFeed()
    }

    func loadMore() async {
        currentPage += 1
        // Cargar más posts...
    }

    func toggleLike(postId: String) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        let post = posts[index]
        posts[index] = PostUI(
            id: post.id,
            userId: post.userId,
            username: post.username,
            userProfilePicture: post.userProfilePicture,
            imageUrl: post.imageUrl,
            caption: post.caption,
            likesCount: post.isLiked ? post.likesCount - 1 : post.likesCount + 1,
            commentsCount: post.commentsCount,
            timestamp: post.timestamp,
            isLiked: !post.isLiked,
            isVideo: post.isVideo,
            videoUrl: post.videoUrl,
            thumbnailUrl: post.thumbnailUrl
        )
    }

    // MARK: - Sample Data

    private func generateSamplePosts() -> [PostUI] {
        let usernames = ["maria_photo", "carlos.design", "luna_travels", "diego_music", "sofia.art"]
        let captions = [
            "Atardecer increíble 🌅 #nature",
            "Nuevo diseño terminado ✨ #design",
            "Explorando nuevas ciudades 🏙️",
            "Música para el alma 🎵",
            "Arte abstracto del día 🎨"
        ]
        let now = Date().timeIntervalSince1970 * 1000
        var result: [PostUI] = []
        for i in 0..<5 {
            let post = PostUI(
                id: "post_\(i)",
                userId: "user_\(i)",
                username: usernames[i % usernames.count],
                userProfilePicture: "",
                imageUrl: "",
                caption: captions[i % 5],
                likesCount: Int.random(in: 10...500),
                commentsCount: Int.random(in: 0...50),
                timestamp: Int64(now) - Int64(i * 3600000),
                isLiked: Bool.random(),
                isVideo: i % 3 == 0,
                videoUrl: "",
                thumbnailUrl: ""
            )
            result.append(post)
        }
        return result
    }

    private func generateSampleStories() -> [StoryGroupUI] {
        let usernames = ["ana", "pedro", "lucia", "jorge", "elena", "marco", "laura", "ivan"]
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var result: [StoryGroupUI] = []
        for i in 0..<8 {
            let story = StoryUI(id: "s_\(i)_0", mediaUrl: "", type: "photo", caption: "", createdAt: now)
            let group = StoryGroupUI(
                id: "story_group_\(i)",
                userId: "user_\(i)",
                username: usernames[i],
                avatarUrl: "",
                stories: [story],
                hasUnseen: i < 5
            )
            result.append(group)
        }
        return result
    }
}

/**
 * Formateo de tiempo relativo para iOS.
 * Equivalente a TimeFormatter del módulo shared.
 */
enum TimeAgoFormatter {
    static func format(_ timestamp: Int64) -> String {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let diffMs = now - timestamp
        guard diffMs > 0 else { return "justo ahora" }

        let seconds = diffMs / 1000
        let minutes = seconds / 60
        let hours = minutes / 60
        let days = hours / 24

        switch days {
        case 0:
            if hours > 0 { return "hace \(hours)h" }
            if minutes > 0 { return "hace \(minutes)m" }
            return "justo ahora"
        case 1..<7:
            return "hace \(days)d"
        case 7..<30:
            return "hace \(days / 7)sem"
        case 30..<365:
            return "hace \(days / 30)mes"
        default:
            return "hace \(days / 365)a"
        }
    }
}
