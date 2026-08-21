import SwiftUI

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
        // Firebase se configura aquí. En un proyecto real:
        // FirebaseApp.configure()
        // Luego se crea el SharedContainer con las implementaciones iOS.
    }
}
