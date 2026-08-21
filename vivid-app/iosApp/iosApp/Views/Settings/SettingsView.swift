import SwiftUI
import FirebaseAuth
import GoogleSignIn

/**
 * Pantalla de ajustes.
 * Equivalente al SettingsScreen/SettingsHubScreen de Android.
 */
struct SettingsView: View {
    @EnvironmentObject var appState: AppState
    @State private var showDeleteConfirmation = false
    @State private var showSignOutConfirmation = false
    @State private var accountError: String?
    @State private var isDeleting = false

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            List {
                // Sección: Cuenta
                Section {
                    SettingsRow(icon: "person.crop.circle", title: "Editar perfil", color: .blue)
                    NavigationLink(destination: PrivacySettingsView()) { SettingsRow(icon: "lock.shield", title: "Privacidad", color: .green) }
                    NavigationLink(destination: NotificationSettingsView()) { SettingsRow(icon: "bell.badge", title: "Notificaciones", color: .orange) }
                } header: {
                    Text("Cuenta")
                        .foregroundStyle(.white.opacity(0.6))
                }

                // Sección: Contenido
                Section {
                    NavigationLink(destination: SavedPostsView()) { SettingsRow(icon: "bookmark", title: "Guardados", color: .purple) }
                    NavigationLink(destination: LikedContentView()) { SettingsRow(icon: "heart", title: "Me gusta", color: .pink) }
                    NavigationLink(destination: ArchivedContentView()) { SettingsRow(icon: "archivebox", title: "Archivados", color: .gray) }
                } header: {
                    Text("Contenido")
                        .foregroundStyle(.white.opacity(0.6))
                }

                // Sección: General
                Section {
                    NavigationLink(destination: AppearanceSettingsView()) { SettingsRow(icon: "globe", title: "Idioma", color: .cyan) }
                    NavigationLink(destination: AppearanceSettingsView()) { SettingsRow(icon: "moon", title: "Tema", color: .indigo) }
                    SettingsRow(icon: "questionmark.circle", title: "Ayuda", color: .teal)
                    SettingsRow(icon: "info.circle", title: "Acerca de", color: .mint)
                } header: {
                    Text("General")
                        .foregroundStyle(.white.opacity(0.6))
                }

                Section("Moderación") {
                    NavigationLink(destination: AdminReportsView()) { SettingsRow(icon: "shield.lefthalf.filled", title: "Reportes admin", color: .red) }
                }

                // Sección: Peligro
                Section {
                    Button(action: { showSignOutConfirmation = true }) {
                        HStack {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                .foregroundStyle(.red)
                            Text("Cerrar sesión")
                                .foregroundStyle(.red)
                        }
                    }

                    Button(action: { showDeleteConfirmation = true }) {
                        HStack {
                            Image(systemName: "trash")
                                .foregroundStyle(.red)
                            Text("Eliminar cuenta")
                                .foregroundStyle(.red)
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .listStyle(.insetGrouped)
        }
        .navigationTitle("Ajustes")
        .navigationBarTitleDisplayMode(.large)
        .alert("Cerrar sesión", isPresented: $showSignOutConfirmation) {
            Button("Cancelar", role: .cancel) {}
            Button("Cerrar sesión", role: .destructive) {
                try? Auth.auth().signOut()
                GIDSignIn.sharedInstance.signOut()
                appState.signOut()
            }
        } message: {
            Text("¿Seguro que quieres cerrar sesión?")
        }
        .alert("Eliminar cuenta", isPresented: $showDeleteConfirmation) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) {
                isDeleting = true
                Task {
                    do { try await AccountRepository().deleteCurrentAccount(); appState.signOut() }
                    catch { accountError = error.localizedDescription }
                    isDeleting = false
                }
            }
        } message: {
            Text("Esta acción es irreversible. Se eliminarán tus datos y contenido remoto.")
        }
        .alert("No se pudo eliminar la cuenta", isPresented: Binding(get: { accountError != nil }, set: { if !$0 { accountError = nil } })) {
            Button("Aceptar") { accountError = nil }
        } message: { Text(accountError ?? "Intenta iniciar sesión de nuevo.") }
        .overlay { if isDeleting { ProgressView("Eliminando…").padding().background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14)) } }
    }
}

struct PrivacySettingsView: View {
    @EnvironmentObject var appState: AppState
    @State private var isPrivate = false

    var body: some View {
        Form {
            Toggle("Cuenta privada", isOn: $isPrivate)
                .onChange(of: isPrivate) { value in
                    Task { try? await FollowRepository().setPrivateAccount(value) }
                }
            NavigationLink("Administrar mejores amigos") { CloseFriendsView() }
            NavigationLink("Usuarios bloqueados") { BlockedUsersView() }
            Text("Si tu cuenta es privada, otras personas deben enviar una solicitud para seguirte y ver tus posts.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .navigationTitle("Privacidad")
        .onAppear { isPrivate = appState.currentUser?.isPrivate ?? false }
    }
}

struct SettingsRow: View {
    let icon: String
    let title: String
    let color: Color

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundStyle(.white)
                .frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 8).fill(color.opacity(0.3)))

            Text(title)
                .foregroundStyle(.white)

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundStyle(.white.opacity(0.3))
        }
    }
}
