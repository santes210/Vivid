import SwiftUI
import FirebaseCore

/**
 * Punto de entrada de la app Vivid para iOS.
 * Configura Firebase y el contenedor de dependencias compartidas.
 */
@main
struct VividApp: App {
    @StateObject private var appState = AppState()

    init() {
        // Configurar Firebase (lee GoogleService-Info.plist automáticamente)
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appState)
                .preferredColorScheme(.dark)
                .task {
                    await appState.restoreSession()
                }
        }
    }
}