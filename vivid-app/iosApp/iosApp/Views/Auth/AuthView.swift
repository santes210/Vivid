import SwiftUI
import AuthenticationServices
import FirebaseCore
import FirebaseAuth
import GoogleSignIn

/**
 * Pantalla de autenticación.
 * Ofrece inicio de sesión con Apple y Google.
 *
 * En iOS, Sign in with Apple es obligatorio si se ofrece cualquier
 * otro método de inicio de sesión de terceros (requisito de la App Store).
 */
struct AuthView: View {
    @EnvironmentObject var appState: AppState
    @State private var isAnimating = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var currentNonce: String?

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient
                .ignoresSafeArea()

            // Partículas decorativas animadas
            ParticleBackground()

            VStack(spacing: 0) {
                Spacer()

                // Logo y título
                VStack(spacing: 16) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 72))
                        .foregroundStyle(VividTheme.accentGradient)
                        .scaleEffect(isAnimating ? 1.05 : 0.95)
                        .animation(
                            .easeInOut(duration: 2).repeatForever(autoreverses: true),
                            value: isAnimating
                        )

                    Text("Vivid")
                        .font(VividTheme.title(48))
                        .foregroundStyle(.white)

                    Text("Comparte tu mundo\nen colores vibrantes")
                        .font(VividTheme.body(16))
                        .foregroundStyle(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }

                Spacer()

                // Error message
                if let error = errorMessage {
                    Text(error)
                        .font(VividTheme.caption(13))
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                        .padding(.bottom, 12)
                }

                // Botones de autenticación
                VStack(spacing: 16) {
                    // Sign in with Apple
                    SignInWithAppleButton(
                        onRequest: { request in
                            request.requestedScopes = [.fullName, .email]
                            let nonce = UUID().uuidString
                            currentNonce = nonce
                            request.nonce = nonce
                        },
                        onCompletion: { result in
                            handleAppleSignIn(result)
                        }
                    )
                    .signInWithAppleButtonStyle(.white)
                    .frame(height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: VividTheme.cornerRadiusMD, style: .continuous))
                    .disabled(isLoading)

                    // Sign in with Google
                    Button(action: signInWithGoogle) {
                        HStack(spacing: 12) {
                            Image(systemName: "g.circle.fill")
                                .font(.system(size: 20))
                                .foregroundStyle(.red, .yellow, .green, .blue)
                            Text("Continuar con Google")
                                .font(VividTheme.heading(16))
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            RoundedRectangle(cornerRadius: VividTheme.cornerRadiusMD, style: .continuous)
                                .fill(.white.opacity(0.15))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: VividTheme.cornerRadiusMD, style: .continuous)
                                .strokeBorder(.white.opacity(0.2), lineWidth: 1)
                        )
                    }
                    .disabled(isLoading)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 60)
            }

            if isLoading {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()
                ProgressView()
                    .tint(.white)
                    .scaleEffect(1.5)
            }
        }
        .onAppear { isAnimating = true }
    }

    // MARK: - Google Sign-In

    private func signInWithGoogle() {
        isLoading = true
        errorMessage = nil

        guard let clientID = FirebaseApp.app()?.options.clientID else {
            errorMessage = "Error de configuración de Google. Reintenta más tarde."
            isLoading = false
            return
        }

        let config = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.configuration = config

        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else {
            errorMessage = "No se pudo abrir la ventana de inicio de sesión."
            isLoading = false
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { result, error in
            DispatchQueue.main.async {
                if let error = error {
                    let nsError = error as NSError
                    // GIDSignInErrorDomain = "com.google.GIDSignIn", código -2 = cancelado.
                    // Se compara por dominio/código para no depender del nombre del
                    // enum Swift (cambia entre versiones del pod GoogleSignIn).
                    if nsError.domain == "com.google.GIDSignIn" && nsError.code == -2 {
                        // Usuario canceló — no es error
                        self.isLoading = false
                        return
                    }
                    self.errorMessage = "Error al iniciar sesión con Google: \(error.localizedDescription)"
                    self.isLoading = false
                    return
                }

                guard let user = result?.user,
                      let idToken = user.idToken?.tokenString else {
                    self.errorMessage = "No se pudo obtener el token de Google."
                    self.isLoading = false
                    return
                }

                let credential = GoogleAuthProvider.credential(
                    withIDToken: idToken,
                    accessToken: user.accessToken.tokenString
                )

                Auth.auth().signIn(with: credential) { authResult, error in
                    self.isLoading = false
                    if let error = error {
                        self.errorMessage = "Error de Firebase: \(error.localizedDescription)"
                        return
                    }
                    guard let firebaseUser = authResult?.user else { return }
                    self.createUserIfNeeded(firebaseUser)
                }
            }
        }
    }

    // MARK: - Apple Sign-In
    
    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
        isLoading = true
        errorMessage = nil

        switch result {
        case .success(let authorization):
            guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let appleIDToken = appleIDCredential.identityToken,
                  let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
                errorMessage = "No se pudo obtener el token de Apple."
                isLoading = false
                return
            }

            let nonce = currentNonce ?? UUID().uuidString
            let credential = OAuthProvider.credential(
                withProviderID: "apple.com",
                idToken: idTokenString,
                rawNonce: nonce
            )

            Auth.auth().signIn(with: credential) { authResult, error in
                self.isLoading = false
                if let error = error {
                    self.errorMessage = "Error al autenticar con Apple: \(error.localizedDescription)"
                    return
                }
                guard let firebaseUser = authResult?.user else { return }

                // Apple solo da el nombre completo en el primer login
                var displayName = firebaseUser.displayName ?? ""
                if displayName.isEmpty {
                    let firstName = appleIDCredential.fullName?.givenName ?? ""
                    let lastName = appleIDCredential.fullName?.familyName ?? ""
                    displayName = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
                    if displayName.isEmpty {
                        displayName = firebaseUser.email?.components(separatedBy: "@").first ?? "usuario"
                    }
                }

                let changeRequest = firebaseUser.createProfileChangeRequest()
                changeRequest.displayName = displayName
                changeRequest.commitChanges { _ in }

                self.createUserIfNeeded(firebaseUser, displayName: displayName)
            }

        case .failure(let error):
            isLoading = false
            let nsError = error as NSError
            if nsError.code != ASAuthorizationError.Code.canceled.rawValue {
                errorMessage = "Error con Apple ID: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Helper

    private func createUserIfNeeded(_ firebaseUser: FirebaseAuth.User, displayName: String? = nil) {
        let name = displayName ?? firebaseUser.displayName
            ?? firebaseUser.email?.components(separatedBy: "@").first
            ?? "usuario"

        let user = User(
            uid: firebaseUser.uid,
            username: name,
            displayName: name,
            bio: "",
            avatarUrl: firebaseUser.photoURL?.absoluteString ?? "",
            avatarBase64: "",
            email: firebaseUser.email ?? "",
            followersCount: 0,
            followingCount: 0,
            postsCount: 0,
            isPrivate: false
        )
        appState.signIn(user: user)
    }
}

/**
 * Fondo con partículas decorativas animadas (efecto visual tipo Vivid).
 */
struct ParticleBackground: View {
    @State private var particles: [Particle] = (0..<20).map { _ in Particle.random() }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                ForEach(particles) { particle in
                    Circle()
                        .fill(particle.color.opacity(0.15))
                        .frame(width: particle.size, height: particle.size)
                        .position(
                            x: particle.x * geo.size.width,
                            y: particle.y * geo.size.height
                        )
                        .blur(radius: 2)
                }
            }
        }
    }
}

struct Particle: Identifiable {
    let id = UUID()
    let x: CGFloat
    let y: CGFloat
    let size: CGFloat
    let color: Color

    static func random() -> Particle {
        let colors: [Color] = [VividTheme.primary, VividTheme.secondary, VividTheme.accent]
        return Particle(
            x: .random(in: 0...1),
            y: .random(in: 0...1),
            size: .random(in: 20...80),
            color: colors.randomElement()!
        )
    }
}