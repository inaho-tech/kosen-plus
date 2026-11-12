import SwiftUI
import AVFoundation
import CoreImage
import UIKit

final class CameraController: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let session = AVCaptureSession()
    @Published var images: [URL] = []
    @Published var error: String?
    private let queue = DispatchQueue(label: "plus.camera")
    private let context = CIContext()
    private var latest: CIImage?

    override init() {
        super.init()
        refreshGallery()
    }

    func start() {
        AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in
            guard let self else { return }
            if allowed { self.configure() } else { DispatchQueue.main.async { self.error = "カメラの利用が許可されていません" } }
        }
    }

    func stop() { queue.async { if self.session.isRunning { self.session.stopRunning() } } }

    private func configure() {
        queue.async {
            guard self.session.inputs.isEmpty else { if !self.session.isRunning { self.session.startRunning() }; return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device), self.session.canAddInput(input) else {
                DispatchQueue.main.async { self.error = "カメラを開始できません" }; return
            }
            self.session.addInput(input)
            let output = AVCaptureVideoDataOutput()
            output.alwaysDiscardsLateVideoFrames = true
            output.setSampleBufferDelegate(self, queue: self.queue)
            if self.session.canAddOutput(output) { self.session.addOutput(output) }
            output.connection(with: .video)?.videoRotationAngle = 90
            self.session.commitConfiguration()
            self.session.startRunning()
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        latest = CIImage(cvPixelBuffer: buffer)
    }

    func capture() {
        queue.async {
            guard let image = self.latest, let cg = self.context.createCGImage(image, from: image.extent), let data = UIImage(cgImage: cg).jpegData(compressionQuality: 0.94) else { return }
            let dir = URL.documentsDirectory.appending(path: "Camera", directoryHint: .isDirectory)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let url = dir.appending(path: "IMG_\(Int(Date.now.timeIntervalSince1970)).jpg")
            try? data.write(to: url, options: .atomic)
            DispatchQueue.main.async { self.refreshGallery() }
        }
    }

    func refreshGallery() {
        let dir = URL.documentsDirectory.appending(path: "Camera", directoryHint: .isDirectory)
        images = ((try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.creationDateKey])) ?? []).sorted { $0.lastPathComponent > $1.lastPathComponent }
    }

    func delete(_ url: URL) { try? FileManager.default.removeItem(at: url); refreshGallery() }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewView { let v = PreviewView(); v.layerView.session = session; v.layerView.videoGravity = .resizeAspectFill; return v }
    func updateUIView(_ uiView: PreviewView, context: Context) {}
}

final class PreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var layerView: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}

struct SilentCameraView: View {
    @StateObject private var camera = CameraController()
    @State private var gallery = false
    var body: some View {
        ZStack(alignment: .bottom) {
            CameraPreview(session: camera.session).ignoresSafeArea(edges: .bottom)
            HStack(spacing: 28) {
                Button { gallery = true } label: { Image(systemName: "photo.on.rectangle").font(.title2).frame(width: 54, height: 54).background(.ultraThinMaterial, in: Circle()) }
                Button { camera.capture() } label: { Circle().strokeBorder(.white, lineWidth: 5).background(Circle().fill(.white.opacity(0.35))).frame(width: 76, height: 76) }
                Color.clear.frame(width: 54, height: 54)
            }.foregroundStyle(.white).padding(.bottom, 28)
        }
        .onAppear { camera.start() }.onDisappear { camera.stop() }
        .sheet(isPresented: $gallery) { CameraGalleryView(camera: camera) }
        .alert("カメラ", isPresented: .constant(camera.error != nil)) { Button("閉じる") { camera.error = nil } } message: { Text(camera.error ?? "") }
    }
}

struct CameraGalleryView: View {
    @ObservedObject var camera: CameraController
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 3)], spacing: 3) {
                    ForEach(camera.images, id: \.self) { url in
                        if let image = UIImage(contentsOfFile: url.path) {
                            Image(uiImage: image).resizable().scaledToFill().frame(height: 125).clipped()
                                .contextMenu { Button("削除", role: .destructive) { camera.delete(url) } }
                        }
                    }
                }
            }.navigationTitle("ギャラリー").toolbar { Button("閉じる") { dismiss() } }
        }
    }
}

