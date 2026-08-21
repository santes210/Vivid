import SwiftUI
import FirebaseCore
import FirebaseAuth

/**
 * Punto de entrada de la app Vivid para iOS.
 * Configura Firebase y el contenedor de dependencias compartidas.
 */
@main
struct VividApp: App {
    @StateObject private var appState = AppState()

    init() {
        configureDependencies()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .preferredColorScheme(.dark)
        }
    }

    private func configureDependencies() {
        // Configurar Firebase (lee GoogleService-Info.plist automáticamente)
        FirebaseApp.configure()

        // Verificar sesión existente al arrancar
        if let currentUser = Auth.auth().currentUser {
            // Cargar datos del usuario desde Firestore
            let user = User(
                uid: currentUser.uid,
                username: currentUser.displayName ?? currentUser.email?.components(separatedBy: "@").first ?? "usuario",
                displayName: currentUser.displayName ?? "Usuario",
                bio: "",
                avatarUrl: currentUser.photoURL?.absoluteString ?? "",
                avatarBase64: "",
                email: currentUser.email ?? "",
                followersCount: 0,
                followingCount: 0,
                postsCount: 0,
                isPrivate: false
            )
            appState.signIn(user: user)
        } else {
            appState.updateLoading(false)
        }
    }
}