import SwiftUI

/**
 * Sistema de diseño de Vivid para iOS.
 * Refleja el tema Material 3 Expressive de la app Android.
 *
 * Colores, gradientes, tipografía y formas consistentes con la identidad
 * de marca de Vivid.
 */
enum VividTheme {

    // ───────────────────── Colores principales ─────────────────────

    static let primary = Color(red: 0.47, green: 0.31, blue: 0.97)       // Violeta vibrante
    static let secondary = Color(red: 0.96, green: 0.33, blue: 0.58)     // Rosa neón
    static let accent = Color(red: 0.20, green: 0.84, blue: 0.97)        // Cyan eléctrico
    static let surface = Color(red: 0.08, green: 0.08, blue: 0.12)       // Negro azulado
    static let surfaceVariant = Color(red: 0.14, green: 0.13, blue: 0.19) // Gris oscuro
    static let onSurface = Color.white

    // ───────────────────── Gradientes ─────────────────────

    static let accentGradient = LinearGradient(
        colors: [primary, secondary, accent],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let backgroundGradient = LinearGradient(
        colors: [
            Color(red: 0.05, green: 0.05, blue: 0.09),
            Color(red: 0.10, green: 0.08, blue: 0.16),
            Color(red: 0.06, green: 0.06, blue: 0.10)
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    static let storyRingGradient = LinearGradient(
        colors: [secondary, primary, accent],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let cardGradient = LinearGradient(
        colors: [
            surfaceVariant.opacity(0.8),
            surface.opacity(0.6)
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    // ───────────────────── Tipografía ─────────────────────

    static func title(_ size: CGFloat = 28) -> Font {
        .system(size: size, weight: .bold, design: .rounded)
    }

    static func heading(_ size: CGFloat = 20) -> Font {
        .system(size: size, weight: .semibold, design: .rounded)
    }

    static func body(_ size: CGFloat = 16) -> Font {
        .system(size: size, weight: .regular, design: .default)
    }

    static func caption(_ size: CGFloat = 12) -> Font {
        .system(size: size, weight: .medium, design: .default)
    }

    // ───────────────────── Espaciado ─────────────────────

    static let spacingXS: CGFloat = 4
    static let spacingSM: CGFloat = 8
    static let spacingMD: CGFloat = 16
    static let spacingLG: CGFloat = 24
    static let spacingXL: CGFloat = 32

    // ───────────────────── Radio de bordes ─────────────────────

    static let cornerRadiusSM: CGFloat = 8
    static let cornerRadiusMD: CGFloat = 16
    static let cornerRadiusLG: CGFloat = 24
    static let cornerRadiusXL: CGFloat = 32
}

// ───────────────────── Modificadores reutilizables ─────────────────────

extension View {
    /** Aplica el fondo degradado de Vivid. */
    func vividBackground() -> some View {
        self.background(VividTheme.backgroundGradient.ignoresSafeArea())
    }

    /** Aplica estilo de tarjeta Vivid. */
    func vividCard() -> some View {
        self
            .padding(VividTheme.spacingMD)
            .background(
                RoundedRectangle(cornerRadius: VividTheme.cornerRadiusMD, style: .continuous)
                    .fill(VividTheme.cardGradient)
            )
            .overlay(
                RoundedRectangle(cornerRadius: VividTheme.cornerRadiusMD, style: .continuous)
                    .strokeBorder(.white.opacity(0.06), lineWidth: 1)
            )
    }

    /** Botón primario con gradiente. */
    func vividPrimaryButton() -> some View {
        self
            .font(VividTheme.heading(16))
            .foregroundStyle(.white)
            .padding(.vertical, 14)
            .padding(.horizontal, 32)
            .background(
                Capsule().fill(VividTheme.accentGradient)
            )
    }
}
