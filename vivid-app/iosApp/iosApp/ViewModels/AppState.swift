import SwiftUI
import Combine

/**
 * Estado global de la aplicación.
 * Equivalente al AppState en Android (ViewModel de nivel de aplicación).
 *
 * Gestiona:
 * - Sesión del usuario (auth)
 * - Tema y configuración visual
 * - Navegación de alto nivel
 */
@MainActor
class AppState: ObservableObject {
    @Published var currentUser: User? = nil
    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = true
    @Published var selectedTab: AppTab = .feed

    // Navegación
    @Published var navigationPath = NavigationPath()

    enum AppTab: String, CaseIterable {
        case feed = "Inicio"
        case explore = "Explorar"
        case create = "Crear"
        case reels = "Reels"
        case profile = "Perfil"
    }

    func signIn(user: User) {
        self.currentUser = user
        self.isAuthenticated = true
        self.isLoading = false
    }

    func signOut() {
        self.currentUser = nil
        self.isAuthenticated = false
        self.selectedTab = .feed
        self.navigationPath = NavigationPath()
    }

    func updateLoading(_ loading: Bool) {
        self.isLoading = loading
    }
}

/**
 * Modelo de usuario (reflejo del modelo Kotlin compartido).
 * En un proyecto real, este vendría del framework Shared.
 */
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

    static func empty() -> User {
        User(
            uid: "", username: "", displayName: "", bio: "",
            avatarUrl: "", avatarBase64: "", email: "",
            followersCount: 0, followingCount: 0, postsCount: 0,
            isPrivate: false
        )
    }
}
