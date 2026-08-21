import FirebaseAnalytics
import Foundation

/// Punto único para analytics; evita enviar texto, emails o IDs sensibles.
enum VividAnalytics {
    static func event(_ name: String, parameters: [String: Any] = [:]) {
        Analytics.logEvent(name, parameters: parameters)
    }
    static func screen(_ name: String) {
        Analytics.logEvent(AnalyticsEventScreenView, parameters: [AnalyticsParameterScreenName: name])
    }
    static func error(feature: String, error: Error) {
        Analytics.logEvent("non_fatal_error", parameters: ["feature": feature, "error_type": String(describing: type(of: error))])
    }
}
