import PhotosUI
import SwiftUI
import Vision
import VisionKit

/// On-device text recognition with Apple Vision. Nothing leaves the device; recognised text is not stored or logged.
enum CardTextRecognizer {
    static func lines(in image: UIImage) async -> [OcrLine] {
        guard let cg = image.cgImage else { return [] }
        return await withCheckedContinuation { cont in
            let request = VNRecognizeTextRequest { req, _ in
                let height = CGFloat(cg.height)
                let result = (req.results as? [VNRecognizedTextObservation] ?? []).compactMap { o -> OcrLine? in
                    guard let text = o.topCandidates(1).first?.string, !text.trimmed.isEmpty else { return nil }
                    // Vision boxes are normalised with the origin at the bottom left.
                    return OcrLine(text: text, top: Int((1 - o.boundingBox.maxY) * height), height: Int(o.boundingBox.height * height))
                }
                cont.resume(returning: result)
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = false
            let handler = VNImageRequestHandler(cgImage: cg, orientation: CGImagePropertyOrientation(image.imageOrientation))
            DispatchQueue.global(qos: .userInitiated).async {
                do { try handler.perform([request]) } catch { cont.resume(returning: []) }
            }
        }
    }

    static func draft(front: UIImage?, back: UIImage?) async -> ContactDraft {
        var lines: [OcrLine] = []
        if let front { lines += await Self.lines(in: front) }
        if let back {
            // Back-side lines go after the front's so the parser prefers the front for the name.
            let offset = (lines.map { $0.top + $0.height }.max() ?? 0) + 1000
            lines += await Self.lines(in: back).map { OcrLine(text: $0.text, top: $0.top + offset, height: $0.height) }
        }
        let p = BusinessCardParser.parse(lines)
        let contact = Contact(fullName: p.fullName, jobTitle: p.jobTitle, company: p.company, phone: p.phone, phoneAlt: p.phoneAlt,
                              email: p.email, emailAlt: p.emailAlt, website: p.website, address: p.address, source: .scan)
        return ContactDraft(contact: contact, front: front, back: back, foundCount: p.foundCount, unmatched: p.unmatched)
    }
}

extension CGImagePropertyOrientation {
    init(_ o: UIImage.Orientation) {
        switch o {
        case .up: self = .up
        case .down: self = .down
        case .left: self = .left
        case .right: self = .right
        case .upMirrored: self = .upMirrored
        case .downMirrored: self = .downMirrored
        case .leftMirrored: self = .leftMirrored
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}

/// Scan a card: the system document camera (edge detection, perspective crop; the first page is the front, a second
/// page the back) or a photo from the library, then on-device OCR, then review in the contact form. Nothing is saved
/// until the user taps Save.
struct ScanFlowView: View {
    let onFinish: (UUID?) -> Void
    @State private var phase: ScanPhase = .start
    @State private var showCamera = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var draft: ContactDraft?
    @State private var importFailed = false

    var body: some View {
        NavigationStack {
            Group {
                if let draft {
                    ContactEditScreen(contactId: nil, draft: draft, onRescan: { self.draft = nil; phase = .start }) { savedId, _ in
                        onFinish(savedId)
                    }
                } else {
                    ScanStartContent(phase: phase, cameraAvailable: VNDocumentCameraViewController.isSupported, importFailed: importFailed,
                                     scan: { showCamera = true }, pickerItem: $pickerItem, manual: {
                                         draft = ContactDraft(contact: Contact(fullName: "", source: .manual))
                                     }, close: { onFinish(nil) })
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            DocumentCamera { images in
                showCamera = false
                if let first = images.first { process(front: first, back: images.dropFirst().first) }
            }
            .ignoresSafeArea()
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    importFailed = false
                    process(front: image, back: nil)
                } else {
                    importFailed = true
                }
                pickerItem = nil
            }
        }
    }

    private func process(front: UIImage, back: UIImage?) {
        phase = .reading
        Task {
            let d = await CardTextRecognizer.draft(front: front, back: back)
            withAnimation { draft = d }
            phase = .start
        }
    }
}

enum ScanPhase { case start, reading }

/// Stateless scan start screen (used directly by screenshot tests).
struct ScanStartContent: View {
    let phase: ScanPhase
    let cameraAvailable: Bool
    var importFailed = false
    let scan: () -> Void
    @Binding var pickerItem: PhotosPickerItem?
    let manual: () -> Void
    let close: () -> Void

    var body: some View {
        ZStack {
            LinearGradient(colors: [VC.navyDeep, Color(argb: 0xFF1B2D6B)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Button(action: close) {
                        Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                            .frame(width: 44, height: 44).background(.white.opacity(0.14), in: Circle())
                    }
                    .accessibilityLabel(L10n.s("common.close"))
                    Spacer()
                    Text(L10n.s("scan.action")).font(VCFont.titleMedium).foregroundStyle(.white)
                    Spacer()
                    Color.clear.frame(width: 44, height: 44)
                }
                .padding(.horizontal, VC.screen).padding(.top, 8)
                Spacer()
                frame
                Text(L10n.s(phase == .reading ? "scan.reading" : "scan.instruction")).font(VCFont.titleSmall).foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 10).background(.white.opacity(0.14), in: Capsule()).padding(.top, 28)
                if importFailed {
                    Text(L10n.s("scan.error_import")).font(VCFont.bodySmall).foregroundStyle(Color(argb: 0xFFFF8FA5)).padding(.top, 10)
                }
                if !cameraAvailable {
                    Text(L10n.s("scan.camera_unavailable")).font(VCFont.bodySmall).foregroundStyle(.white.opacity(0.75))
                        .multilineTextAlignment(.center).padding(.horizontal, 32).padding(.top, 10)
                }
                Spacer()
                VStack(spacing: 12) {
                    if cameraAvailable {
                        VCPrimaryButton(title: L10n.s("scan.take_photo"), icon: "camera.fill", color: Color(argb: 0xFF2F5FEA), action: scan)
                    }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label(L10n.s("scan.pick_gallery"), systemImage: "photo.on.rectangle").font(VCFont.titleSmall)
                            .frame(maxWidth: .infinity, minHeight: 52).foregroundStyle(.white)
                            .overlay(Capsule().stroke(.white.opacity(0.5)))
                    }
                    Button(L10n.s("scan.enter_manually"), action: manual).font(VCFont.labelLarge).foregroundStyle(.white.opacity(0.9)).frame(minHeight: 44)
                }
                .disabled(phase == .reading)
                .padding(.horizontal, VC.screen).padding(.bottom, 16)
            }
        }
    }

    private var frame: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.06))
            CornerBrackets().stroke(Color.white, style: StrokeStyle(lineWidth: 4, lineCap: .round))
            if phase == .reading {
                ProgressView().tint(.white).scaleEffect(1.4)
            } else {
                Image(systemName: "person.text.rectangle").font(.system(size: 48)).foregroundStyle(.white.opacity(0.35))
            }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .padding(.horizontal, 32)
        .accessibilityHidden(true)
    }
}

/// Four rounded corner brackets marking the card area.
struct CornerBrackets: Shape {
    var length: CGFloat = 30
    func path(in r: CGRect) -> Path {
        var p = Path()
        let l = length
        p.move(to: CGPoint(x: r.minX, y: r.minY + l)); p.addLine(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.minX + l, y: r.minY))
        p.move(to: CGPoint(x: r.maxX - l, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY + l))
        p.move(to: CGPoint(x: r.maxX, y: r.maxY - l)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY)); p.addLine(to: CGPoint(x: r.maxX - l, y: r.maxY))
        p.move(to: CGPoint(x: r.minX + l, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY - l))
        return p
    }
}

/// VisionKit's document camera. Returns the scanned pages (empty when cancelled or failed).
struct DocumentCamera: UIViewControllerRepresentable {
    let onFinish: ([UIImage]) -> Void

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let c = VNDocumentCameraViewController()
        c.delegate = context.coordinator
        return c
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let onFinish: ([UIImage]) -> Void
        init(onFinish: @escaping ([UIImage]) -> Void) { self.onFinish = onFinish }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            onFinish((0..<min(scan.pageCount, 2)).map { scan.imageOfPage(at: $0) })
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) { onFinish([]) }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) { onFinish([]) }
    }
}
