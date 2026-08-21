import AVFoundation
import Foundation

/// Grabador y reproductor de notas de voz. Mantiene la sesión de audio activa
/// solo durante la grabación/reproducción y genera AAC compatible con Android.
@MainActor
final class AudioNoteService: NSObject, ObservableObject, AVAudioRecorderDelegate, AVAudioPlayerDelegate {
    @Published private(set) var isRecording = false
    @Published private(set) var isPlaying = false
    @Published private(set) var elapsed: TimeInterval = 0
    @Published var errorMessage: String?

    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private var timer: Timer?
    private var startedAt: Date?

    func startRecording() async -> Bool {
        let granted = await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { continuation.resume(returning: $0) }
        }
        guard granted else {
            errorMessage = "Activa el micrófono en Ajustes para enviar notas de voz."
            return false
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("voice-\(UUID().uuidString)").appendingPathExtension("m4a")
            recorder = try AVAudioRecorder(url: url, settings: [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 44_100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
                AVEncoderBitRateKey: 96_000
            ])
            recorder?.delegate = self
            recorder?.isMeteringEnabled = true
            guard recorder?.record() == true else { throw AudioNoteError.couldNotRecord }
            startedAt = Date(); elapsed = 0; isRecording = true
            startTimer()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    /// Finaliza y devuelve el archivo y su duración. Grabaciones menores a 0.4 s se descartan.
    func stopRecording() -> (url: URL, durationMs: Int64)? {
        guard let recorder, isRecording else { return nil }
        recorder.stop(); timer?.invalidate(); timer = nil; isRecording = false
        let duration = max(recorder.currentTime, Date().timeIntervalSince(startedAt ?? Date()))
        self.recorder = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        guard duration >= 0.4 else {
            try? FileManager.default.removeItem(at: recorder.url)
            return nil
        }
        return (recorder.url, Int64(duration * 1_000))
    }

    func cancelRecording() {
        let url = recorder?.url
        recorder?.stop(); recorder?.deleteRecording(); recorder = nil
        if let url { try? FileManager.default.removeItem(at: url) }
        timer?.invalidate(); timer = nil; isRecording = false; elapsed = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func togglePlayback(url: URL) {
        if isPlaying { stopPlayback(); return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio)
            try session.setActive(true)
            player = try AVAudioPlayer(contentsOf: url)
            player?.delegate = self
            player?.play()
            isPlaying = true
        } catch { errorMessage = error.localizedDescription }
    }

    func stopPlayback() {
        player?.stop(); player = nil; isPlaying = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func startTimer() {
        timer?.invalidate()
        timer = .scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.elapsed = Date().timeIntervalSince(self?.startedAt ?? Date()) }
        }
    }

    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor in if !flag { self.errorMessage = "No se pudo guardar la nota de voz." } }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.isPlaying = false; self.player = nil }
    }
}

enum AudioNoteError: LocalizedError {
    case couldNotRecord
    var errorDescription: String? { "No se pudo iniciar la grabación." }
}
