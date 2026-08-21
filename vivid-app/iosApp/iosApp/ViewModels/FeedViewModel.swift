import SwiftUI
import Combine
import FirebaseFirestore

@MainActor
class FeedViewModel: ObservableObject {
    @Published var posts: [PostUI] = []
    @Published var storyGroups: [StoryGroupUI] = []
    @Published var isLoading = false
    @Published var error: String? = nil

    private let postsRepository = PostRepository()
    private let storiesRepository = StoryRepository()
    private let followRepository = FollowRepository()
    private var postsListener: ListenerRegistration?
    private var storiesListener: ListenerRegistration?
    private var followingListener: ListenerRegistration?

    func loadFeed() async {
        if posts.isEmpty { posts = LocalCacheStore.shared.loadPosts() }
        guard followingListener == nil else { return }
        isLoading = true
        guard let uid = Auth.auth().currentUser?.uid else {
            listenPublic()
            return
        }
        followingListener = followRepository.observeFollowingIds(userId: uid) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                switch result {
                case .success(let ids):
                    let feedIds = ids + [uid]
                    self.postsListener?.remove()
                    self.postsListener = self.postsRepository.observeFollowingFeed(userIds: feedIds) { [weak self] postsResult in
                        DispatchQueue.main.async {
                            guard let self else { return }
                            self.isLoading = false
                            switch postsResult {
                            case .success(let posts):
                                let mapped = posts.map { $0.asUI() }
                                self.posts = mapped
                                LocalCacheStore.shared.savePosts(mapped)
                            case .failure(let error): self.error = error.localizedDescription
                            }
                        }
                    }
                    if ids.isEmpty { self.listenPublic() }
                case .failure:
                    self.listenPublic()
                }
            }
        }
        storiesListener = storiesRepository.observePublicActiveStories { [weak self] result in
            DispatchQueue.main.async {
                switch result { case .success(let stories): self?.storyGroups = stories.groupedForUI(); case .failure(let error): self?.error = error.localizedDescription }
            }
        }
    }

    private func listenPublic() {
        postsListener?.remove()
        postsListener = postsRepository.observePublicFeed { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }; self.isLoading = false
                switch result {
                case .success(let posts):
                    let mapped = posts.map { $0.asUI() }
                    self.posts = mapped
                    LocalCacheStore.shared.savePosts(mapped)
                case .failure(let error): self.error = error.localizedDescription
                }
            }
        }
    }

    func refresh() async { stop(); await loadFeed() }

    func toggleLike(postId: String) {
        Task { do { try await postsRepository.toggleLike(postId: postId) } catch { self.error = error.localizedDescription } }
    }

    func stop() { postsListener?.remove(); storiesListener?.remove(); followingListener?.remove(); postsListener = nil; storiesListener = nil; followingListener = nil }
}

enum TimeAgoFormatter {
    static func format(_ timestamp: Int64) -> String {
        let diffMs = Int64(Date().timeIntervalSince1970 * 1000) - timestamp
        guard diffMs > 0 else { return "justo ahora" }
        let minutes = diffMs / 60_000; let hours = minutes / 60; let days = hours / 24
        if days >= 365 { return "hace \(days / 365)a" }; if days >= 30 { return "hace \(days / 30)mes" }; if days >= 7 { return "hace \(days / 7)sem" }; if days > 0 { return "hace \(days)d" }; if hours > 0 { return "hace \(hours)h" }; if minutes > 0 { return "hace \(minutes)m" }; return "justo ahora"
    }
}
