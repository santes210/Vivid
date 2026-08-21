import SwiftUI
import FirebaseAuth
import FirebaseFirestore

struct CommentsView: View {
    let postId: String
    var isReel: Bool = false
    let postOwnerUsername: String
    @EnvironmentObject var appState: AppState
    @StateObject private var viewModel = CommentsViewModel()
    @State private var draft = ""

    var body: some View {
        NavigationStack {
            ZStack {
                VividTheme.backgroundGradient.ignoresSafeArea()
                VStack(spacing: 0) {
                    List(viewModel.comments) { comment in
                        HStack(alignment: .top, spacing: 12) {
                            NavigationLink(destination: ProfileView(userId: comment.userId)) {
                                Text(String(comment.username.prefix(1)).uppercased())
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(VividTheme.surfaceVariant))
                            }
                            VStack(alignment: .leading, spacing: 4) {
                                Text(comment.username).font(.subheadline.bold()).foregroundStyle(.white)
                                Text(comment.text).font(.subheadline).foregroundStyle(.white.opacity(0.85))
                                Text(TimeAgoFormatter.format(comment.timestamp)).font(.caption2).foregroundStyle(.white.opacity(0.4))
                            }
                        }
                        .listRowBackground(Color.clear)
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)

                    HStack {
                        TextField("Comenta como \(appState.currentUser?.username ?? "tú")…", text: $draft, axis: .vertical)
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.08)))
                        Button("Enviar") { send() }
                            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            .foregroundStyle(VividTheme.primary)
                    }
                    .padding()
                }
            }
            .navigationTitle("Comentarios")
            .navigationBarTitleDisplayMode(.inline)
            .task { viewModel.listen(postId: postId, isReel: isReel) }
        }
    }

    private func send() {
        let text = draft
        draft = ""
        viewModel.addComment(postId: postId, isReel: isReel, text: text, user: appState.currentUser)
    }
}

@MainActor
final class CommentsViewModel: ObservableObject {
    @Published var comments: [FirestoreComment] = []
    @Published var error: String?
    private let repository = PostRepository()
    private let reels = ReelRepository()
    private var listener: ListenerRegistration?

    func listen(postId: String, isReel: Bool) {
        listener?.remove()
        listener = isReel
            ? reels.observeComments(reelId: postId) { [weak self] result in
                DispatchQueue.main.async {
                    switch result {
                    case .success(let comments): self?.comments = comments
                    case .failure(let error): self?.error = error.localizedDescription
                    }
                }
            }
            : repository.observeComments(postId: postId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let comments): self?.comments = comments
                case .failure(let error): self?.error = error.localizedDescription
                }
            }
        }
    }

    func addComment(postId: String, isReel: Bool, text: String, user: User?) {
        Task {
            do {
                if isReel {
                    try await reels.addComment(reelId: postId, text: text, username: user?.username ?? "usuario", avatarURL: user?.avatarUrl ?? "")
                } else {
                    try await repository.addComment(postId: postId, text: text, username: user?.username ?? "usuario", avatarURL: user?.avatarUrl ?? "")
                }
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

struct ReelCommentsView: View {
    let reelId: String
    var body: some View {
        CommentsView(postId: reelId, isReel: true, postOwnerUsername: "")
    }
}
