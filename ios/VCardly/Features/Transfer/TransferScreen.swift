import Contacts
import ContactsUI
import SwiftUI
import UniformTypeIdentifiers

enum ImportFailure: Error, Equatable { case unreadable, tooLarge, noContacts }

enum ImportState: Equatable {
    case idle, reading
    case preview([ImportCandidate], selected: Set<Int>)
    case done(imported: Int, duplicates: Int, unusable: Int)
    case failed(ImportFailure)
}

enum ExportState: Equatable {
    case idle, working
    case done(Int)
    case failed
}

struct TransferState: Equatable {
    var importState: ImportState = .idle
    var exportState: ExportState = .idle
}

struct TransferActions {
    var chooseFile: () -> Void = {}
    var chooseFromContacts: () -> Void = {}
    var toggle: (Int) -> Void = { _ in }
    var importSelected: () -> Void = {}
    var resetImport: () -> Void = {}
    var exportAll: () -> Void = {}
}

/// A .vcf file handed to the system "Save to Files" sheet.
struct VCardDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.vCard] }
    let text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws { throw CocoaError(.featureUnsupported) }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: Data(text.utf8)) }
}

/// vCard files in and out, plus import from the iPhone's Contacts app. Nothing is added until the user reviews the list.
struct TransferScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var state = TransferState()
    @State private var importing = false
    @State private var exportDoc: VCardDocument?
    @State private var exportCount = 0
    @State private var picker = ContactsPickerPresenter()
    static let maxBytes = 5 * 1024 * 1024

    var body: some View {
        TransferContent(state: state, actions: TransferActions(
            chooseFile: { importing = true },
            chooseFromContacts: { picker.present { cards in preview(cards) } },
            toggle: toggle,
            importSelected: importSelected,
            resetImport: { state.importState = .idle },
            exportAll: exportAll
        ))
        .navigationTitle(L10n.s("transfer.title"))
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.vCard, .plainText, .data]) { result in
            if case .success(let url) = result { readFile(url) }
        }
        .fileExporter(isPresented: Binding(get: { exportDoc != nil }, set: { if !$0 { exportDoc = nil } }), document: exportDoc,
                      contentType: .vCard, defaultFilename: L10n.s("transfer.export_file_name")) { result in
            if case .success = result { state.exportState = .done(exportCount) } else { state.exportState = .idle }
            exportDoc = nil
        }
    }

    private func readFile(_ url: URL) {
        state.importState = .reading
        Task {
            let result = await Task.detached { () -> Result<String, ImportFailure> in
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                // Reads at most maxBytes + 1 so an oversized file is refused, not loaded.
                guard let h = try? FileHandle(forReadingFrom: url) else { return .failure(.unreadable) }
                defer { try? h.close() }
                guard let data = try? h.read(upToCount: TransferScreen.maxBytes + 1) else { return .failure(.unreadable) }
                guard data.count <= TransferScreen.maxBytes else { return .failure(.tooLarge) }
                return .success(String(decoding: data, as: UTF8.self))
            }.value
            switch result {
            case .failure(let f): state.importState = .failed(f)
            case .success(let text): preview(VCardParser.parse(text))
            }
        }
    }

    private func preview(_ parsed: [ParsedVCard]) {
        guard !parsed.isEmpty else { state.importState = .failed(.noContacts); return }
        let entries = VCardImporter.prepare(parsed, existing: env.contacts.contacts().map(\.contact))
        state.importState = .preview(entries, selected: Set(entries.filter { $0.status == .new }.map(\.index)))
    }

    private func toggle(_ index: Int) {
        guard case .preview(let entries, var selected) = state.importState else { return }
        if selected.contains(index) { selected.remove(index) } else { selected.insert(index) }
        state.importState = .preview(entries, selected: selected)
    }

    private func importSelected() {
        guard case .preview(let entries, let selected) = state.importState else { return }
        var imported = 0
        for e in entries where selected.contains(e.index) && e.status == .new {
            guard let contact = e.contact else { continue }
            let tagIds = Set(e.tags.compactMap { env.contacts.findOrCreateTag($0)?.id })
            if (try? env.contacts.save(contact, tagIds: tagIds)) != nil { imported += 1 }
        }
        state.importState = .done(imported: imported, duplicates: entries.filter { $0.status == .duplicate }.count,
                                  unusable: entries.filter { $0.status == .unusable }.count)
    }

    private func exportAll() {
        let all = env.contacts.contacts()
        let now = Date()
        exportCount = all.count
        exportDoc = VCardDocument(text: all.map { VCardWriter.write($0, revision: now) }.joined())
    }
}

/// Stateless import/export screen (used directly by screenshot tests).
struct TransferContent: View {
    let state: TransferState
    let actions: TransferActions
    @State private var confirmExport = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                importCard
                exportCard
            }
            .padding(VC.screen)
        }
        .background(VC.background)
        .confirmationDialog(L10n.s("transfer.export_confirm.title"), isPresented: $confirmExport, titleVisibility: .visible) {
            Button(L10n.s("transfer.export_all"), action: actions.exportAll)
        } message: {
            Text(L10n.s("transfer.export_confirm.message"))
        }
    }

    private func header(_ symbol: String, _ tone: Tone, _ title: String, _ body: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VCIconBadge(symbol: symbol, tone: tone, size: 40, solid: true)
            VStack(alignment: .leading, spacing: 2) {
                Text(L10n.s(title)).font(VCFont.titleMedium).accessibilityAddTraits(.isHeader)
                Text(L10n.s(body)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
            }
        }
    }

    @ViewBuilder
    private var importCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            header("square.and.arrow.down.fill", VC.blue, "transfer.import", "transfer.import_description")
            switch state.importState {
            case .idle:
                importButtons
            case .reading:
                HStack(spacing: 12) { ProgressView(); Text(L10n.s("transfer.reading")).font(VCFont.bodyMedium) }
                    .frame(maxWidth: .infinity, minHeight: 48)
            case .preview(let entries, let selected):
                Text(L10n.s("transfer.preview_hint")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                VStack(spacing: 0) {
                    ForEach(entries) { e in row(e, selected: selected.contains(e.index)) }
                }
                .background(VC.cardHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                HStack(spacing: 10) {
                    VCSecondaryButton(title: L10n.s("common.cancel"), action: actions.resetImport)
                    VCPrimaryButton(title: L10n.s("transfer.import_selected", selected.count), icon: "square.and.arrow.down", action: actions.importSelected)
                        .disabled(selected.isEmpty)
                }
            case .done(let imported, let duplicates, let unusable):
                VCNotice(text: L10n.plural("transfer.imported", imported), tone: VC.mint, symbol: "checkmark.circle.fill")
                if duplicates > 0 { Text(L10n.plural("transfer.skipped_duplicates", duplicates)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant) }
                if unusable > 0 { Text(L10n.plural("transfer.skipped_unusable", unusable)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant) }
                importButtons
            case .failed(let f):
                VCNotice(text: L10n.s(f.messageKey), tone: VC.rose, symbol: "xmark.octagon.fill")
                importButtons
            }
        }
        .vcCard(padding: 20)
    }

    private var importButtons: some View {
        VStack(spacing: 10) {
            VCTonalButton(title: L10n.s("transfer.choose_file"), icon: "doc.badge.plus", action: actions.chooseFile)
            VCSecondaryButton(title: L10n.s("transfer.from_contacts"), icon: "person.crop.circle.badge.plus", action: actions.chooseFromContacts)
        }
    }

    private func row(_ e: ImportCandidate, selected: Bool) -> some View {
        let c = e.contact
        let selectable = e.status == .new
        return Button { if selectable { actions.toggle(e.index) } } label: {
            HStack(spacing: 12) {
                Image(systemName: selectable ? (selected ? "checkmark.circle.fill" : "circle") : "minus.circle")
                    .font(.system(size: 20)).foregroundStyle(selectable && selected ? VC.primary : VC.outline)
                VStack(alignment: .leading, spacing: 1) {
                    Text(c?.fullName ?? L10n.s("transfer.unnamed")).font(VCFont.titleSmall).foregroundStyle(selectable ? VC.onSurface : VC.onSurfaceVariant)
                    let detail = [c?.company ?? "", c?.email ?? "", c?.phone ?? ""].first { !$0.isEmpty } ?? ""
                    if e.status == .duplicate {
                        Text(L10n.s("transfer.status.duplicate")).font(VCFont.bodySmall).foregroundStyle(VC.orange.content)
                    } else if e.status == .unusable {
                        Text(L10n.s("transfer.status.unusable")).font(VCFont.bodySmall).foregroundStyle(VC.rose.content)
                    } else if !detail.isEmpty {
                        Text(detail).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).vcLineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!selectable)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private var exportCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            header("square.and.arrow.up.fill", VC.lavender, "transfer.export", "transfer.export_description")
            switch state.exportState {
            case .done(let n): VCNotice(text: L10n.plural("transfer.exported", n), tone: VC.mint, symbol: "checkmark.circle.fill")
            case .failed: VCNotice(text: L10n.s("transfer.export_failed"), tone: VC.rose, symbol: "xmark.octagon.fill")
            default: EmptyView()
            }
            VCNotice(text: L10n.s("transfer.export_privacy"), tone: VC.orange, symbol: "lock.shield.fill")
            VCTonalButton(title: L10n.s("transfer.export_all"), icon: "square.and.arrow.up") { confirmExport = true }
        }
        .vcCard(padding: 20)
    }
}

extension ImportFailure {
    var messageKey: String {
        switch self {
        case .unreadable: "transfer.error.unreadable"
        case .tooLarge: "transfer.error.too_large"
        case .noContacts: "transfer.error.no_contacts"
        }
    }
}

// MARK: - iPhone Contacts

/// Shows the system contact picker. It needs no Contacts permission: the user picks people and only those are handed over,
/// read-only. Picked contacts go through the same review list as a vCard file.
@MainActor
final class ContactsPickerPresenter: NSObject, CNContactPickerDelegate {
    private var onPick: (([ParsedVCard]) -> Void)?

    func present(_ onPick: @escaping ([ParsedVCard]) -> Void) {
        self.onPick = onPick
        let picker = CNContactPickerViewController()
        picker.delegate = self
        guard let root = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController else { return }
        var top = root
        while let next = top.presentedViewController { top = next }
        top.present(picker, animated: true)
    }

    nonisolated func contactPicker(_ picker: CNContactPickerViewController, didSelect contacts: [CNContact]) {
        let parsed = contacts.map(Self.parsed)
        Task { @MainActor in self.onPick?(parsed); self.onPick = nil }
    }

    nonisolated func contactPickerDidCancel(_ picker: CNContactPickerViewController) {
        Task { @MainActor in self.onPick = nil }
    }

    /// Reads only the fields VCardly stores, and only those the picker actually provided.
    nonisolated static func parsed(_ c: CNContact) -> ParsedVCard {
        func has(_ key: String) -> Bool { c.isKeyAvailable(key) }
        var p = ParsedVCard()
        let given = has(CNContactGivenNameKey) ? c.givenName : ""
        let middle = has(CNContactMiddleNameKey) ? c.middleName : ""
        let family = has(CNContactFamilyNameKey) ? c.familyName : ""
        p.fullName = [given, middle, family].filter { !$0.isEmpty }.joined(separator: " ")
        if has(CNContactJobTitleKey) { p.jobTitle = c.jobTitle }
        if has(CNContactOrganizationNameKey) { p.company = c.organizationName }
        if has(CNContactPhoneNumbersKey) {
            let numbers = c.phoneNumbers
                .filter { !($0.label ?? "").localizedCaseInsensitiveContains("fax") }
                .sorted { ($0.label == CNLabelPhoneNumberMobile || $0.label == CNLabelPhoneNumberiPhone ? 0 : 1) < ($1.label == CNLabelPhoneNumberMobile || $1.label == CNLabelPhoneNumberiPhone ? 0 : 1) }
            p.phones = numbers.map { $0.value.stringValue }.filter { !$0.trimmed.isEmpty }
        }
        if has(CNContactEmailAddressesKey) { p.emails = c.emailAddresses.map { $0.value as String }.filter { !$0.trimmed.isEmpty } }
        if has(CNContactUrlAddressesKey) { p.website = c.urlAddresses.first.map { $0.value as String } ?? "" }
        if has(CNContactPostalAddressesKey), let a = c.postalAddresses.first?.value {
            p.address = [a.street, a.city, a.state, a.postalCode, a.country].map(\.trimmed).filter { !$0.isEmpty }.joined(separator: ", ")
        }
        // Notes are not read (Apple restricts them, and they are often private).
        return p
    }
}
