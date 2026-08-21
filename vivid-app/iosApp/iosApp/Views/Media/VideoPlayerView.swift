import SwiftUI
import AVKit
import AVFoundation

/// Reproductor AVPlayer reutilizable para reels, posts y stories.
struct LoopingVideoPlayer: UIViewRepresentable {
    let url: URL
    var isPlaying: Bool = true
    var isMuted: Bool = false

    func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.configure(url: url, muted: isMuted)
        return view
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.update(url: url, playing: isPlaying, muted: isMuted)
    }

    static func dismantleUIView(_ uiView: PlayerUIView, coordinator: ()) {
        uiView.pause()
    }

    final class PlayerUIView: UIView {
        private var player: AVPlayer?
        private var playerLayer: AVPlayerLayer?
        private var currentURL: URL?
        private var endObserver: NSObjectProtocol?

        override func layoutSubviews() {
            super.layoutSubviews()
            playerLayer?.frame = bounds
        }

        func configure(url: URL, muted: Bool) {
            backgroundColor = .black
            let item = AVPlayerItem(url: url)
            let player = AVPlayer(playerItem: item)
            player.isMuted = muted
            let layer = AVPlayerLayer(player: player)
            layer.videoGravity = .resizeAspectFill
            layer.frame = bounds
            self.layer.addSublayer(layer)
            self.player = player
            self.playerLayer = layer
            self.currentURL = url
            observeLoop(item: item)
            player.play()
        }

        func update(url: URL, playing: Bool, muted: Bool) {
            if currentURL != url {
                player?.pause()
                let item = AVPlayerItem(url: url)
                player?.replaceCurrentItem(with: item)
                currentURL = url
                observeLoop(item: item)
            }
            player?.isMuted = muted
            if playing { player?.play() } else { player?.pause() }
        }

        func pause() { player?.pause() }

        private func observeLoop(item: AVPlayerItem) {
            if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
            endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                self?.player?.seek(to: .zero)
                self?.player?.play()
            }
        }

        deinit {
            if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        }
    }
}
