import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// Declared in Info.plist (UTExportedTypeDeclarations) with the `.vcbackup` extension, the same as on Android.
    static let vcardlyBackup = UTType(exportedAs: "com.yasin.vcardly.backup", conformingTo: .data)
}

/// Hands an already written backup file to the system "Save to Files" sheet.
struct BackupFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.vcardlyBackup] }
    let url: URL

    init(url: URL) { self.url = url }
    init(configuration: ReadConfiguration) throws { throw CocoaError(.featureUnsupported) }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { try FileWrapper(url: url, options: []) }
}

enum BackupCreateState: Equatable {
    case idle, working
    case saved(BackupCounts)
    case failed(BackupError)
}

enum BackupRestoreState {
    case idle, validating
    case needsPassword(URL, wrongAttempt: Bool)
    case ready(PreparedBackup)
    case restoring
    case done(RestoreSummary)
    case failed(BackupError)
}

struct BackupState {
    var lastBackupAt: Date?
    var create: BackupCreateState = .idle
    var restore: BackupRestoreState = .idle
}

struct BackupActions {
    var create: (String?) -> Void = { _ in }
    var chooseFile: () -> Void = {}
    var submitPassword: (URL, String) -> Void = { _, _ in }
    var restore: (RestoreMode) -> Void = { _ in }
    var reset: () -> Void = {}
}

struct BackupScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var state = BackupState()
    @State private var created: BackupService.CreatedBackup?
    @State private var exporting = false
    @State private var importing = false

    var body: some View {
        BackupContent(state: state, actions: BackupActions(
            create: create,
            chooseFile: { discardPrepared(); importing = true },
            submitPassword: { url, pw in prepare(url, password: pw) },
            restore: restore,
            reset: { discardPrepared(); state.restore = .idle }
        ))
        .navigationTitle(L10n.s("backup.title"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { state.lastBackupAt = env.prefs.lastBackupAt }
        .onDisappear(perform: discardPrepared)
        .fileExporter(isPresented: $exporting, document: created.map { BackupFileDocument(url: $0.file) }, contentType: .vcardlyBackup,
                      defaultFilename: created?.file.lastPathComponent) { result in
            if let c = created {
                if case .success = result {
                    state.create = .saved(c.counts)
                    state.lastBackupAt = env.prefs.lastBackupAt
                } else {
                    state.create = .idle
                }
                BackupService.cleanUp(c.workDir)
            }
            created = nil
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.vcardlyBackup, .zip, .data]) { result in
            if case .success(let url) = result { prepare(url, password: nil) }
        }
    }

    private func create(_ password: String?) {
        state.create = .working
        Task {
            do {
                created = try await env.backup.create(password: password)
                exporting = true
            } catch {
                state.create = .failed((error as? BackupError) ?? .io)
            }
        }
    }

    private func prepare(_ url: URL, password: String?) {
        state.restore = .validating
        Task {
            do {
                state.restore = .ready(try await env.backup.prepare(url, password: password))
            } catch BackupError.needsPassword {
                state.restore = .needsPassword(url, wrongAttempt: false)
            } catch BackupError.wrongPasswordOrCorrupt {
                state.restore = .needsPassword(url, wrongAttempt: true)
            } catch {
                state.restore = .failed((error as? BackupError) ?? .io)
            }
        }
    }

    private func restore(_ mode: RestoreMode) {
        guard case .ready(let prepared) = state.restore else { return }
        state.restore = .restoring
        do {
            state.restore = .done(try env.backup.restore(prepared, mode: mode))
        } catch {
            state.restore = .failed((error as? BackupError) ?? .corrupt)
        }
    }

    private func discardPrepared() {
        if case .ready(let p) = state.restore { env.backup.discard(p) }
    }
}

/// Stateless backup screen (used directly by screenshot tests).
struct BackupContent: View {
    let state: BackupState
    let actions: BackupActions
    @State private var protect = true
    @State private var password = ""
    @State private var confirm = ""
    @State private var restorePassword = ""
    @State private var mode: RestoreMode = .merge
    @State private var confirmReplace = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                status
                createCard
                restoreCard
                VCNotice(text: L10n.s("backup.where_hint"), tone: VC.blue, symbol: "icloud.and.arrow.up")
            }
            .padding(VC.screen)
        }
        .background(VC.background)
        .scrollDismissesKeyboard(.interactively)
        .confirmationDialog(L10n.s("backup.replace_confirm.title"), isPresented: $confirmReplace, titleVisibility: .visible) {
            Button(L10n.s("backup.replace_confirm.action"), role: .destructive) { actions.restore(.replace) }
        } message: {
            Text(L10n.s("backup.replace_confirm.message"))
        }
    }

    // MARK: status

    private var status: some View {
        let done = state.lastBackupAt != nil
        let tone = done ? VC.mint : VC.orange
        return HStack(spacing: 14) {
            VCIconBadge(symbol: done ? "checkmark.shield.fill" : "exclamationmark.shield.fill", tone: tone, size: 52, circle: true)
            VStack(alignment: .leading, spacing: 2) {
                Text(L10n.s(done ? "backup.status.protected" : "backup.status.none")).font(VCFont.titleMedium).foregroundStyle(VC.onSurface)
                Text(state.lastBackupAt.map { L10n.s("backup.last", $0.formatted(date: .abbreviated, time: .shortened)) } ?? L10n.s("backup.never"))
                    .font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
            }
            Spacer(minLength: 0)
        }
        .vcCard()
        .accessibilityElement(children: .combine)
    }

    // MARK: create

    private var passwordOK: Bool { !protect || (password.count >= BackupFormat.minPasswordLength && password == confirm) }

    private var createCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            header("square.and.arrow.down.on.square.fill", VC.blue, "backup.create", "backup.create_description")
            Toggle(isOn: $protect.animation()) {
                Text(L10n.s("backup.protect")).font(VCFont.titleSmall)
            }
            .tint(VC.primary)
            if protect {
                secure("backup.password", $password,
                       error: !password.isEmpty && password.count < BackupFormat.minPasswordLength ? L10n.s("backup.password_short", BackupFormat.minPasswordLength) : nil)
                secure("backup.password_confirm", $confirm, error: !confirm.isEmpty && confirm != password ? L10n.s("backup.password_mismatch") : nil)
                VCNotice(text: L10n.s("backup.password_warning"), tone: VC.orange, symbol: "key.fill")
            } else {
                VCNotice(text: L10n.s("backup.unprotected_warning"), tone: VC.rose, symbol: "exclamationmark.triangle.fill")
            }
            switch state.create {
            case .saved(let counts):
                VCNotice(text: L10n.plural("backup.created", counts.contacts), tone: VC.mint, symbol: "checkmark.circle.fill")
            case .failed(let e):
                VCNotice(text: L10n.s(e.messageKey), tone: VC.rose, symbol: "xmark.octagon.fill")
            default:
                EmptyView()
            }
            VCPrimaryButton(title: L10n.s(state.create == .working ? "backup.creating" : "backup.create_button"), icon: "lock.doc.fill",
                            loading: state.create == .working) {
                actions.create(protect ? password : nil)
            }
            .disabled(!passwordOK)
        }
        .vcCard(padding: 20)
    }

    // MARK: restore

    @ViewBuilder
    private var restoreCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            header("arrow.counterclockwise.circle.fill", VC.lavender, "backup.restore", "backup.restore_description")
            switch state.restore {
            case .idle:
                VCTonalButton(title: L10n.s("backup.choose_file"), icon: "folder", action: actions.chooseFile)
            case .validating, .restoring:
                HStack(spacing: 12) {
                    ProgressView()
                    Text(L10n.s(isRestoring ? "backup.restoring" : "backup.validating")).font(VCFont.bodyMedium)
                }
                .frame(maxWidth: .infinity, minHeight: 48)
            case .needsPassword(let url, let wrong):
                Text(L10n.s("backup.password_prompt")).font(VCFont.titleSmall)
                secure("backup.password", $restorePassword, error: wrong ? L10n.s("backup.error.wrong_password") : nil)
                HStack(spacing: 10) {
                    VCSecondaryButton(title: L10n.s("common.cancel")) { restorePassword = ""; actions.reset() }
                    VCPrimaryButton(title: L10n.s("backup.unlock_file"), icon: "lock.open.fill") {
                        actions.submitPassword(url, restorePassword)
                        restorePassword = ""
                    }
                    .disabled(restorePassword.isEmpty)
                }
            case .ready(let p):
                summary(p.manifest)
                modeOption(.merge, "plus.circle.fill", "backup.mode.merge", "backup.mode.merge_hint")
                modeOption(.replace, "arrow.triangle.2.circlepath", "backup.mode.replace", "backup.mode.replace_hint")
                HStack(spacing: 10) {
                    VCSecondaryButton(title: L10n.s("common.cancel"), action: actions.reset)
                    VCPrimaryButton(title: L10n.s("backup.restore_button"), icon: "arrow.counterclockwise") {
                        if mode == .replace { confirmReplace = true } else { actions.restore(.merge) }
                    }
                }
            case .done(let s):
                VCNotice(text: L10n.plural("backup.restored", s.contactsAdded), tone: VC.mint, symbol: "checkmark.circle.fill")
                if s.duplicatesSkipped > 0 {
                    Text(L10n.plural("backup.skipped_duplicates", s.duplicatesSkipped)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                }
                VCSecondaryButton(title: L10n.s("backup.restore_another"), action: actions.chooseFile)
            case .failed(let e):
                VCNotice(text: L10n.s(e.messageKey), tone: VC.rose, symbol: "xmark.octagon.fill")
                VCTonalButton(title: L10n.s("backup.choose_file"), icon: "folder", action: actions.chooseFile)
            }
        }
        .vcCard(padding: 20)
    }

    private var isRestoring: Bool { if case .restoring = state.restore { true } else { false } }

    private func summary(_ m: BackupManifest) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: m.encrypted ? "lock.fill" : "doc.fill").foregroundStyle(VC.primary)
                Text(m.createdDate.map { L10n.s("backup.file_from", $0.formatted(date: .abbreviated, time: .shortened)) } ?? L10n.s("backup.file_undated"))
                    .font(VCFont.titleSmall)
            }
            Text(L10n.s("backup.contents", m.counts.contacts, m.counts.followUps, m.counts.images))
                .font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(VC.cardHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func modeOption(_ m: RestoreMode, _ symbol: String, _ title: String, _ hint: String) -> some View {
        let on = mode == m
        return Button { mode = m } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: on ? "largecircle.fill.circle" : "circle").foregroundStyle(on ? VC.primary : VC.outline).font(.system(size: 20))
                VStack(alignment: .leading, spacing: 2) {
                    Label(L10n.s(title), systemImage: symbol).font(VCFont.titleSmall).foregroundStyle(m == .replace ? VC.error : VC.onSurface)
                    Text(L10n.s(hint)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .background(on ? VC.primaryContainer.opacity(0.45) : .clear, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(on ? VC.primary : VC.outlineVariant, lineWidth: on ? 1.5 : 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? [.isSelected] : [])
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

    private func secure(_ key: String, _ text: Binding<String>, error: String?) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.s(key)).font(VCFont.labelLarge)
            SecureField("", text: text)
                .textContentType(.newPassword)
                .font(VCFont.bodyLarge)
                .padding(.horizontal, 16).frame(minHeight: 50)
                .background(VC.field, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(error != nil ? VC.error : VC.outlineVariant, lineWidth: error != nil ? 1.5 : 1))
                .accessibilityLabel(L10n.s(key))
            if let error { Text(error).font(VCFont.bodySmall).foregroundStyle(VC.error) }
        }
    }
}
