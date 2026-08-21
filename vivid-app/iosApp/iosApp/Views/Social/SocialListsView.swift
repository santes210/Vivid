import SwiftUI
import FirebaseAuth
import FirebaseFirestore
import PhotosUI

struct FollowersFollowingView: View {
    let userId: String
    let initialTab: Tab
    enum Tab { case followers, following }

    @State private var tab: Tab
    @State private var followers: [SocialUserPreview] = []
    @State private var following: [SocialUserPreview] = []
    private let follow = FollowRepository()

    init(userId: String, initialTab: Tab = .followers) {
        self.userId = userId
        self.initialTab = initialTab
        _tab = State(initialValue: initialTab)
    }

    var body: some View {
        VStack {
            Picker("", selection: $tab) {
                Text("Seguidores").tag(Tab.followers)
                Text("Siguiendo").tag(Tab.following)
            }
            .pickerStyle(.segmented)
            .padding()

            List(tab == .followers ? followers : following) { user in
                NavigationLink(destination: ProfileView(userId: user.id)) {
                    SocialRow(user: user)
                }
                .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
        .background(VividTheme.backgroundGradient.ignoresSafeArea())
        .navigationTitle(tab == .followers ? "Seguidores" : "Siguiendo")
        .task { await load() }
    }

    private func load() async {
        let followerIds: [String] = await withCheckedContinuation { cont in
            var once: ListenerRegistration?
            once = follow.observeFollowers(userId: userId) { result in
                once?.remove()
                if case .success(let ids) = result { cont.resume(returning: ids) } else { cont.resume(returning: []) }
            }
        }
        let followingIds: [String] = await withCheckedContinuation { cont in
            var once: ListenerRegistration?
            once = follow.observeFollowingIds(userId: userId) { result in
                once?.remove()
                if case .success(let ids) = result { cont.resume(returning: ids) } else { cont.resume(returning: []) }
            }
        }
        followers = await follow.loadUserPreviews(ids: followerIds)
        following = await follow.loadUserPreviews(ids: followingIds)
    }
}

struct SocialRow: View {
    let user: SocialUserPreview
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: user.avatarURL)) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Circle().fill(VividTheme.surfaceVariant)
                    .overlay(Text(String(user.username.prefix(1)).uppercased()).foregroundStyle(.white))
            }
            .frame(width: 44, height: 44)
            .clipShape(Circle())
            VStack(alignment: .leading) {
                Text(user.username).font(.subheadline.bold()).foregroundStyle(.white)
                Text(user.displayName).font(.caption).foregroundStyle(.white.opacity(0.6))
            }
        }
    }
}

struct FollowRequestsView: View {
    @State private var requests: [SocialUserPreview] = []
    private let follow = FollowRepository()

    var body: some View {
        List(requests) { user in
            HStack {
                NavigationLink(destination: ProfileView(userId: user.id)) { SocialRow(user: user) }
                Spacer()
                Button("Aceptar") { Task { try? await follow.acceptFollowRequest(requesterId: user.id) } }
                    .font(.caption.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Capsule().fill(VividTheme.primary))
                Button("Rechazar") { Task { try? await follow.rejectFollowRequest(requesterId: user.id) } }
                    .font(.caption).foregroundStyle(.white.opacity(0.7))
            }
            .listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .background(VividTheme.backgroundGradient.ignoresSafeArea())
        .navigationTitle("Solicitudes")
        .onAppear {
            _ = follow.observeFollowRequests { result in
                DispatchQueue.main.async {
                    if case .success(let users) = result { requests = users }
                }
            }
        }
    }
}

struct UserPickerView: View {
    let onPick: (SocialUserPreview) -> Void
    @State private var query = ""
    @State private var results: [User] = []
    private let users = UserRepository()
    @State private var listener: ListenerRegistration?

    var body: some View {
        NavigationStack {
            List(results) { user in
                Button {
                    onPick(SocialUserPreview(id: user.uid, username: user.username, displayName: user.displayName, avatarURL: user.avatarUrl))
                } label: {
                    HStack {
                        Text(user.username).foregroundStyle(.white)
                        Spacer()
                    }
                }
                .listRowBackground(Color.clear)
            }
            .scrollContentBackground(.hidden)
            .searchable(text: $query)
            .onChange(of: query) { value in
                listener?.remove()
                listener = users.observeUsernameSearch(prefix: value) { result in
                    DispatchQueue.main.async {
                        if case .success(let found) = result { results = found.map(User.init(profile:)) }
                    }
                }
            }
            .navigationTitle("Nuevo chat")
        }
    }
}

struct ReportSheet: View {
    let targetType: String
    let targetId: String
    @Environment(\.dismiss) private var dismiss
    @State private var reason = ""
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                TextField("Motivo", text: $reason, axis: .vertical)
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Reportar")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Enviar") {
                        Task {
                            do {
                                try await ContentActionsRepository().report(targetType: targetType, targetId: targetId, reason: reason)
                                dismiss()
                            } catch {
                                self.error = error.localizedDescription
                            }
                        }
                    }
                }
            }
        }
    }
}

struct BlockedUsersView: View {
    @State private var users: [SocialUserPreview] = []
    private let follow = FollowRepository()

    var body: some View {
        List(users) { user in
            HStack {
                SocialRow(user: user)
                Spacer()
                Button("Desbloquear") { Task { try? await follow.unblockUser(targetUserId: user.id); await load() } }
                    .foregroundStyle(VividTheme.primary)
            }
            .listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .background(VividTheme.backgroundGradient.ignoresSafeArea())
        .navigationTitle("Bloqueados")
        .task { await load() }
    }

    private func load() async {
        let ids: Set<String> = await withCheckedContinuation { cont in
            var once: ListenerRegistration?
            once = follow.observeBlockedIds { result in
                once?.remove()
                if case .success(let set) = result { cont.resume(returning: set) } else { cont.resume(returning: []) }
            }
        }
        users = await follow.loadUserPreviews(ids: Array(ids))
    }
}

struct SavedPostsView: View {
    @State private var posts: [PostUI] = []
    private let actions = ContentActionsRepository()
    private let postsRepo = PostRepository()

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 2) {
                ForEach(posts) { post in ExploreGridItem(post: post) }
            }
        }
        .background(VividTheme.backgroundGradient.ignoresSafeArea())
        .navigationTitle("Guardados")
        .onAppear {
            _ = actions.observeSavedPostIds { result in
                guard case .success(let ids) = result else { return }
                Task {
                    var loaded: [PostUI] = []
                    for id in ids {
                        if let post = try? await postsRepo.fetchPost(id: id) {
                            loaded.append(post.asUI())
                        }
                    }
                    await MainActor.run { posts = loaded }
                }
            }
        }
    }
}
