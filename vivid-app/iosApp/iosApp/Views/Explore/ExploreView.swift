import SwiftUI
import FirebaseFirestore

/**
 * Pantalla de explorar: búsqueda de usuarios y contenido trending.
 * Equivalente al ExploreScreen de Android.
 */
struct ExploreView: View {
    @StateObject private var viewModel = ExploreViewModel()
    @State private var searchText = ""

    private let columns = [
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2),
        GridItem(.flexible(), spacing: 2)
    ]

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Barra de búsqueda
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.white.opacity(0.5))

                    TextField("Buscar personas, posts...", text: $searchText)
                        .foregroundStyle(.white)
                        .autocorrectionDisabled()

                    if !searchText.isEmpty {
                        Button(action: { searchText = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.white.opacity(0.5))
                        }
                    }
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: VividTheme.cornerRadiusMD, style: .continuous)
                        .fill(.white.opacity(0.08))
                )
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // Grid de contenido
                ScrollView {
                    if searchText.isEmpty {
                        // Categorías
                        LazyVGrid(columns: columns, spacing: 2) {
                            ForEach(viewModel.explorePosts) { post in
                                ExploreGridItem(post: post)
                            }
                        }
                    } else {
                        // Resultados de búsqueda
                        LazyVStack(spacing: 0) {
                            ForEach(viewModel.searchResults) { user in
                                UserSearchRow(user: user)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                            }

                            if viewModel.searchResults.isEmpty {
                                Text("No se encontraron resultados")
                                    .font(VividTheme.body(14))
                                    .foregroundStyle(.white.opacity(0.5))
                                    .padding(40)
                            }
                        }
                    }
                }
            }
        }
        .onChange(of: searchText) { newValue in
            viewModel.search(query: newValue)
        }
    }
}

struct ExploreGridItem: View {
    let post: PostUI

    var body: some View {
        ZStack(alignment: .topTrailing) {
            AsyncImage(url: URL(string: post.imageUrl)) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Rectangle()
                    .fill(VividTheme.surfaceVariant)
                    .overlay(
                        Image(systemName: "photo")
                            .foregroundStyle(.white.opacity(0.2))
                    )
            }
            .frame(height: 130)
            .clipped()

            if post.isVideo {
                Image(systemName: "play.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(.white)
                    .padding(6)
            }
        }
    }
}

struct UserSearchRow: View {
    let user: User

    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: user.avatarUrl)) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Circle().fill(VividTheme.surfaceVariant)
                    .overlay(
                        Text(String(user.username.prefix(1)).uppercased())
                            .font(.headline)
                            .foregroundStyle(.white)
                    )
            }
            .frame(width: 48, height: 48)
            .clipShape(Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(user.username)
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                Text(user.displayName)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.6))
            }

            Spacer()

            Button("Seguir") {}
                .font(.caption.bold())
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Capsule().fill(VividTheme.primary))
        }
    }
}

@MainActor
class ExploreViewModel: ObservableObject {
    @Published var explorePosts: [PostUI] = []
    @Published var searchResults: [User] = []
    private let postsRepository = PostRepository()
    private let usersRepository = UserRepository()
    private var contentListener: ListenerRegistration?
    private var searchListener: ListenerRegistration?

    init() { loadExploreContent() }

    func search(query: String) {
        searchListener?.remove(); searchListener = nil
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { searchResults = []; return }
        searchListener = usersRepository.observeUsernameSearch(prefix: query) { [weak self] result in
            DispatchQueue.main.async { if case .success(let users) = result { self?.searchResults = users.map(User.init(profile:)) } }
        }
    }

    private func loadExploreContent() {
        contentListener = postsRepository.observePublicFeed(limit: 90) { [weak self] result in
            DispatchQueue.main.async { if case .success(let posts) = result { self?.explorePosts = posts.map { $0.asUI() } } }
        }
    }
}
