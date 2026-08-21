import SwiftUI

/**
 * Feed principal: stories + posts.
 * Equivalente al FeedScreen de Android con Jetpack Compose.
 */
struct FeedView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = FeedViewModel()
    @State private var showStoryViewer = false
    @State private var storyIndex = 0

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            ScrollView {
                LazyVStack(spacing: 0) {
                    // Barra de stories
                    StoriesBar(storyGroups: viewModel.storyGroups) { index in
                        showStoryViewer = true
                        storyIndex = index
                    }
                        .padding(.vertical, 8)

                    Divider()
                        .background(.white.opacity(0.1))

                    // Posts
                    ForEach(viewModel.posts) { post in
                        PostCard(post: post) { viewModel.toggleLike(postId: post.id) }
                            .padding(.vertical, 4)
                    }

                    if viewModel.isLoading {
                        ProgressView()
                            .tint(.white)
                            .padding(32)
                    }

                    if viewModel.posts.isEmpty && !viewModel.isLoading {
                        EmptyFeedView()
                    }
                }
            }
            .refreshable {
                await viewModel.refresh()
            }
        }
        .navigationTitle("Vivid")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                NavigationLink(destination: ChatListView()) {
                    Image(systemName: "message.fill")
                        .foregroundStyle(.white)
                }
            }
        }
        .task {
            await viewModel.loadFeed()
        }
        .fullScreenCover(isPresented: $showStoryViewer) {
            StoryViewerView(storyGroups: viewModel.storyGroups, initialGroupIndex: storyIndex)
        }
    }
}

// MARK: - Stories Bar

struct StoriesBar: View {
    let storyGroups: [StoryGroupUI]
    var onSelect: (Int) -> Void = { _ in }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                YourStoryButton()
                ForEach(Array(storyGroups.enumerated()), id: \.element.id) { index, group in
                    Button { onSelect(index) } label: { StoryAvatar(group: group) }
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

struct StoryGroupUI: Identifiable {
    let id: String
    let userId: String
    let username: String
    let avatarUrl: String
    let stories: [StoryUI]
    var hasUnseen: Bool = true
}

struct StoryUI: Identifiable {
    let id: String
    let mediaUrl: String
    let type: String
    let caption: String
    let createdAt: Int64
}

struct YourStoryButton: View {
    var body: some View {
        VStack(spacing: 6) {
            ZStack(alignment: .bottomTrailing) {
                Circle()
                    .fill(VividTheme.surfaceVariant)
                    .frame(width: 68, height: 68)
                    .overlay(
                        Image(systemName: "person.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(.white.opacity(0.5))
                    )

                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(VividTheme.primary)
                    .background(Circle().fill(VividTheme.surface).frame(width: 20, height: 20))
            }

            Text("Tu story")
                .font(VividTheme.caption(10))
                .foregroundStyle(.white.opacity(0.7))
        }
    }
}

struct StoryAvatar: View {
    let group: StoryGroupUI

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .strokeBorder(
                        group.hasUnseen
                            ? AnyShapeStyle(VividTheme.storyRingGradient)
                            : AnyShapeStyle(.white.opacity(0.2)),
                        lineWidth: group.hasUnseen ? 3 : 1.5
                    )
                    .frame(width: 72, height: 72)

                AsyncImage(url: URL(string: group.avatarUrl)) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Circle().fill(VividTheme.surfaceVariant)
                        .overlay(
                            Text(String(group.username.prefix(1)).uppercased())
                                .font(.headline)
                                .foregroundStyle(.white)
                        )
                }
                .frame(width: 62, height: 62)
                .clipShape(Circle())
            }

            Text(group.username)
                .font(VividTheme.caption(10))
                .foregroundStyle(.white.opacity(0.7))
                .lineLimit(1)
        }
    }
}

// MARK: - Post Card

struct PostUI: Identifiable {
    let id: String
    let userId: String
    let username: String
    let userProfilePicture: String
    let imageUrl: String
    let caption: String
    var likesCount: Int
    var commentsCount: Int
    let timestamp: Int64
    var isLiked: Bool
    let isVideo: Bool
    let videoUrl: String
    let thumbnailUrl: String
}

struct PostCard: View {
    let post: PostUI
    let onLike: () -> Void
    @State private var isLiked: Bool
    @State private var likesCount: Int
    @State private var showComments = false
    @State private var showReport = false
    @State private var isSaved = false

    init(post: PostUI, onLike: @escaping () -> Void) {
        self.post = post
        self.onLike = onLike
        _isLiked = State(initialValue: post.isLiked)
        _likesCount = State(initialValue: post.likesCount)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header: avatar + username + timestamp
            HStack(spacing: 12) {
                NavigationLink(destination: ProfileView(userId: post.userId)) {
                    AsyncImage(url: URL(string: post.userProfilePicture)) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Circle().fill(VividTheme.surfaceVariant)
                            .overlay(
                                Text(String(post.username.prefix(1)).uppercased())
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                            )
                    }
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(post.username)
                        .font(.subheadline.bold())
                        .foregroundStyle(.white)

                    Text(TimeAgoFormatter.format(post.timestamp))
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.5))
                }

                Spacer()

                Menu {
                    Button("Reportar") { showReport = true }
                    Button("Bloquear autor") { Task { try? await FollowRepository().blockUser(targetUserId: post.userId) } }
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundStyle(.white.opacity(0.6))
                        .padding(8)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            // Media
            ZStack {
                if post.isVideo, let url = URL(string: post.videoUrl), !post.videoUrl.isEmpty {
                    LoopingVideoPlayer(url: url, isPlaying: true, isMuted: true)
                        .frame(height: 360)
                        .clipped()
                } else {
                    AsyncImage(url: URL(string: post.imageUrl)) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(VividTheme.surfaceVariant)
                            .overlay(ProgressView().tint(.white))
                    }
                    .frame(height: 360)
                    .clipped()
                }
            }

            // Acciones: like, comment, share
            HStack(spacing: 20) {
                Button(action: toggleLike) {
                    Image(systemName: isLiked ? "heart.fill" : "heart")
                        .font(.system(size: 24))
                        .foregroundStyle(isLiked ? VividTheme.secondary : .white)
                        .scaleEffect(isLiked ? 1.1 : 1.0)
                        .animation(.spring(response: 0.3), value: isLiked)
                }

                Button(action: { showComments = true }) {
                    Image(systemName: "bubble.right")
                        .font(.system(size: 22))
                        .foregroundStyle(.white)
                }

                Button(action: {}) {
                    Image(systemName: "paperplane")
                        .font(.system(size: 22))
                        .foregroundStyle(.white)
                }

                Spacer()

                Button(action: {
                    isSaved.toggle()
                    Task { try? await ContentActionsRepository().toggleSave(postId: post.id) }
                }) {
                    Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                        .font(.system(size: 22))
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            // Likes y caption
            VStack(alignment: .leading, spacing: 6) {
                Text("\(likesCount) Me gusta")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)

                if !post.caption.isEmpty {
                    HStack(alignment: .top, spacing: 4) {
                        Text(post.username)
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                        Text(post.caption)
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }

                if post.commentsCount > 0 {
                    Text("Ver los \(post.commentsCount) comentarios")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.5))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
        }
        .sheet(isPresented: $showComments) {
            CommentsView(postId: post.id, postOwnerUsername: post.username)
        }
        .sheet(isPresented: $showReport) {
            ReportSheet(targetType: "post", targetId: post.id)
        }
    }

    private func toggleLike() {
        isLiked.toggle()
        likesCount += isLiked ? 1 : -1
        onLike()
    }
}

// MARK: - Empty State

struct EmptyFeedView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 48))
                .foregroundStyle(.white.opacity(0.3))

            Text("Tu feed está vacío")
                .font(VividTheme.heading())
                .foregroundStyle(.white)

            Text("Sigue a personas para ver sus publicaciones aquí")
                .font(VividTheme.body(14))
                .foregroundStyle(.white.opacity(0.5))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 48)
        }
        .padding(.vertical, 80)
    }
}
