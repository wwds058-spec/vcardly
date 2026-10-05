import CoreImage.CIFilterBuiltins
import SwiftUI
import UniformTypeIdentifiers

enum QRCode {
    /// A crisp QR image for the text (error correction M), or nil if it is too long to encode.
    static func image(_ text: String, scale: CGFloat = 12) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: scale, y: scale)),
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

extension ShareField {
    var labelKey: String {
        switch self {
        case .name: "field.name"
        case .jobTitle: "field.job_title"
        case .company: "field.company"
        case .phone: "field.phone"
        case .phoneAlt: "field.phone_alt"
        case .email: "field.email"
        case .emailAlt: "field.email_alt"
        case .website: "field.website"
        case .address: "field.address"
        }
    }
}

/// A .vcf file handed to the share sheet. Written to the temporary directory only when the user shares.
struct VCardFile: Transferable {
    let name: String
    let text: String

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .vCard) { file in
            let safe = file.name.trimmed.isEmpty ? "contact" : file.name.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }.joined(separator: "_")
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(safe).vcf")
            try Data(file.text.utf8).write(to: url, options: .atomic)
            return SentTransferredFile(url)
        }
    }
}

/// QR code of a vCard with the chosen fields, plus Share contact file / Share QR image. Notes are never included.
struct QRSharePanel: View {
    let card: ShareCard
    @State var selected: Set<ShareField>

    init(card: ShareCard, selected: Set<ShareField>? = nil) {
        self.card = card
        _selected = State(initialValue: selected ?? card.defaultSelection)
    }

    var body: some View {
        let text = card.vCard(selected)
        let qr = QRCode.image(text)
        let name = card.values[.name] ?? ""
        VStack(spacing: 16) {
            ZStack {
                if let qr {
                    Image(uiImage: qr).interpolation(.none).resizable().scaledToFit()
                        .padding(14).background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .overlay { VCMark(size: 34).padding(6).background(Color.white, in: RoundedRectangle(cornerRadius: 10)) }
                        .accessibilityLabel(L10n.s("share.qr_label", name))
                } else {
                    VCNotice(text: L10n.s("share.qr_too_long"), tone: VC.orange, symbol: "exclamationmark.triangle.fill")
                }
            }
            .frame(maxWidth: 240)
            Text(L10n.s("share.scan_hint")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).multilineTextAlignment(.center)

            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.s("share.fields")).font(VCFont.titleSmall)
                FlowLayout(spacing: 8) {
                    ForEach(card.available, id: \.self) { f in
                        VCChip(title: L10n.s(f.labelKey), selected: selected.contains(f) || f == .name, symbol: selected.contains(f) || f == .name ? "checkmark" : nil) {
                            guard f != .name else { return }
                            if selected.contains(f) { selected.remove(f) } else { selected.insert(f) }
                        }
                        .disabled(f == .name)
                    }
                }
                Text(L10n.s("share.notes_never")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 12) {
                ShareLink(item: VCardFile(name: name, text: text), preview: SharePreview(name.isEmpty ? L10n.s("share.contact_file") : name)) {
                    Label(L10n.s("share.contact_file"), systemImage: "square.and.arrow.up").font(VCFont.titleSmall)
                        .frame(maxWidth: .infinity, minHeight: 52).foregroundStyle(.white).background(VC.cta, in: Capsule())
                }
                if let qr {
                    let image = Image(uiImage: qr)
                    ShareLink(item: image, preview: SharePreview(L10n.s("share.qr_image"), image: image)) {
                        Label(L10n.s("share.qr_image"), systemImage: "qrcode").font(VCFont.titleSmall)
                            .frame(maxWidth: .infinity, minHeight: 52).foregroundStyle(VC.onPrimaryContainer).background(VC.primaryContainer, in: Capsule())
                    }
                }
            }
        }
        .vcCard(padding: 20)
    }
}

/// Share a saved contact as QR or .vcf.
struct ContactShareScreen: View {
    @Environment(AppEnvironment.self) private var env
    let contactId: UUID
    @State private var details: ContactDetails?

    var body: some View {
        ScrollView {
            if let details {
                VStack(spacing: 16) {
                    HStack(spacing: 12) {
                        VCAvatar(name: details.contact.fullName, argb: details.category?.colorARGB, size: 48)
                        VStack(alignment: .leading) {
                            Text(details.contact.fullName).font(VCFont.titleMedium)
                            if !details.contact.company.isEmpty { Text(details.contact.company).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant) }
                        }
                        Spacer()
                    }
                    QRSharePanel(card: .of(details.contact))
                }
                .padding(VC.screen)
            }
        }
        .background(VC.background)
        .navigationTitle(L10n.s("share.title"))
        .navigationBarTitleDisplayMode(.inline)
        .task { details = env.contacts.contact(contactId) }
    }
}
