import FirebaseAuth
import SwiftUI

/// Resuelve el destino completo del push; no se limita a cambiar de tab.
struct DeepLinkDestinationView: View {
    let link: AppState.DeepLink
    @State private var post: PostUI?
    @State private var reel: ReelUI?
    @State private var chat: ChatUI?
    @State private var error: String?

    var body: some View {
        Group {
            switch link {
            case .profile(let id): ProfileView(userId: id)
            case .post:
                if let post { ScrollView { PostCard(post: post) { Task { try? await PostRepository().toggleLike(postId: post.id) } } } }
                else { loading }
            case .reel:
                if let reel { ReelCard(reel: reel) { Task { try? await ReelRepository().toggleLike(reelId: reel.id) } }.background(.black).ignoresSafeArea() }
                else { loading }
            case .chat:
                if let chat { ChatView(chat: chat) } else { loading }
            }
        }
        .task { await resolve() }
    }

    private var loading: some View {
        Group { if let error { VStack(spacing: 12) { Image(systemName: "exclamationmark.triangle").font(.largeTitle); Text("No disponible").font(.headline); Text(error).font(.caption) }.padding() } else { ProgressView() } }
    }

    private func resolve() async {
        do {
            switch link {
            case .post(let id): post = try await PostRepository().fetchPost(id: id)?.asUI()
            case .reel(let id): reel = try await ReelRepository().fetchReel(id: id)?.asUI()
            case .chat(let id):
                guard let value = try await ChatRepository().fetchChat(id: id), let uid = Auth.auth().currentUser?.uid, let other = value.otherParticipant(for: uid) else { throw FirebaseRepositoryError.missingValue }
                chat = ChatUI(id: value.id, chatId: value.id, otherUserId: other, otherUserName: value.participantNames[other] ?? "Usuario", otherUserAvatar: value.participantAvatars[other] ?? "", lastMessage: value.lastMessage, lastMessageTimestamp: value.lastTimestamp, unreadCount: value.unreadCounts[uid] ?? 0)
            case .profile: break
            }
        } catch { self.error = error.localizedDescription }
    }
}
