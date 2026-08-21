import Foundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseMessaging
import UserNotifications
import UIKit

/// Registra el token APNs/FCM bajo `/users/{uid}/fcmTokens/{token}`, la misma
/// ruta que Android y el worker de notificaciones ya utilizan.
final class PushNotificationService: NSObject, MessagingDelegate, UNUserNotificationCenterDelegate {
    static let shared = PushNotificationService()

    private override init() { super.init() }

    func configure() {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
    }

    func requestPermissionAndRegister() async {
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        guard granted else { return }
        await MainActor.run { UIApplication.shared.registerForRemoteNotifications() }
    }

    func registerCurrentToken() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        Messaging.messaging().token { token, error in
            guard error == nil, let token else { return }
            Firestore.firestore().collection("users").document(uid).collection("fcmTokens").document(token).setData([
                "createdAt": Int64(Date().timeIntervalSince1970 * 1_000), "platform": "ios", "appVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
            ], merge: true)
        }
    }

    func unregisterCurrentToken(for uid: String) {
        Messaging.messaging().token { token, _ in
            guard let token else { return }
            Firestore.firestore().collection("users").document(uid).collection("fcmTokens").document(token).delete()
        }
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) { registerCurrentToken() }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions { [.banner, .sound, .badge] }
}
