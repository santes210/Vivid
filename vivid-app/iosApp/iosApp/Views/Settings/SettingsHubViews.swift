import FirebaseAuth
import FirebaseFirestore
import SwiftUI
import UIKit
import UserNotifications

struct AppearanceSettingsView: View {
    @AppStorage("settings.language") private var language = "system"
    @AppStorage("settings.theme") private var theme = "system"
    var body: some View {
        Form {
            Picker("Idioma", selection: $language) { Text("Sistema").tag("system"); Text("Español").tag("es"); Text("English").tag("en") }
            Picker("Tema", selection: $theme) { Text("Sistema").tag("system"); Text("Oscuro").tag("dark"); Text("Claro").tag("light") }
            Text("El idioma se aplicará por completo al reiniciar Vivid.").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle("Apariencia")
    }
}

struct NotificationSettingsView: View {
    @State private var status = "Cargando…"
    var body: some View {
        Form {
            LabeledContent("Permiso del sistema", value: status)
            Button("Abrir Ajustes de iOS") { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }
        }.navigationTitle("Notificaciones").task {
            let value = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
            status = value == .authorized ? "Activado" : value == .denied ? "Desactivado" : "Sin decidir"
        }
    }
}

struct LikedContentView: View {
    @State private var posts: [PostUI] = []
    @State private var error: String?
    var body: some View {
        ScrollView { LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 3), spacing: 2) { ForEach(posts) { ExploreGridItem(post: $0) } } }
            .background(VividTheme.backgroundGradient.ignoresSafeArea()).navigationTitle("Me gusta")
            .task { await load() }
    }
    private func load() async {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        do {
            let snapshot = try await FirebaseAsync.value { completion in Firestore.firestore().collectionGroup("likes").whereField("userId", isEqualTo: uid).getDocuments(completion: completion) }
            var values: [PostUI] = []
            for like in snapshot.documents where like.reference.parent.parent?.parent.collectionID == "posts" {
                if let id = like.reference.parent.parent?.documentID, let post = try await PostRepository().fetchPost(id: id) { values.append(post.asUI()) }
            }
            posts = values
        } catch { self.error = error.localizedDescription }
    }
}

struct ArchivedContentView: View {
    var body: some View {
        VStack(spacing: 12) { Image(systemName: "archivebox").font(.largeTitle); Text("Sin archivados").font(.headline); Text("El contenido que archives aparecerá aquí.").font(.caption).foregroundStyle(.secondary) }
            .navigationTitle("Archivados")
    }
}
