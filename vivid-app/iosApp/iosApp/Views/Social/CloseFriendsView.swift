import FirebaseAuth
import FirebaseFirestore
import SwiftUI

struct CloseFriendsView: View {
    @State private var people: [SocialUserPreview] = []
    @State private var selected: Set<String> = []
    private let repository = FollowRepository()

    var body: some View {
        List(people) { user in
            Button {
                let enabled = !selected.contains(user.id)
                if enabled { selected.insert(user.id) } else { selected.remove(user.id) }
                Task { try? await repository.setCloseFriend(userId: user.id, enabled: enabled) }
            } label: {
                HStack { SocialRow(user: user); Spacer(); Image(systemName: selected.contains(user.id) ? "star.fill" : "star").foregroundStyle(selected.contains(user.id) ? .green : .secondary) }
            }.buttonStyle(.plain)
        }
        .navigationTitle("Mejores amigos")
        .overlay { if people.isEmpty { VStack(spacing: 10) { Image(systemName: "person.2").font(.largeTitle); Text("Sigue a personas primero") } } }
        .task { await load() }
    }

    private func load() async {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        let following: [String] = await withCheckedContinuation { continuation in
            var listener: ListenerRegistration?
            listener = repository.observeFollowingIds(userId: uid) { result in
                listener?.remove(); continuation.resume(returning: (try? result.get()) ?? [])
            }
        }
        let close: Set<String> = await withCheckedContinuation { continuation in
            var listener: ListenerRegistration?
            listener = repository.observeCloseFriendIds { result in
                listener?.remove(); continuation.resume(returning: (try? result.get()) ?? [])
            }
        }
        people = await repository.loadUserPreviews(ids: following); selected = close
    }
}
