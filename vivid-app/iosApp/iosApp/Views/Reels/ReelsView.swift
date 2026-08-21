import SwiftUI

/**
 * Pantalla de Reels: scroll vertical de videos cortos.
 * Equivalente al ReelsScreen de Android con VerticalPager.
 * Usa TabView con PageTabViewStyle para el efecto de scroll vertical.
 */
struct ReelsView: View {
    @StateObject private var viewModel = ReelsViewModel()
    @State private var currentReelIndex = 0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            TabView(selection: $currentReelIndex) {
                ForEach(Array(viewModel.reels.enumerated()), id: \.offset) { index, reel in
                    ReelCard(reel: reel)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea()

            // Overlay de UI (controles, info del reel)
            VStack {
                // Header
                HStack {
                    Text("Reels")
                        .font(VividTheme.title(24))
                        .foregroundStyle(.white)
                    Spacer()
                    Image(systemName: "camera.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(.white)
                        .padding(10)
                        .background(Circle().fill(.white.opacity(0.15)))
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                Spacer()
            }
        }
        .navigationBarHidden(true)
        .task {
            await viewModel.loadReels()
        }
    }
}

struct ReelCard: View {
    let reel: ReelUI
    @State private var isLiked = false
    @State private var likesCount: Int
    @State private var isPaused = false

    init(reel: ReelUI) {
        self.reel = reel
        _likesCount = State(initialValue: reel.likes)
    }

    var body: some View {
        ZStack {
            // Video placeholder (en producción: AVPlayer)
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [.black, VividTheme.surfaceVariant, .black],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .overlay(
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 60))
                        .foregroundStyle(.white.opacity(0.1))
                )

            // Gradiente inferior para legibilidad
            LinearGradient(
                colors: [.clear, .black.opacity(0.7)],
                startPoint: .center,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Contenido del reel
            HStack(alignment: .bottom) {
                // Info del usuario y caption
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        AsyncImage(url: URL(string: reel.userAvatar)) { image in
                            image.resizable().scaledToFill()
                        } placeholder: {
                            Circle().fill(VividTheme.surfaceVariant)
                                .overlay(
                                    Text(String(reel.username.prefix(1)).uppercased())
                                        .font(.caption.bold())
                                        .foregroundStyle(.white)
                                )
                        }
                        .frame(width: 36, height: 36)
                        .clipShape(Circle())

                        Text("@\(reel.username)")
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)

                        Button("Seguir") {}
                            .font(.caption.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Capsule().stroke(.white, lineWidth: 1))
                    }

                    if !reel.caption.isEmpty {
                        Text(reel.caption)
                            .font(.subheadline)
                            .foregroundStyle(.white)
                            .lineLimit(3)
                    }
                }
                .padding(.bottom, 40)
                .padding(.leading, 16)

                Spacer()

                // Acciones laterales
                VStack(spacing: 24) {
                    Button(action: toggleLike) {
                        VStack(spacing: 4) {
                            Image(systemName: isLiked ? "heart.fill" : "heart")
                                .font(.system(size: 28))
                                .foregroundStyle(isLiked ? VividTheme.secondary : .white)
                            Text("\(likesCount)")
                                .font(.caption)
                                .foregroundStyle(.white)
                        }
                    }

                    Button(action: {}) {
                        VStack(spacing: 4) {
                            Image(systemName: "bubble.right")
                                .font(.system(size: 26))
                                .foregroundStyle(.white)
                            Text("\(reel.commentsCount)")
                                .font(.caption)
                                .foregroundStyle(.white)
                        }
                    }

                    Button(action: {}) {
                        Image(systemName: "paperplane")
                            .font(.system(size: 26))
                            .foregroundStyle(.white)
                    }

                    Button(action: {}) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 22))
                            .foregroundStyle(.white)
                    }
                }
                .padding(.bottom, 40)
                .padding(.trailing, 12)
            }
        }
        .onTapGesture {
            isPaused.toggle()
        }
    }

    private func toggleLike() {
        isLiked.toggle()
        likesCount += isLiked ? 1 : -1
    }
}

struct ReelUI: Identifiable {
    let id: String
    let userId: String
    let username: String
    let userAvatar: String
    let videoUrl: String
    let thumbnailUrl: String
    let caption: String
    let likes: Int
    let commentsCount: Int
}

@MainActor
class ReelsViewModel: ObservableObject {
    @Published var reels: [ReelUI] = []
    @Published var isLoading = false

    func loadReels() async {
        isLoading = true
        defer { isLoading = false }

        // En producción: usar ContentRepository
        reels = (0..<10).map { i in
            ReelUI(
                id: "reel_\(i)",
                userId: "user_\(i)",
                username: ["maria", "carlos", "luna", "diego", "sofia", "pablo", "elena", "marco", "laura", "ivan"][i],
                userAvatar: "",
                videoUrl: "",
                thumbnailUrl: "",
                caption: [
                    "Nuevo trend 🕺 #viral",
                    "Atardecer en la playa 🌅",
                    "Receta fácil 🍳 #food",
                    "Cover acústico 🎸",
                    "Outfit del día 👗",
                    "Skate session 🛹",
                    "Arte digital ✨",
                    "Dance challenge 💃",
                    "Travel vlog ✈️",
                    "Comedia 😂"
                ][i],
                likes: Int.random(in: 100...10000),
                commentsCount: Int.random(in: 10...500)
            )
        }
    }
}
