import SwiftUI

/**
 * Pantalla de perfil de usuario.
 * Muestra información del perfil, estadísticas y grid de publicaciones.
 * Equivalente al ProfileScreen de Android.
 */
struct ProfileView: View {
    let userId: String
    @StateObject private var viewModel = ProfileViewModel()
    @State private var selectedTab: ProfileTab = .posts

    enum ProfileTab: String, CaseIterable {
        case posts = "Publicaciones"
        case reels = "Reels"
        case tagged = "Etiquetas"
    }

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    // Header del perfil
                    ProfileHeader(user: viewModel.user, isCurrentUser: viewModel.isCurrentUser)

                    // Estadísticas
                    ProfileStats(
                        postsCount: viewModel.user?.postsCount ?? 0,
                        followersCount: viewModel.user?.followersCount ?? 0,
                        followingCount: viewModel.user?.followingCount ?? 0
                    )
                    .padding(.vertical, 16)

                    // Botones de acción
                    if viewModel.isCurrentUser {
                        HStack(spacing: 12) {
                            Button("Editar perfil") {}
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(.white.opacity(0.1))
                                )

                            NavigationLink(destination: SettingsView()) {
                                Image(systemName: "gearshape.fill")
                                    .foregroundStyle(.white)
                                    .frame(width: 44, height: 38)
                                    .background(
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(.white.opacity(0.1))
                                    )
                            }
                        }
                        .padding(.horizontal, 16)
                    } else {
                        HStack(spacing: 12) {
                            Button("Seguir") {}
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Capsule().fill(VividTheme.primary))

                            Button("Mensaje") {}
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(
                                    Capsule().fill(.white.opacity(0.1))
                                )
                        }
                        .padding(.horizontal, 16)
                    }

                    // Tabs de contenido
                    HStack(spacing: 0) {
                        ForEach(ProfileTab.allCases, id: \.rawValue) { tab in
                            Button(action: { selectedTab = tab }) {
                                VStack(spacing: 8) {
                                    Image(systemName: tabIcon(tab))
                                        .font(.system(size: 20))
                                        .foregroundStyle(selectedTab == tab ? .white : .white.opacity(0.4))

                                    Rectangle()
                                        .fill(selectedTab == tab ? VividTheme.primary : .clear)
                                        .frame(height: 2)
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                    }
                    .padding(.top, 20)

                    // Grid de publicaciones
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: 2),
                            GridItem(.flexible(), spacing: 2),
                            GridItem(.flexible(), spacing: 2)
                        ],
                        spacing: 2
                    ) {
                        ForEach(viewModel.userPosts) { post in
                            ExploreGridItem(post: post)
                        }
                    }

                    if viewModel.userPosts.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "photo.on.rectangle")
                                .font(.system(size: 40))
                                .foregroundStyle(.white.opacity(0.2))
                            Text("Aún no hay publicaciones")
                                .font(VividTheme.body(14))
                                .foregroundStyle(.white.opacity(0.4))
                        }
                        .padding(.vertical, 60)
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if viewModel.isCurrentUser {
                    NavigationLink(destination: SettingsView()) {
                        Image(systemName: "line.3.horizontal")
                            .foregroundStyle(.white)
                    }
                }
            }
        }
        .task {
            await viewModel.loadProfile(userId: userId)
        }
    }

    private func tabIcon(_ tab: ProfileTab) -> String {
        switch tab {
        case .posts: return "square.grid.3x3"
        case .reels: return "play.rectangle"
        case .tagged: return "person.crop.rectangle"
        }
    }
}

// MARK: - Profile Header

struct ProfileHeader: View {
    let user: User?
    let isCurrentUser: Bool

    var body: some View {
        HStack(spacing: 20) {
            // Avatar
            ZStack {
                Circle()
                    .strokeBorder(VividTheme.accentGradient, lineWidth: 3)
                    .frame(width: 90, height: 90)

                AsyncImage(url: URL(string: user?.avatarUrl ?? "")) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Circle().fill(VividTheme.surfaceVariant)
                        .overlay(
                            Image(systemName: "person.fill")
                                .font(.system(size: 32))
                                .foregroundStyle(.white.opacity(0.5))
                        )
                }
                .frame(width: 80, height: 80)
                .clipShape(Circle())
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(user?.displayName ?? "Usuario")
                    .font(VividTheme.heading(18))
                    .foregroundStyle(.white)

                Text("@\(user?.username ?? "usuario")")
                    .font(VividTheme.caption(13))
                    .foregroundStyle(.white.opacity(0.6))

                if let bio = user?.bio, !bio.isEmpty {
                    Text(bio)
                        .font(VividTheme.body(13))
                        .foregroundStyle(.white.opacity(0.8))
                        .lineLimit(3)
                        .padding(.top, 2)
                }
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

// MARK: - Profile Stats

struct ProfileStats: View {
    let postsCount: Int
    let followersCount: Int
    let followingCount: Int

    var body: some View {
        HStack(spacing: 0) {
            StatItem(count: postsCount, label: "Publicaciones")
            Divider().frame(height: 30).background(.white.opacity(0.1))
            StatItem(count: followersCount, label: "Seguidores")
            Divider().frame(height: 30).background(.white.opacity(0.1))
            StatItem(count: followingCount, label: "Siguiendo")
        }
        .padding(.horizontal, 16)
    }
}

struct StatItem: View {
    let count: Int
    let label: String

    var body: some View {
        VStack(spacing: 4) {
            Text(formatCount(count))
                .font(VividTheme.heading(18))
                .foregroundStyle(.white)
            Text(label)
                .font(VividTheme.caption(11))
                .foregroundStyle(.white.opacity(0.5))
        }
        .frame(maxWidth: .infinity)
    }

    private func formatCount(_ count: Int) -> String {
        if count < 1000 { return "\(count)" }
        if count < 1_000_000 {
            let k = Double(count) / 1000.0
            return String(format: "%.1fK", k)
        }
        let m = Double(count) / 1_000_000.0
        return String(format: "%.1fM", m)
    }
}

@MainActor
class ProfileViewModel: ObservableObject {
    @Published var user: User? = nil
    @Published var userPosts: [PostUI] = []
    @Published var isCurrentUser = true
    @Published var isLoading = false

    func loadProfile(userId: String) async {
        isLoading = true
        defer { isLoading = false }

        // En producción: usar UserRepository
        user = User(
            uid: userId,
            username: "vivid_user",
            displayName: "Usuario Vivid",
            bio: "✨ Compartiendo momentos en Vivid",
            avatarUrl: "",
            avatarBase64: "",
            email: "",
            followersCount: 128,
            followingCount: 95,
            postsCount: 24,
            isPrivate: false
        )

        userPosts = (0..<12).map { i in
            PostUI(
                id: "profile_post_\(i)",
                userId: userId,
                username: user?.username ?? "",
                userProfilePicture: "",
                imageUrl: "",
                caption: "",
                likesCount: Int.random(in: 5...200),
                commentsCount: Int.random(in: 0...30),
                timestamp: Int64(Date().timeIntervalSince1970 * 1000),
                isLiked: false,
                isVideo: i % 4 == 0,
                videoUrl: "",
                thumbnailUrl: ""
            )
        }
    }
}
