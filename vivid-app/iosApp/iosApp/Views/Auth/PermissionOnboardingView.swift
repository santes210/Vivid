import AVFoundation
import Photos
import SwiftUI
import UserNotifications

/// Onboarding explícito: explica cada permiso antes de mostrar el prompt del
/// sistema. Los permisos se solicitan solo al tocar Continuar, nunca al login.
struct PermissionOnboardingView: View {
    @Binding var isComplete: Bool
    @State private var cameraGranted = false
    @State private var microphoneGranted = false
    @State private var notificationsGranted = false
    @State private var isRequesting = false

    var body: some View {
        ZStack {
            VividTheme.backgroundGradient.ignoresSafeArea()
            VStack(spacing: 26) {
                Image(systemName: "sparkles").font(.system(size: 56)).foregroundStyle(VividTheme.accentGradient)
                Text("Vive Vivid al máximo").font(.title.bold()).foregroundStyle(.white)
                Text("Tú decides. Puedes cambiar estos permisos en Ajustes cuando quieras.")
                    .multilineTextAlignment(.center).foregroundStyle(.white.opacity(0.65))
                permissionRow("Cámara", detail: "Crea posts, reels y stories", icon: "camera.fill", granted: cameraGranted)
                permissionRow("Micrófono", detail: "Graba videos y notas de voz", icon: "mic.fill", granted: microphoneGranted)
                permissionRow("Notificaciones", detail: "Mensajes y actividad importante", icon: "bell.fill", granted: notificationsGranted)
                Button(isRequesting ? "Solicitando…" : "Continuar") { Task { await requestPermissions() } }
                    .disabled(isRequesting).buttonStyle(.borderedProminent).tint(VividTheme.primary)
                Button("Ahora no") { isComplete = true }.foregroundStyle(.white.opacity(0.6))
            }
            .padding(28)
        }
        .task { await refreshStatuses() }
    }

    private func permissionRow(_ title: String, detail: String, icon: String, granted: Bool) -> some View {
        HStack(spacing: 16) {
            Image(systemName: icon).frame(width: 42, height: 42).background(VividTheme.primary.opacity(0.25), in: Circle())
            VStack(alignment: .leading) { Text(title).font(.headline); Text(detail).font(.caption).foregroundStyle(.white.opacity(0.55)) }
            Spacer(); Image(systemName: granted ? "checkmark.circle.fill" : "circle").foregroundStyle(granted ? .green : .white.opacity(0.3))
        }.foregroundStyle(.white)
    }

    private func refreshStatuses() async {
        cameraGranted = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        microphoneGranted = AVCaptureDevice.authorizationStatus(for: .audio) == .authorized
        notificationsGranted = (await UNUserNotificationCenter.current().notificationSettings()).authorizationStatus == .authorized
    }

    private func requestPermissions() async {
        isRequesting = true
        cameraGranted = await AVCaptureDevice.requestAccess(for: .video)
        microphoneGranted = await AVCaptureDevice.requestAccess(for: .audio)
        await PushNotificationService.shared.requestPermissionAndRegister()
        notificationsGranted = (await UNUserNotificationCenter.current().notificationSettings()).authorizationStatus == .authorized
        isRequesting = false; isComplete = true
    }
}
