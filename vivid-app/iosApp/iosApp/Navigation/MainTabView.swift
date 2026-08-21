import SwiftUI

/**
 * Navegación principal con TabView.
 * Equivalente al BottomNavigationBar de Android.
 */
struct MainTabView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        TabView(selection: $appState.selectedTab) {
            NavigationStack {
                FeedView()
            }
            .tabItem {
                Label("Inicio", systemImage: "house.fill")
            }
            .tag(AppState.AppTab.feed)

            NavigationStack {
                ExploreView()
            }
            .tabItem {
                Label("Explorar", systemImage: "magnifyingglass")
            }
            .tag(AppState.AppTab.explore)

            // Botón central de crear (sin navegación propia)
            NavigationStack {
                CreateMenuView()
            }
            .tabItem {
                Label("Crear", systemImage: "plus.circle.fill")
            }
            .tag(AppState.AppTab.create)

            NavigationStack {
                ReelsView()
            }
            .tabItem {
                Label("Reels", systemImage: "play.rectangle.fill")
            }
            .tag(AppState.AppTab.reels)

            NavigationStack {
                ProfileView(userId: appState.currentUser?.uid ?? "")
            }
            .tabItem {
                Label("Perfil", systemImage: "person.fill")
            }
            .tag(AppState.AppTab.profile)
        }
        .tint(VividTheme.primary)
    }
}

/**
 * Menú de creación: post, reel, story.
 */
struct CreateMenuView: View {
    @State private var showCreateOptions = false

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            VStack(spacing: 24) {
                Text("¿Qué quieres crear?")
                    .font(.title2.bold())
                    .foregroundStyle(.white)

                HStack(spacing: 20) {
                    CreateOptionButton(
                        icon: "photo.fill",
                        title: "Post",
                        color: .blue
                    ) {
                        showCreateOptions = true
                    }

                    CreateOptionButton(
                        icon: "video.fill",
                        title: "Reel",
                        color: .purple
                    ) {
                        showCreateOptions = true
                    }

                    CreateOptionButton(
                        icon: "sparkles",
                        title: "Story",
                        color: .orange
                    ) {
                        showCreateOptions = true
                    }
                }
            }
        }
        .fullScreenCover(isPresented: $showCreateOptions) { CameraView() }
    }
}

struct CreateOptionButton: View {
    let icon: String
    let title: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 28))
                    .foregroundStyle(.white)
                    .frame(width: 72, height: 72)
                    .background(
                        Circle()
                            .fill(color.gradient)
                    )

                Text(title)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
            }
        }
    }
}
