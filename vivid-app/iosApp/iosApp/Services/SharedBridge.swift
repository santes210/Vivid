import Foundation

/**
 * Bridge entre el módulo KMP (Shared) y la app iOS nativa.
 *
 * Este servicio adapta las interfaces Kotlin del módulo Shared
 * a protocolos Swift idiomáticos, manejando la conversión de tipos
 * y la gestión de concurrencia (Kotlin coroutines ↔ Swift async/await).
 *
 * En producción, las implementaciones de ChatRepository, FollowRepository, etc.
 * se crean en Kotlin y se pasan a Swift a través de este bridge.
 */
class SharedBridge {

    static let shared = SharedBridge()

    private init() {}

    // MARK: - Conversión de tipos Kotlin ↔ Swift

    /// Convierte un callback de Kotlin a async/await de Swift.
    func awaitKotlinCallback<T>(
        _ operation: @escaping ((T) -> Void, (Error) -> Void) -> Void
    ) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            operation(
                { result in continuation.resume(returning: result) },
                { error in continuation.resume(throwing: error) }
            )
        }
    }

    // MARK: - Conversión de modelos

    /// Convierte un timestamp de Kotlin (Long/Int64) a Date de Swift.
    func toDate(_ timestamp: Int64) -> Date {
        Date(timeIntervalSince1970: Double(timestamp) / 1000.0)
    }

    /// Convierte una Date de Swift a timestamp Kotlin (Int64).
    func toTimestamp(_ date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1000)
    }
}

/**
 * Adaptador del ChatRepository Kotlin para Swift.
 * Envuelve las llamadas al repositorio compartido.
 */
@MainActor
class ChatRepositoryAdapter {
    // En producción:
    // private let repository: Shared.ChatRepository

    func getChats() async throws -> [ChatUI] {
        // let kotlinChats = try await SharedBridge.shared.awaitKotlinCallback { success, failure in
        //     repository.getChatsFlow()...
        // }
        // return kotlinChats.map { ChatUI(from: $0) }
        return []
    }

    func sendMessage(chatId: String, text: String, receiverId: String) async throws {
        // try await SharedBridge.shared.awaitKotlinCallback { success, failure in
        //     repository.sendMessage(chatId: chatId, text: text, receiverId: receiverId)
        // }
    }
}

/**
 * Adaptador del FollowRepository Kotlin para Swift.
 */
@MainActor
class FollowRepositoryAdapter {
    func toggleFollow(targetUserId: String) async throws -> String {
        // let result = try await SharedBridge.shared.awaitKotlinCallback { success, failure in
        //     repository.toggleFollow(targetUserId: targetUserId)
        // }
        // return result.name
        return "FOLLOWED"
    }
}
