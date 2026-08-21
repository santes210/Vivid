import SwiftUI
import FirebaseAuth

/**
 * Visor de stories con transición automática y gestos.
 * Equivalente al StoryViewerScreen de Android.
 *
 * Features:
 * - Avance automático con barra de progreso
 * - Tap izquierda/derecha para navegar
 * - Mantener presionado para pausar
 * - Swipe down para cerrar
 * - Respuesta a story (reply)
 */
struct StoryViewerView: View {
    let storyGroups: [StoryGroupUI]
    let initialGroupIndex: Int
    @Environment(\.dismiss) var dismiss

    @State private var currentGroupIndex: Int
    @State private var currentStoryIndex: Int = 0
    @State private var progress: CGFloat = 0
    @State private var isPaused = false
    @State private var showReplyInput = false
    @State private var replyText = ""
    @State private var progressTimer: Timer? = nil
    @State private var viewers: [StoryViewer] = []
    @State private var showViewers = false

    private let storyDuration: Double = 5.0 // segundos por story

    init(storyGroups: [StoryGroupUI], initialGroupIndex: Int = 0) {
        self.storyGroups = storyGroups
        self.initialGroupIndex = initialGroupIndex
        _currentGroupIndex = State(initialValue: initialGroupIndex)
    }

    private var currentGroup: StoryGroupUI {
        storyGroups[currentGroupIndex]
    }

    private var currentStory: StoryUI {
        currentGroup.stories[currentStoryIndex]
    }

    var body: some View {
        ZStack {
            // Fondo de la story
            Color.black.ignoresSafeArea()

            // Contenido de la story
            ZStack {
                if currentStory.type == "video", let url = URL(string: currentStory.mediaUrl) {
                    LoopingVideoPlayer(url: url, isPlaying: !isPaused, isMuted: false)
                        .ignoresSafeArea()
                } else {
                    AsyncImage(url: URL(string: currentStory.mediaUrl)) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Rectangle().fill(VividTheme.surfaceVariant)
                            .overlay(ProgressView().tint(.white))
                    }
                    .ignoresSafeArea()
                }

                // Caption overlay
                if !currentStory.caption.isEmpty {
                    VStack {
                        Spacer()
                        Text(currentStory.caption)
                            .font(.body)
                            .foregroundStyle(.white)
                            .padding()
                            .background(.black.opacity(0.4))
                            .cornerRadius(12)
                            .padding()
                    }
                }
            }

            // Tap areas para navegación
            HStack(spacing: 0) {
                // Tap izquierda → story anterior
                Color.clear
                    .contentShape(Rectangle())
                    .frame(maxWidth: .infinity)
                    .onTapGesture { previousStory() }

                // Tap derecha → siguiente story
                Color.clear
                    .contentShape(Rectangle())
                    .frame(maxWidth: .infinity)
                    .onTapGesture { nextStory() }
            }

            // Overlay de UI
            VStack(spacing: 0) {
                // Barras de progreso
                HStack(spacing: 4) {
                    ForEach(Array(currentGroup.stories.enumerated()), id: \.offset) { index, _ in
                        StoryProgressBar(
                            progress: index < currentStoryIndex ? 1.0 :
                                      index == currentStoryIndex ? progress : 0.0
                        )
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 50)

                // Header: avatar + username + tiempo
                HStack(spacing: 12) {
                    AsyncImage(url: URL(string: currentGroup.avatarUrl)) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Circle().fill(VividTheme.surfaceVariant)
                            .overlay(
                                Text(String(currentGroup.username.prefix(1)).uppercased())
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                            )
                    }
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())

                    Text(currentGroup.username)
                        .font(.subheadline.bold())
                        .foregroundStyle(.white)

                    Text(TimeAgoFormatter.format(currentStory.createdAt))
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.6))

                    Spacer()

                    if currentGroup.userId == Auth.auth().currentUser?.uid {
                        Button {
                            Task { viewers = (try? await StoryRepository().fetchViewers(storyId: currentStory.id)) ?? []; showViewers = true }
                        } label: { Image(systemName: "eye.fill").foregroundStyle(.white) }
                    }

                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                Spacer()

                // Input de respuesta
                HStack(spacing: 12) {
                    TextField("Enviar mensaje...", text: $replyText)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().stroke(.white.opacity(0.4), lineWidth: 1))

                    Button(action: {}) {
                        Image(systemName: "heart")
                            .font(.system(size: 24))
                            .foregroundStyle(.white)
                    }

                    Button(action: sendReply) {
                        Image(systemName: "paperplane.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(.white)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 34)
            }
        }
        .onAppear { startProgressTimer() }
        .task(id: currentStory.id) { try? await StoryRepository().markViewed(storyId: currentStory.id) }
        .sheet(isPresented: $showViewers) {
            NavigationStack {
                List(viewers) { viewer in
                    HStack { AsyncImage(url: URL(string: viewer.avatarURL)) { $0.resizable().scaledToFill() } placeholder: { Circle().fill(.gray.opacity(0.3)) }.frame(width: 38, height: 38).clipShape(Circle()); Text("@\(viewer.username)"); Spacer(); Text(TimeAgoFormatter.format(viewer.viewedAt)).font(.caption).foregroundStyle(.secondary) }
                }.navigationTitle("Vieron tu story")
            }
        }
        .onDisappear { progressTimer?.invalidate() }
        .gesture(
            DragGesture()
                .onEnded { value in
                    if value.translation.height > 100 {
                        dismiss()
                    }
                }
        )
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.2)
                .onChanged { pressing in
                    isPaused = pressing
                }
        )
    }

    // MARK: - Progress Timer

    private func startProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [self] _ in
            DispatchQueue.main.async { [self] in
                guard !self.isPaused else { return }

                self.progress += 0.05 / self.storyDuration

                if self.progress >= 1.0 {
                    self.nextStory()
                }
            }
        }
    }

    private func nextStory() {
        if currentStoryIndex < currentGroup.stories.count - 1 {
            currentStoryIndex += 1
            progress = 0
        } else if currentGroupIndex < storyGroups.count - 1 {
            currentGroupIndex += 1
            currentStoryIndex = 0
            progress = 0
        } else {
            dismiss()
        }
    }

    private func previousStory() {
        if currentStoryIndex > 0 {
            currentStoryIndex -= 1
            progress = 0
        } else if currentGroupIndex > 0 {
            currentGroupIndex -= 1
            currentStoryIndex = 0
            progress = 0
        }
    }

    private func sendReply() {
        let text = replyText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        replyText = ""
        Task {
            let sender = Auth.auth().currentUser?.displayName ?? "Usuario"
            _ = try? await ChatRepository().sendMessage(
                receiverId: currentGroup.userId,
                receiverName: currentGroup.username,
                text: text,
                senderName: sender,
                replyToStoryId: currentStory.id
            )
        }
    }
}

// MARK: - Progress Bar

struct StoryProgressBar: View {
    let progress: CGFloat

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(.white.opacity(0.3))
                    .frame(height: 2.5)

                RoundedRectangle(cornerRadius: 2)
                    .fill(.white)
                    .frame(width: geo.size.width * progress, height: 2.5)
            }
        }
        .frame(height: 2.5)
    }
}
