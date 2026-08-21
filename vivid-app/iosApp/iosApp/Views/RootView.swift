import SwiftUI

/**
 * Vista raíz que decide entre Auth y MainTabView según el estado de sesión.
 */
struct RootView: View {
    @EnvironmentObject var appState: AppState
    @AppStorage("onboarding.permissions.complete") private var permissionsComplete = false

    var body: some View {
        ZStack {
            if appState.isLoading {
                SplashScreen()
            } else if appState.isAuthenticated && !permissionsComplete {
                PermissionOnboardingView(isComplete: $permissionsComplete)
            } else if appState.isAuthenticated {
                MainTabView()
            } else {
                AuthView()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: appState.isAuthenticated)
        .animation(.easeInOut(duration: 0.2), value: appState.isLoading)
    }
}

/**
 * Pantalla de carga inicial mientras se verifica la sesión.
 */
struct SplashScreen: View {
    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "sparkles")
                    .font(.system(size: 60))
                    .foregroundStyle(VividTheme.accentGradient)

                Text("Vivid")
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                ProgressView()
                    .tint(.white)
                    .padding(.top, 8)
            }
        }
    }
}
