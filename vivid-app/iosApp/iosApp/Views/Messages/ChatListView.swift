import SwiftUI
import Combine
import FirebaseAuth
import FirebaseFirestore
import PhotosUI
import AVFoundation

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
                Button(action: { viewModel.showPicker = true }) {
                    Image(systemName: "square.and.pencil")
                        .foregroundStyle(.white)
                }
            }
        }
        .sheet(isPresented: $viewModel.showPicker) {
            UserPickerView { user in
                viewModel.showPicker = false
                viewModel.pendingChat = ChatUI(id: ChatRepository.id(firstUserId: Auth.auth().currentUser?.uid ?? "", secondUserId: user.id), chatId: ChatRepository.id(firstUserId: Auth.auth().currentUser?.uid ?? "", secondUserId: user.id), otherUserId: user.id, otherUserName: user.username, otherUserAvatar: user.avatarURL, lastMessage: "", lastMessageTimestamp: 0, unreadCount: 0)
            }
        }
        .background(
            NavigationLink(
                destination: Group {
                    if let chat = viewModel.pendingChat { ChatView(chat: chat) }
                },
                isActive: Binding(
                    get: { viewModel.pendingChat != nil },
                    set: { if !$0 { viewModel.pendingChat = nil } }
                )
            ) { EmptyView() }
        )
        .task {
            await viewModel.loadChats()
        }
    }
}

struct ChatUI: Identifiable, Hashable {
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
    @State private var photoItem: PhotosPickerItem?
    @State private var editingMessage: MessageUI?
    @State private var editedText = ""

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Mensajes
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            if viewModel.canLoadOlder {
                                Button(viewModel.isLoadingOlder ? "Cargando…" : "Cargar mensajes anteriores") {
                                    Task { await viewModel.loadOlder(chatId: chat.chatId) }
                                }
                                .disabled(viewModel.isLoadingOlder)
                                .font(.caption).foregroundStyle(.white.opacity(0.65))
                            }
                            ForEach(viewModel.messages) { message in
                                MessageBubble(message: message, isFromCurrentUser: viewModel.isFromCurrentUser(message))
                                    .contextMenu {
                                        ForEach(["❤️", "😂", "😮", "😢", "🔥"], id: \.self) { reaction in
                                            Button(reaction) { viewModel.react(chatId: chat.chatId, messageId: message.id, reaction: reaction) }
                                        }
                                        if viewModel.isFromCurrentUser(message) && message.type == "text" {
                                            Button { editedText = message.text; editingMessage = message } label: { Label("Editar", systemImage: "pencil") }
                                        }
                                        if viewModel.isFromCurrentUser(message) {
                                            Button(role: .destructive) { viewModel.delete(chatId: chat.chatId, messageId: message.id) } label: { Label("Eliminar", systemImage: "trash") }
                                        }
                                    }
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
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(VividTheme.primary)
                    }
                    .onChange(of: photoItem) { item in
                        Task { await viewModel.sendImage(item: item, chat: chat) }
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
                        Button(action: { Task { await viewModel.toggleVoiceRecording(chat: chat) } }) {
                            HStack(spacing: 5) {
                                if viewModel.audio.isRecording {
                                    Text(viewModel.recordingTime).font(.caption.monospacedDigit())
                                }
                                Image(systemName: viewModel.audio.isRecording ? "stop.circle.fill" : "mic.fill")
                                    .font(.system(size: 22))
                            }
                            .foregroundStyle(viewModel.audio.isRecording ? .red : .white.opacity(0.7))
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
        .task { await viewModel.loadMessages(chatId: chat.chatId) }
        .onDisappear { viewModel.audio.cancelRecording() }
        .alert("Editar mensaje", isPresented: Binding(get: { editingMessage != nil }, set: { if !$0 { editingMessage = nil } })) {
            TextField("Mensaje", text: $editedText)
            Button("Cancelar", role: .cancel) { editingMessage = nil }
            Button("Guardar") {
                if let message = editingMessage { viewModel.edit(chatId: chat.chatId, messageId: message.id, text: editedText) }
                editingMessage = nil
            }
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
    let imageURL: String
    let voiceURL: String
    let voiceDurationMs: Int64
    let lastEditedAt: Int64
}

struct MessageBubble: View {
    let message: MessageUI
    let isFromCurrentUser: Bool
    @StateObject private var audio = AudioNoteService()

    var body: some View {
        HStack {
            if isFromCurrentUser { Spacer(minLength: 60) }

            VStack(alignment: isFromCurrentUser ? .trailing : .leading, spacing: 2) {
                if message.type == "image", let url = URL(string: message.imageURL) {
                    AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
                        .frame(width: 180, height: 180)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                } else if message.type == "voice" {
                    Button {
                        if let url = URL(string: message.voiceURL) { audio.togglePlayback(url: url) }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: audio.isPlaying ? "pause.fill" : "play.fill")
                            Image(systemName: "waveform").font(.title3)
                            Text(String(format: "%d:%02d", message.voiceDurationMs / 60_000, (message.voiceDurationMs / 1_000) % 60))
                                .font(.caption.monospacedDigit())
                        }
                        .foregroundStyle(.white).padding(.horizontal, 14).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 18).fill(isFromCurrentUser ? VividTheme.primary : .white.opacity(0.12)))
                    }
                } else {
                    Text(message.type == "story_reply" ? "↳ \(message.text)" : message.text)
                        .font(.subheadline)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(isFromCurrentUser ? VividTheme.primary : .white.opacity(0.12))
                        )
                }

                HStack(spacing: 4) {
                    if !message.reaction.isEmpty { Text(message.reaction).font(.caption) }
                    if message.lastEditedAt > 0 { Text("editado").font(.caption2).foregroundStyle(.white.opacity(0.35)) }
                    Text(TimeAgoFormatter.format(message.timestamp)).font(.caption2).foregroundStyle(.white.opacity(0.4))
                    if isFromCurrentUser {
                        Image(systemName: message.isRead ? "checkmark.circle.fill" : (message.isDelivered ? "checkmark.circle" : "checkmark"))
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
    @Published var showPicker = false
    @Published var pendingChat: ChatUI?
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
    @Published var canLoadOlder = true
    @Published var isLoadingOlder = false
    let audio = AudioNoteService()
    private let repository = ChatRepository()
    private var listener: ListenerRegistration?
    private var olderMessages: [MessageUI] = []
    private var audioCancellable: AnyCancellable?
    private var currentUserId: String { Auth.auth().currentUser?.uid ?? "" }
    var recordingTime: String { String(format: "%d:%02d", Int(audio.elapsed) / 60, Int(audio.elapsed) % 60) }

    init() {
        audioCancellable = audio.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }
    }

    func loadMessages(chatId: String) async {
        listener?.remove()
        listener = repository.observeMessages(chatId: chatId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let messages):
                    guard let self else { return }
                    let recent = messages.map(Self.map)
                    let recentIds = Set(recent.map(\.id))
                    self.messages = self.olderMessages.filter { !recentIds.contains($0.id) } + recent
                    self.canLoadOlder = messages.count >= 50
                case .failure(let error): self?.error = error.localizedDescription
                }
            }
        }
        do {
            try await repository.markMessagesDelivered(chatId: chatId)
            try await repository.markMessagesRead(chatId: chatId)
        } catch { self.error = error.localizedDescription }
    }

    func loadOlder(chatId: String) async {
        guard let oldest = messages.first, canLoadOlder, !isLoadingOlder else { return }
        isLoadingOlder = true
        do {
            let loaded = try await repository.fetchOlderMessages(chatId: chatId, before: oldest.timestamp)
            let mapped = loaded.map(Self.map)
            olderMessages = mapped + olderMessages
            messages = mapped + messages
            canLoadOlder = loaded.count >= 50
        } catch { self.error = error.localizedDescription }
        isLoadingOlder = false
    }

    private static func map(_ value: FirestoreMessage) -> MessageUI {
        MessageUI(id: value.id, text: value.text, senderId: value.senderId, timestamp: value.timestamp, isRead: value.isRead, isDelivered: value.isDelivered, reaction: value.reaction, type: value.type, imageURL: value.imageURL, voiceURL: value.voiceURL, voiceDurationMs: value.voiceDurationMs, lastEditedAt: value.lastEditedAt)
    }

    func sendMessage(chatId: String, text: String, receiverId: String) {
        let name = Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email?.components(separatedBy: "@").first ?? "Usuario"
        Task { do { _ = try await repository.sendMessage(receiverId: receiverId, receiverName: "Usuario", text: text, senderName: name) } catch { self.error = error.localizedDescription } }
    }

    func isFromCurrentUser(_ message: MessageUI) -> Bool { message.senderId == currentUserId }

    func sendImage(item: PhotosPickerItem?, chat: ChatUI) async {
        guard let item, let data = try? await item.loadTransferable(type: Data.self) else { return }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("chat-\(UUID().uuidString).jpg")
        try? data.write(to: url)
        do {
            let uploaded = try await MediaStorageRepository().upload(localURL: url, path: "chats/\(chat.chatId)/\(UUID().uuidString).jpg")
            let name = Auth.auth().currentUser?.displayName ?? "Usuario"
            _ = try await repository.sendMessage(receiverId: chat.otherUserId, receiverName: chat.otherUserName, text: "", senderName: name, image: uploaded)
        } catch { self.error = error.localizedDescription }
    }

    func toggleVoiceRecording(chat: ChatUI) async {
        if !audio.isRecording { _ = await audio.startRecording(); objectWillChange.send(); return }
        guard let recording = audio.stopRecording() else { return }
        do {
            let uploaded = try await MediaStorageRepository().upload(localURL: recording.url, path: "chats/\(chat.chatId)/voice-\(UUID().uuidString).m4a", contentType: "audio/mp4")
            let name = Auth.auth().currentUser?.displayName ?? "Usuario"
            _ = try await repository.sendMessage(receiverId: chat.otherUserId, receiverName: chat.otherUserName, text: "", senderName: name, voice: uploaded, voiceDurationMs: recording.durationMs)
            try? FileManager.default.removeItem(at: recording.url)
        } catch { self.error = error.localizedDescription }
    }

    func react(chatId: String, messageId: String, reaction: String) {
        Task { do { try await repository.react(chatId: chatId, messageId: messageId, reaction: reaction) } catch { self.error = error.localizedDescription } }
    }
    func edit(chatId: String, messageId: String, text: String) {
        Task { do { try await repository.editMessage(chatId: chatId, messageId: messageId, text: text) } catch { self.error = error.localizedDescription } }
    }
    func delete(chatId: String, messageId: String) {
        Task { do { try await repository.deleteMessage(chatId: chatId, messageId: messageId) } catch { self.error = error.localizedDescription } }
    }
}
