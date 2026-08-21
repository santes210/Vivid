import SwiftUI
import FirebaseAuth
import FirebaseFirestore

/**
 * Lista de chats (conversaciones).
 * Equivalente al ChatListScreen de Android.
 */
struct ChatListView: View {
    @StateObject private var viewModel = ChatListViewModel()

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            if viewModel.chats.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.system(size: 48))
                        .foregroundStyle(.white.opacity(0.3))

                    Text("No tienes mensajes")
                        .font(VividTheme.heading())
                        .foregroundStyle(.white)

                    Text("Inicia una conversación con alguien")
                        .font(VividTheme.body(14))
                        .foregroundStyle(.white.opacity(0.5))
                }
            } else {
                List {
                    ForEach(viewModel.chats) { chat in
                        NavigationLink(destination: ChatView(chat: chat)) {
                            ChatRow(chat: chat)
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("Mensajes")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: {}) {
                    Image(systemName: "square.and.pencil")
                        .foregroundStyle(.white)
                }
            }
        }
        .task {
            await viewModel.loadChats()
        }
    }
}

struct ChatUI: Identifiable {
    let id: String
    let chatId: String
    let otherUserId: String
    let otherUserName: String
    let otherUserAvatar: String
    let lastMessage: String
    let lastMessageTimestamp: Int64
    var unreadCount: Int
}

struct ChatRow: View {
    let chat: ChatUI

    var body: some View {
        HStack(spacing: 14) {
            // Avatar
            ZStack(alignment: .bottomTrailing) {
                AsyncImage(url: URL(string: chat.otherUserAvatar)) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Circle().fill(VividTheme.surfaceVariant)
                        .overlay(
                            Text(String(chat.otherUserName.prefix(1)).uppercased())
                                .font(.headline)
                                .foregroundStyle(.white)
                        )
                }
                .frame(width: 56, height: 56)
                .clipShape(Circle())

                // Indicador de online
                Circle()
                    .fill(.green)
                    .frame(width: 14, height: 14)
                    .overlay(Circle().stroke(VividTheme.surface, lineWidth: 2))
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(chat.otherUserName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.white)

                    Spacer()

                    Text(TimeAgoFormatter.format(chat.lastMessageTimestamp))
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.5))
                }

                HStack {
                    Text(chat.lastMessage)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.6))
                        .lineLimit(1)

                    Spacer()

                    if chat.unreadCount > 0 {
                        Text("\(chat.unreadCount)")
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(VividTheme.primary))
                    }
                }
            }
        }
        .padding(.vertical, 8)
    }
}

/**
 * Vista de un chat individual con mensajes.
 * Equivalente al ChatScreen de Android.
 */
struct ChatView: View {
    let chat: ChatUI
    @StateObject private var viewModel = ChatViewModel()
    @State private var messageText = ""
    @FocusState private var isInputFocused: Bool

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Mensajes
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(viewModel.messages) { message in
                                MessageBubble(
                                    message: message,
                                    isFromCurrentUser: viewModel.isFromCurrentUser(message)
                                )
                                .id(message.id)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }
                    .onChange(of: viewModel.messages.count) { newCount in
                        if !viewModel.messages.isEmpty {
                            let lastMessage = viewModel.messages[newCount - 1]
                            withAnimation {
                                proxy.scrollTo(lastMessage.id, anchor: .bottom)
                            }
                        }
                    }
                }

                // Input de mensaje
                HStack(spacing: 12) {
                    Button(action: {}) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(VividTheme.primary)
                    }

                    TextField("Mensaje...", text: $messageText, axis: .vertical)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.white)
                        .lineLimit(1...4)
                        .focused($isInputFocused)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(
                            Capsule().fill(.white.opacity(0.08))
                        )

                    if messageText.isEmpty {
                        Button(action: {}) {
                            Image(systemName: "mic.fill")
                                .font(.system(size: 22))
                                .foregroundStyle(.white.opacity(0.6))
                        }
                    } else {
                        Button(action: sendMessage) {
                            Image(systemName: "paperplane.fill")
                                .font(.system(size: 22))
                                .foregroundStyle(VividTheme.primary)
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.black.opacity(0.3))
            }
        }
        .navigationTitle(chat.otherUserName)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadMessages(chatId: chat.chatId)
        }
    }

    private func sendMessage() {
        guard !messageText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        viewModel.sendMessage(chatId: chat.chatId, text: messageText, receiverId: chat.otherUserId)
        messageText = ""
    }
}

struct MessageUI: Identifiable {
    let id: String
    let text: String
    let senderId: String
    let timestamp: Int64
    let isRead: Bool
    let isDelivered: Bool
    let reaction: String
    let type: String
}

struct MessageBubble: View {
    let message: MessageUI
    let isFromCurrentUser: Bool

    var body: some View {
        HStack {
            if isFromCurrentUser { Spacer(minLength: 60) }

            VStack(alignment: isFromCurrentUser ? .trailing : .leading, spacing: 2) {
                Text(message.text)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(isFromCurrentUser ? VividTheme.primary : .white.opacity(0.12))
                    )

                HStack(spacing: 4) {
                    Text(TimeAgoFormatter.format(message.timestamp))
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.4))

                    if isFromCurrentUser {
                        Image(systemName: message.isRead ? "checkmark.circle.fill" : "checkmark")
                            .font(.system(size: 10))
                            .foregroundStyle(message.isRead ? VividTheme.accent : .white.opacity(0.4))
                    }
                }
            }

            if !isFromCurrentUser { Spacer(minLength: 60) }
        }
    }
}

@MainActor
class ChatListViewModel: ObservableObject {
    @Published var chats: [ChatUI] = []
    private let repository = ChatRepository()
    private var listener: ListenerRegistration?

    func loadChats() async {
        guard let uid = Auth.auth().currentUser?.uid, listener == nil else { return }
        listener = repository.observeChats(forUserId: uid) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                if case .success(let chats) = result {
                    self.chats = chats.compactMap { chat in
                        guard let other = chat.otherParticipant(for: uid) else { return nil }
                        return ChatUI(id: chat.id, chatId: chat.id, otherUserId: other, otherUserName: chat.participantNames[other] ?? "Usuario", otherUserAvatar: chat.participantAvatars[other] ?? "", lastMessage: chat.lastMessage, lastMessageTimestamp: chat.lastTimestamp, unreadCount: chat.unreadCounts[uid] ?? 0)
                    }
                }
            }
        }
    }
}

@MainActor
class ChatViewModel: ObservableObject {
    @Published var messages: [MessageUI] = []
    @Published var error: String?
    private let repository = ChatRepository()
    private var listener: ListenerRegistration?
    private var currentUserId: String { Auth.auth().currentUser?.uid ?? "" }

    func loadMessages(chatId: String) async {
        listener?.remove()
        listener = repository.observeMessages(chatId: chatId) { [weak self] result in
            DispatchQueue.main.async {
                switch result { case .success(let messages): self?.messages = messages.map { MessageUI(id: $0.id, text: $0.text, senderId: $0.senderId, timestamp: $0.timestamp, isRead: $0.isRead, isDelivered: $0.isDelivered, reaction: $0.reaction, type: $0.type) }; case .failure(let error): self?.error = error.localizedDescription }
            }
        }
        do { try await repository.markMessagesRead(chatId: chatId) } catch { self.error = error.localizedDescription }
    }

    func sendMessage(chatId: String, text: String, receiverId: String) {
        let name = Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email?.components(separatedBy: "@").first ?? "Usuario"
        Task { do { _ = try await repository.sendMessage(receiverId: receiverId, receiverName: "Usuario", text: text, senderName: name) } catch { self.error = error.localizedDescription } }
    }

    func isFromCurrentUser(_ message: MessageUI) -> Bool { message.senderId == currentUserId }
}
