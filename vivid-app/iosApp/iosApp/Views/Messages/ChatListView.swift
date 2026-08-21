import SwiftUI

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

    func loadChats() async {
        // En producción: usar ChatRepository
        let names = ["Ana García", "Carlos López", "María Ruiz", "Pedro Sánchez", "Laura Díaz"]
        let lastMessages = ["¡Hola! ¿Cómo estás?", "Nos vemos mañana", "Mira esta foto", "Jaja qué bueno", "Ok perfecto 👍"]
        let now = Date().timeIntervalSince1970 * 1000
        var result: [ChatUI] = []
        for i in 0..<5 {
            let chat = ChatUI(
                id: "chat_\(i)",
                chatId: "chat_\(i)",
                otherUserId: "user_\(i)",
                otherUserName: names[i],
                otherUserAvatar: "",
                lastMessage: lastMessages[i],
                lastMessageTimestamp: Int64(now) - Int64(i * 1800000),
                unreadCount: i < 2 ? Int.random(in: 1...5) : 0
            )
            result.append(chat)
        }
        chats = result
    }
}

@MainActor
class ChatViewModel: ObservableObject {
    @Published var messages: [MessageUI] = []
    private let currentUserId = "current_user"

    func loadMessages(chatId: String) async {
        // En producción: usar ChatRepository
        let sampleTexts = ["Hola!", "¿Cómo estás?", "Bien y tú?", "Todo genial", "¿Qué planes tienes?", "Nada especial", "¿Salimos?", "Sí, buena idea", "¿A qué hora?", "A las 8?"]
        let now = Date().timeIntervalSince1970 * 1000
        var result: [MessageUI] = []
        for i in 0..<10 {
            let msg = MessageUI(
                id: "msg_\(i)",
                text: sampleTexts[i],
                senderId: i % 2 == 0 ? currentUserId : "other_user",
                timestamp: Int64(now) - Int64((10 - i) * 60000),
                isRead: true,
                isDelivered: true,
                reaction: "",
                type: "text"
            )
            result.append(msg)
        }
        messages = result
    }

    func sendMessage(chatId: String, text: String, receiverId: String) {
        let message = MessageUI(
            id: "msg_\(messages.count)",
            text: text,
            senderId: currentUserId,
            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
            isRead: false,
            isDelivered: false,
            reaction: "",
            type: "text"
        )
        messages.append(message)
    }

    func isFromCurrentUser(_ message: MessageUI) -> Bool {
        message.senderId == currentUserId
    }
}
