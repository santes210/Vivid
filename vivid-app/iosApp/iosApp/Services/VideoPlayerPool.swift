import AVFoundation
import Foundation

/// Pool acotado de AVPlayer. Precarga el reel siguiente y evita crear un decoder
/// nuevo en cada celda, equivalente al pool de ExoPlayer de Android.
@MainActor
final class VideoPlayerPool {
    static let shared = VideoPlayerPool(capacity: 4)

    private struct Entry {
        let player: AVPlayer
        var lastAccess: Date
    }
    private let capacity: Int
    private var entries: [URL: Entry] = [:]

    init(capacity: Int) { self.capacity = max(2, capacity) }

    func player(for url: URL) -> AVPlayer {
        if var entry = entries[url] {
            entry.lastAccess = Date(); entries[url] = entry
            return entry.player
        }
        evictIfNeeded()
        let item = AVPlayerItem(url: url)
        item.preferredForwardBufferDuration = 4
        let player = AVPlayer(playerItem: item)
        player.automaticallyWaitsToMinimizeStalling = true
        entries[url] = Entry(player: player, lastAccess: Date())
        return player
    }

    func prefetch(_ urls: [URL]) {
        for url in urls.prefix(capacity) {
            let player = player(for: url)
            player.preroll(atRate: 1) { _ in }
        }
    }

    func release(except urls: Set<URL> = []) {
        entries.keys.filter { !urls.contains($0) }.forEach {
            entries[$0]?.player.pause(); entries.removeValue(forKey: $0)
        }
    }

    private func evictIfNeeded() {
        guard entries.count >= capacity,
              let oldest = entries.min(by: { $0.value.lastAccess < $1.value.lastAccess })?.key else { return }
        entries[oldest]?.player.pause(); entries.removeValue(forKey: oldest)
    }
}
