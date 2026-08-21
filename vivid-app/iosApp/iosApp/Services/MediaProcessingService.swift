import AVFoundation
import CoreImage
import Foundation
import UIKit

struct VideoEditOptions {
    var start: TimeInterval = 0
    var end: TimeInterval?
    var musicURL: URL?
    var musicStart: TimeInterval = 0
    var originalVolume: Float = 1
    var musicVolume: Float = 0.7
    var watermark: String?
    var preset: String = AVAssetExportPreset1280x720
}

enum MediaProcessingError: LocalizedError {
    case invalidDuration, exportUnavailable, exportFailed(String), cancelled
    var errorDescription: String? {
        switch self {
        case .invalidDuration: return "El rango de video no es válido."
        case .exportUnavailable: return "Este video no se puede procesar en el dispositivo."
        case .exportFailed(let message): return "No se pudo procesar el video: \(message)"
        case .cancelled: return "La edición fue cancelada."
        }
    }
}

/// Pipeline nativo equivalente a VideoTrimmer/MusicSelector/VideoCompressor:
/// recorta, mezcla audio, añade watermark y exporta H.264/AAC antes de subir.
final class MediaProcessingService {
    static let shared = MediaProcessingService()
    private init() {}

    func duration(of url: URL) async -> TimeInterval {
        let asset = AVURLAsset(url: url)
        return (try? await asset.load(.duration).seconds) ?? 0
    }

    func processVideo(at url: URL, options: VideoEditOptions) async throws -> URL {
        let asset = AVURLAsset(url: url)
        let assetDuration = try await asset.load(.duration)
        let total = assetDuration.seconds
        let start = max(0, min(options.start, total))
        let end = min(options.end ?? total, total)
        guard end - start >= 0.25 else { throw MediaProcessingError.invalidDuration }

        let composition = AVMutableComposition()
        let sourceRange = CMTimeRange(
            start: CMTime(seconds: start, preferredTimescale: 600),
            duration: CMTime(seconds: end - start, preferredTimescale: 600)
        )
        let videoTracks = try await asset.loadTracks(withMediaType: .video)
        guard let sourceVideo = videoTracks.first,
              let videoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
        else { throw MediaProcessingError.exportUnavailable }
        try videoTrack.insertTimeRange(sourceRange, of: sourceVideo, at: .zero)
        videoTrack.preferredTransform = try await sourceVideo.load(.preferredTransform)

        let audioMix = AVMutableAudioMix()
        var audioParameters: [AVMutableAudioMixInputParameters] = []
        if let sourceAudio = try await asset.loadTracks(withMediaType: .audio).first,
           let audioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            try audioTrack.insertTimeRange(sourceRange, of: sourceAudio, at: .zero)
            let params = AVMutableAudioMixInputParameters(track: audioTrack)
            params.setVolume(options.originalVolume, at: .zero)
            audioParameters.append(params)
        }

        if let musicURL = options.musicURL {
            let musicAsset = AVURLAsset(url: musicURL)
            if let sourceMusic = try await musicAsset.loadTracks(withMediaType: .audio).first,
               let musicTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
                let musicDuration = try await musicAsset.load(.duration).seconds
                let musicStart = min(max(0, options.musicStart), max(0, musicDuration - 0.1))
                let available = min(end - start, musicDuration - musicStart)
                if available > 0 {
                    try musicTrack.insertTimeRange(
                        CMTimeRange(start: CMTime(seconds: musicStart, preferredTimescale: 600), duration: CMTime(seconds: available, preferredTimescale: 600)),
                        of: sourceMusic, at: .zero
                    )
                    let params = AVMutableAudioMixInputParameters(track: musicTrack)
                    params.setVolume(options.musicVolume, at: .zero)
                    audioParameters.append(params)
                }
            }
        }
        audioMix.inputParameters = audioParameters

        guard let exporter = AVAssetExportSession(asset: composition, presetName: options.preset) else {
            throw MediaProcessingError.exportUnavailable
        }
        let output = FileManager.default.temporaryDirectory
            .appendingPathComponent("vivid-edited-\(UUID().uuidString)").appendingPathExtension("mp4")
        exporter.outputURL = output
        exporter.outputFileType = .mp4
        exporter.shouldOptimizeForNetworkUse = true
        exporter.audioMix = audioMix
        if let text = options.watermark?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
            exporter.videoComposition = try await watermarkComposition(asset: composition, videoTrack: videoTrack, sourceTrack: sourceVideo, text: text)
        }
        try await export(exporter)
        return output
    }

    private func watermarkComposition(asset: AVAsset, videoTrack: AVCompositionTrack, sourceTrack: AVAssetTrack, text: String) async throws -> AVMutableVideoComposition {
        let naturalSize = try await sourceTrack.load(.naturalSize)
        let transform = try await sourceTrack.load(.preferredTransform)
        let transformed = naturalSize.applying(transform)
        let renderSize = CGSize(width: abs(transformed.width), height: abs(transformed.height))
        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: try await asset.load(.duration))
        let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: videoTrack)
        layerInstruction.setTransform(transform, at: .zero)
        instruction.layerInstructions = [layerInstruction]

        let parent = CALayer(); parent.frame = CGRect(origin: .zero, size: renderSize)
        let video = CALayer(); video.frame = parent.frame; parent.addSublayer(video)
        let watermark = CATextLayer()
        watermark.string = text
        watermark.font = UIFont.systemFont(ofSize: max(20, renderSize.width * 0.035), weight: .semibold)
        watermark.fontSize = max(20, renderSize.width * 0.035)
        watermark.foregroundColor = UIColor.white.withAlphaComponent(0.82).cgColor
        watermark.shadowOpacity = 0.6; watermark.shadowRadius = 3; watermark.alignmentMode = .right
        watermark.contentsScale = UIScreen.main.scale
        watermark.frame = CGRect(x: 20, y: 24, width: renderSize.width - 40, height: 55)
        parent.addSublayer(watermark)

        let result = AVMutableVideoComposition()
        result.instructions = [instruction]
        result.renderSize = renderSize
        result.frameDuration = CMTime(value: 1, timescale: 30)
        result.animationTool = AVVideoCompositionCoreAnimationTool(postProcessingAsVideoLayer: video, in: parent)
        return result
    }

    private func export(_ exporter: AVAssetExportSession) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            exporter.exportAsynchronously {
                switch exporter.status {
                case .completed: continuation.resume()
                case .cancelled: continuation.resume(throwing: MediaProcessingError.cancelled)
                default: continuation.resume(throwing: MediaProcessingError.exportFailed(exporter.error?.localizedDescription ?? "Error desconocido"))
                }
            }
        }
    }
}
