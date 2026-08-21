import SwiftUI
import Combine
import FirebaseAuth
import FirebaseFirestore

@MainActor
class AppState: ObservableObject {
    @Published var currentUser: User? = nil
    @Published var isAuthenticated = false
    @Published var isLoading = true
    @Published var selectedTab: AppTab = .feed
    @Published var navigationPath = NavigationPath()

    private let userRepository = UserRepository()
    private var userListener: ListenerRegistration?

    enum AppTab: String, CaseIterable { case feed = "Inicio", explore = "Explorar", create = "Crear", reels = "Reels", profile = "Perfil" }

    func restoreSession() async {
        guard let firebaseUser = Auth.auth().currentUser else { updateLoading(false); return }
        observeProfile(uid: firebaseUser.uid, fallback: firebaseUser)
        PushNotificationService.shared.registerCurrentToken()
        Task { await PushNotificationService.shared.requestPermissionAndRegister() }
    }

    func signIn(user: User) {
        currentUser = user
        isAuthenticated = true
        isLoading = false
        observeProfile(uid: user.uid, fallback: Auth.auth().currentUser)
        PushNotificationService.shared.registerCurrentToken()
        Task { await PushNotificationService.shared.requestPermissionAndRegister() }
    }

    func signOut() {
        if let uid = currentUser?.uid { PushNotificationService.shared.unregisterCurrentToken(for: uid) }
        userListener?.remove(); userListener = nil
        currentUser = nil; isAuthenticated = false; selectedTab = .feed; navigationPath = NavigationPath()
    }

    func updateLoading(_ loading: Bool) { isLoading = loading }

    private func observeProfile(uid: String, fallback: FirebaseAuth.User?) {
        userListener?.remove()
        userListener = userRepository.observeUser(id: uid) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                switch result {
                case .success(let profile):
                    if let profile { self.currentUser = User(profile: profile) }
                    else { self.currentUser = User(firebaseUser: fallback) }
                    self.isAuthenticated = true; self.isLoading = false
                case .failure:
                    self.currentUser = User(firebaseUser: fallback); self.isAuthenticated = self.currentUser != nil; self.isLoading = false
                }
            }
        }
    }
}

struct User: Identifiable, Codable, Equatable {
    let uid: String
    var id: String { uid }
    var username: String
    var displayName: String
    var bio: String
    var avatarUrl: String
    var avatarBase64: String
    var email: String
    var followersCount: Int
    var followingCount: Int
    var postsCount: Int
    var isPrivate: Bool

    init(uid: String, username: String, displayName: String, bio: String, avatarUrl: String, avatarBase64: String, email: String, followersCount: Int, followingCount: Int, postsCount: Int, isPrivate: Bool) {
        self.uid = uid; self.username = username; self.displayName = displayName; self.bio = bio; self.avatarUrl = avatarUrl; self.avatarBase64 = avatarBase64; self.email = email; self.followersCount = followersCount; self.followingCount = followingCount; self.postsCount = postsCount; self.isPrivate = isPrivate
    }

    init(profile: VividUser) {
        self.init(uid: profile.id, username: profile.username, displayName: profile.displayName, bio: profile.bio, avatarUrl: profile.avatarURL, avatarBase64: profile.avatarBase64, email: profile.email, followersCount: profile.followersCount, followingCount: profile.followingCount, postsCount: profile.postsCount, isPrivate: profile.isPrivate)
    }

    init?(firebaseUser: FirebaseAuth.User?) {
        guard let firebaseUser else { return nil }
        self.init(uid: firebaseUser.uid, username: firebaseUser.displayName ?? firebaseUser.email?.components(separatedBy: "@").first ?? "usuario", displayName: firebaseUser.displayName ?? "Usuario", bio: "", avatarUrl: firebaseUser.photoURL?.absoluteString ?? "", avatarBase64: "", email: firebaseUser.email ?? "", followersCount: 0, followingCount: 0, postsCount: 0, isPrivate: false)
    }

    static func empty() -> User { User(uid: "", username: "", displayName: "", bio: "", avatarUrl: "", avatarBase64: "", email: "", followersCount: 0, followingCount: 0, postsCount: 0, isPrivate: false) }
}
