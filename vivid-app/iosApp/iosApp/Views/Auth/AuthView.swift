import SwiftUI

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

                // Botones de autenticación
                VStack(spacing: 16) {
                    // Sign in with Apple
                    Button(action: signInWithApple) {
                        HStack(spacing: 12) {
                            Image(systemName: "apple.logo")
                                .font(.system(size: 20))
                            Text("Continuar con Apple")
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
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 60)
            }
        }
        .onAppear { isAnimating = true }
    }

    private func signInWithApple() {
        // Integración con AuthenticationServices + Firebase Auth
        // let provider = OAuthProvider(providerID: "apple.com")
        // provider.getCredentialWith(nil) { credential, error in ... }
        print("Sign in with Apple tapped")
    }

    private func signInWithGoogle() {
        // Integración con GoogleSignIn SDK + Firebase Auth
        // GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { result, error in ... }
        print("Sign in with Google tapped")
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
