import SwiftUI
import Combine
import FirebaseAuth
import FirebaseFirestore

@MainActor
class FeedViewModel: ObservableObject {
    @Published var posts: [PostUI] = []
    @Published var storyGroups: [StoryGroupUI] = []
    @Published var isLoading = false
    @Published var error: String? = nil
    @Published var isLoadingMore = false
    @Published var hasMore = true

    private let postsRepository = PostRepository()
    private let storiesRepository = StoryRepository()
    private let followRepository = FollowRepository()
    private var postsListener: ListenerRegistration?
    private var storiesListener: ListenerRegistration?
    private var followingListener: ListenerRegistration?
    private var feedUserIds: [String] = []
    private var publicStories: [FirestoreStory] = []
    private var closeFriendStories: [FirestoreStory] = []

    private func updateStoryGroups() {
        let unique = Dictionary((publicStories + closeFriendStories).map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        storyGroups = Array(unique.values).groupedForUI()
    }

    func loadFeed() async {
        if posts.isEmpty {
            posts = LocalCacheStore.shared.loadPosts()
            if !posts.isEmpty { Task { self.posts = await SignedURLRefreshService.shared.refresh(posts: self.posts) } }
        }
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
                    self.feedUserIds = ids + [uid]
                    Task {
                        do {
                            let page = try await self.postsRepository.fetchFollowingPage(userIds: self.feedUserIds)
                            self.posts = page.map { $0.asUI() }; self.hasMore = page.count >= 30; self.isLoading = false
                            self.closeFriendStories = (try? await self.storiesRepository.fetchCloseFriendsStories(ownerIds: ids)) ?? []
                            self.updateStoryGroups()
                            LocalCacheStore.shared.savePosts(self.posts)
                        } catch { self.error = error.localizedDescription; self.isLoading = false }
                    }
                case .failure:
                    self.listenPublic()
                }
            }
        }
        storiesListener = storiesRepository.observePublicActiveStories { [weak self] result in
            DispatchQueue.main.async {
                switch result { case .success(let stories): self?.publicStories = stories; self?.updateStoryGroups(); case .failure(let error): self?.error = error.localizedDescription }
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

    func refresh() async { stop(); posts = []; hasMore = true; await loadFeed() }

    func loadMoreIfNeeded(current post: PostUI) async {
        guard post.id == posts.last?.id, hasMore, !isLoadingMore, let last = posts.last else { return }
        isLoadingMore = true
        do {
            let page = feedUserIds.isEmpty
                ? try await postsRepository.fetchPublicPage(before: last.timestamp)
                : try await postsRepository.fetchFollowingPage(userIds: feedUserIds, before: last.timestamp)
            let existing = Set(posts.map(\.id)); posts.append(contentsOf: page.map { $0.asUI() }.filter { !existing.contains($0.id) })
            hasMore = page.count >= 30; LocalCacheStore.shared.savePosts(posts)
        } catch { self.error = error.localizedDescription }
        isLoadingMore = false
    }

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
