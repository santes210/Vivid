import SwiftUI
import AVFoundation

/**
 * Vista de cámara para iOS usando AVFoundation.
 * Equivalente al CameraScreen de Android con CameraX.
 *
 * Soporta:
 * - Foto y video
 * - Cambio de cámara frontal/trasera
 * - Flash on/off/auto
 * - Enfoque por tap
 */
struct CameraView: View {
    @StateObject private var cameraManager = CameraManager()
    @State private var flashMode: FlashMode = .off
    @State private var captureMode: CaptureMode = .photo
    @State private var isRecording = false
    @State private var showPreview = false
    @State private var capturedMedia: CapturedMedia? = nil

    enum FlashMode: String {
        case off = "bolt.slash"
        case on = "bolt.fill"
        case auto = "bolt.badge.a"
    }

    enum CaptureMode: String, CaseIterable {
        case photo = "Foto"
        case video = "Video"
    }

    struct CapturedMedia {
        let url: URL
        let isVideo: Bool
    }

    var body: some View {
        ZStack {
            // Vista previa de la cámara
            CameraPreview(session: cameraManager.session)
                .ignoresSafeArea()
                .onTapGesture { location in
                    cameraManager.focus(at: location)
                }

            // Indicador de enfoque
            if cameraManager.showFocusIndicator {
                Circle()
                    .stroke(Color.yellow, lineWidth: 1.5)
                    .frame(width: 60, height: 60)
                    .position(cameraManager.focusPoint)
                    .transition(.opacity)
            }

            VStack {
                // Controles superiores
                HStack {
                    Button(action: { cameraManager.toggleTorch() }) {
                        Image(systemName: flashMode.rawValue)
                            .font(.system(size: 20))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(Circle().fill(.black.opacity(0.4)))
                    }

                    Spacer()

                    Button(action: { cameraManager.flipCamera() }) {
                        Image(systemName: "camera.rotate.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(Circle().fill(.black.opacity(0.4)))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 60)

                Spacer()

                // Selector de modo
                HStack(spacing: 20) {
                    ForEach(CaptureMode.allCases, id: \.rawValue) { mode in
                        Button(action: { captureMode = mode }) {
                            Text(mode.rawValue)
                                .font(.subheadline.bold())
                                .foregroundStyle(captureMode == mode ? .yellow : .white.opacity(0.6))
                        }
                    }
                }
                .padding(.bottom, 20)

                // Controles de captura
                HStack(spacing: 40) {
                    // Galería (última foto)
                    Button(action: {}) {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(.white.opacity(0.2))
                            .frame(width: 48, height: 48)
                            .overlay(
                                Image(systemName: "photo")
                                    .foregroundStyle(.white.opacity(0.6))
                            )
                    }

                    // Botón de captura
                    Button(action: capture) {
                        ZStack {
                            Circle()
                                .stroke(.white, lineWidth: 4)
                                .frame(width: 72, height: 72)

                            if captureMode == .video {
                                Circle()
                                    .fill(isRecording ? Color.red : .white)
                                    .frame(width: isRecording ? 28 : 60)
                                    .cornerRadius(isRecording ? 4 : 30)
                            } else {
                                Circle()
                                    .fill(.white)
                                    .frame(width: 60, height: 60)
                            }
                        }
                    }

                    // Filtros
                    Button(action: {}) {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(.white.opacity(0.2))
                            .frame(width: 48, height: 48)
                            .overlay(
                                Image(systemName: "camera.filters")
                                    .foregroundStyle(.white.opacity(0.6))
                            )
                    }
                }
                .padding(.bottom, 40)
            }
        }
        .onAppear {
            cameraManager.checkPermissions()
        }
        .onDisappear {
            cameraManager.stopSession()
        }
        .fullScreenCover(isPresented: $showPreview) {
            if let media = capturedMedia {
                CapturePreviewView(media: media) {
                    showPreview = false
                    capturedMedia = nil
                }
            }
        }
    }

    private func capture() {
        switch captureMode {
        case .photo:
            cameraManager.capturePhoto { url in
                capturedMedia = CapturedMedia(url: url, isVideo: false)
                showPreview = true
            }
        case .video:
            if isRecording {
                cameraManager.stopRecording { url in
                    capturedMedia = CapturedMedia(url: url, isVideo: true)
                    showPreview = true
                    isRecording = false
                }
            } else {
                cameraManager.startRecording()
                isRecording = true
            }
        }
    }
}

/**
 * Wrapper de UIView para la vista previa de AVFoundation.
 */
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        if let previewLayer = uiView.layer.sublayers?.first as? AVCaptureVideoPreviewLayer {
            previewLayer.frame = uiView.bounds
        }
    }
}

/**
 * Vista previa del contenido capturado.
 */
struct CapturePreviewView: View {
    let media: CameraView.CapturedMedia
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Preview de la imagen/video
            // En producción: mostrar la imagen real o video player

            VStack {
                HStack {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(Circle().fill(.white.opacity(0.2)))
                    }
                    Spacer()
                }
                .padding()

                Spacer()

                // Caption input
                VStack(spacing: 16) {
                    TextField("Escribe un pie de foto...", text: .constant(""))
                        .textFieldStyle(.plain)
                        .foregroundStyle(.white)
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.1)))

                    HStack(spacing: 16) {
                        Button("Cancelar") { onDismiss() }
                            .font(.subheadline.bold())
                            .foregroundStyle(.white.opacity(0.6))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Capsule().fill(.white.opacity(0.1)))

                        Button("Publicar") {}
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Capsule().fill(VividTheme.primary))
                    }
                }
                .padding()
            }
        }
    }
}

/**
 * Gestor de la cámara usando AVFoundation.
 * Equivalente al CameraController de Android con CameraX.
 */
@MainActor
class CameraManager: NSObject, ObservableObject {
    let session = AVCaptureSession()

    @Published var showFocusIndicator = false
    @Published var focusPoint: CGPoint = .zero
    @Published var isFlashOn = false

    private let sessionQueue = DispatchQueue(label: "com.vivid.camera.session")
    private var videoDeviceInput: AVCaptureDeviceInput?
    private let photoOutput = AVCapturePhotoOutput()
    private let movieOutput = AVCaptureMovieFileOutput()
    private var photoCaptureCompletion: ((URL) -> Void)?
    private var videoCaptureCompletion: ((URL) -> Void)?
    private var isUsingFrontCamera = false

    func checkPermissions() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted { self.setupSession() }
            }
        default:
            break
        }
    }

    func setupSession() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .high

            // Configurar entrada de video
            if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) {
                if let input = try? AVCaptureDeviceInput(device: device) {
                    if self.session.canAddInput(input) {
                        self.session.addInput(input)
                        self.videoDeviceInput = input
                    }
                }
            }

            // Configurar entrada de audio
            if let audioDevice = AVCaptureDevice.default(for: .audio) {
                if let audioInput = try? AVCaptureDeviceInput(device: audioDevice) {
                    if self.session.canAddInput(audioInput) {
                        self.session.addInput(audioInput)
                    }
                }
            }

            // Configurar salidas
            if self.session.canAddOutput(self.photoOutput) {
                self.session.addOutput(self.photoOutput)
            }

            if self.session.canAddOutput(self.movieOutput) {
                self.session.addOutput(self.movieOutput)
            }

            self.session.commitConfiguration()
            self.session.startRunning()
        }
    }

    func stopSession() {
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    func flipCamera() {
        isUsingFrontCamera.toggle()
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.session.beginConfiguration()

            if let currentInput = self.videoDeviceInput {
                self.session.removeInput(currentInput)
            }

            let position: AVCaptureDevice.Position = self.isUsingFrontCamera ? .front : .back
            if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) {
                if let input = try? AVCaptureDeviceInput(device: device) {
                    if self.session.canAddInput(input) {
                        self.session.addInput(input)
                        self.videoDeviceInput = input
                    }
                }
            }

            self.session.commitConfiguration()
        }
    }

    func toggleTorch() {
        guard let device = videoDeviceInput?.device else { return }
        if device.hasTorch {
            try? device.lockForConfiguration()
            device.torchMode = device.torchMode == .on ? .off : .on
            isFlashOn = device.torchMode == .on
            device.unlockForConfiguration()
        }
    }

    func focus(at point: CGPoint) {
        // Guardar punto para indicador visual (en coordenadas de vista)
        focusPoint = point
        showFocusIndicator = true

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.showFocusIndicator = false
        }

        guard let device = videoDeviceInput?.device else { return }
        try? device.lockForConfiguration()
        if device.isFocusPointOfInterestSupported {
            // AVFoundation necesita coordenadas normalizadas (0-1),
            // pero el punto llega en coordenadas de vista desde SwiftUI.
            // Como la previewLayer usa resizeAspectFill, asumimos que
            // el punto ya está en el rango visible aproximado.
            let normalizedPoint = CGPoint(
                x: min(max(point.x / UIScreen.main.bounds.width, 0), 1),
                y: min(max(point.y / UIScreen.main.bounds.height, 0), 1)
            )
            device.focusPointOfInterest = normalizedPoint
            device.focusMode = .autoFocus
        }
        device.unlockForConfiguration()
    }

    func capturePhoto(completion: @escaping (URL) -> Void) {
        photoCaptureCompletion = completion
        let settings = AVCapturePhotoSettings()
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    func startRecording() {
        let outputPath = NSTemporaryDirectory() + "vivid_recording_\(Int(Date().timeIntervalSince1970)).mp4"
        movieOutput.startRecording(to: URL(fileURLWithPath: outputPath), recordingDelegate: self)
    }

    func stopRecording(completion: @escaping (URL) -> Void) {
        videoCaptureCompletion = completion
        movieOutput.stopRecording()
    }
}

// MARK: - AVCapturePhotoCaptureDelegate

extension CameraManager: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(_ output: AVCapturePhotoOutput,
                                  didFinishProcessingPhoto photo: AVCapturePhoto,
                                  error: Error?) {
        guard let data = photo.fileDataRepresentation(),
              error == nil else { return }

        let url = URL(fileURLWithPath: NSTemporaryDirectory() + "vivid_photo_\(Int(Date().timeIntervalSince1970)).jpg")
        try? data.write(to: url)

        Task { @MainActor in
            self.photoCaptureCompletion?(url)
        }
    }
}

// MARK: - AVCaptureFileOutputRecordingDelegate

extension CameraManager: AVCaptureFileOutputRecordingDelegate {
    nonisolated func fileOutput(_ output: AVCaptureFileOutput,
                                 didFinishRecordingTo outputFileURL: URL,
                                 from connections: [AVCaptureConnection],
                                 error: Error?) {
        guard error == nil else { return }

        Task { @MainActor in
            self.videoCaptureCompletion?(outputFileURL)
        }
    }
}
