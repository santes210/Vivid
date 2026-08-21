import SwiftUI
import AVKit
import AVFoundation

/// Reproductor reutilizable respaldado por VideoPlayerPool. Soporta mute en
/// caliente y pausa automática cuando una celda deja de estar visible.
struct LoopingVideoPlayer: UIViewRepresentable {
    let url: URL
    var isPlaying: Bool = true
    var isMuted: Bool = false

    func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.update(url: url, playing: isPlaying, muted: isMuted)
        return view
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.update(url: url, playing: isPlaying, muted: isMuted)
    }

    static func dismantleUIView(_ uiView: PlayerUIView, coordinator: ()) { uiView.pause() }

    @MainActor
    final class PlayerUIView: UIView {
        private var player: AVPlayer?
        private let playerLayer = AVPlayerLayer()
        private var currentURL: URL?
        private var endObserver: NSObjectProtocol?

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .black
            playerLayer.videoGravity = .resizeAspectFill
            layer.addSublayer(playerLayer)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override func layoutSubviews() { super.layoutSubviews(); playerLayer.frame = bounds }

        func update(url: URL, playing: Bool, muted: Bool) {
            if currentURL != url {
                removeObserver(); player?.pause()
                player = VideoPlayerPool.shared.player(for: url)
                playerLayer.player = player
                currentURL = url
                if let item = player?.currentItem {
                    endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                        Task { @MainActor in self?.player?.seek(to: .zero); self?.player?.play() }
                    }
                }
            }
            player?.isMuted = muted
            if playing { player?.play() } else { player?.pause() }
        }

        func pause() { player?.pause() }
        private func removeObserver() {
            if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
            endObserver = nil
        }
        deinit { if let endObserver { NotificationCenter.default.removeObserver(endObserver) } }
    }
}
