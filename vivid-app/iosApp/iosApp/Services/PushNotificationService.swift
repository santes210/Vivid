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
        let reply = UNTextInputNotificationAction(identifier: "VIVID_REPLY", title: "Responder", options: [], textInputButtonTitle: "Enviar", textInputPlaceholder: "Mensaje…")
        let markRead = UNNotificationAction(identifier: "VIVID_MARK_READ", title: "Marcar como leído", options: [])
        let messageCategory = UNNotificationCategory(identifier: "VIVID_MESSAGE", actions: [reply, markRead], intentIdentifiers: [], options: [])
        let view = UNNotificationAction(identifier: "VIVID_OPEN", title: "Ver", options: [.foreground])
        let contentCategory = UNNotificationCategory(identifier: "VIVID_CONTENT", actions: [view], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([messageCategory, contentCategory])
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

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let data = response.notification.request.content.userInfo
        let type = (data["type"] as? String) ?? ""
        let targetId = (data["targetId"] as? String) ?? (data["postId"] as? String) ?? (data["reelId"] as? String) ?? (data["userId"] as? String) ?? (data["chatId"] as? String) ?? ""
        if response.actionIdentifier == "VIVID_MARK_READ", let chatId = data["chatId"] as? String {
            try? await ChatRepository().markMessagesRead(chatId: chatId); return
        }
        if let reply = response as? UNTextInputNotificationResponse,
           let receiverId = data["senderId"] as? String {
            let senderName = Auth.auth().currentUser?.displayName ?? "Usuario"
            _ = try? await ChatRepository().sendMessage(receiverId: receiverId, receiverName: data["senderName"] as? String ?? "Usuario", text: reply.userText, senderName: senderName)
            return
        }
        await MainActor.run {
            switch type {
            case "follow", "follow_request", "profile":
                NotificationCenter.default.post(name: .vividDeepLink, object: AppState.DeepLink.profile(targetId))
            case "message", "story_reply":
                NotificationCenter.default.post(name: .vividDeepLink, object: AppState.DeepLink.chat(targetId))
            case "comment", "like", "post":
                NotificationCenter.default.post(name: .vividDeepLink, object: AppState.DeepLink.post(targetId))
            case "reel":
                NotificationCenter.default.post(name: .vividDeepLink, object: AppState.DeepLink.reel(targetId))
            default:
                break
            }
        }
    }
}

extension Notification.Name {
    static let vividDeepLink = Notification.Name("vividDeepLink")
}
