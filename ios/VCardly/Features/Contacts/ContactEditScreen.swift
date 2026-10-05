import PhotosUI
import SwiftUI

/// What a scan hands to the edit form: recognised values (never saved until the user confirms) and the card photos.
struct ContactDraft {
    var contact: Contact
    var front: UIImage?
    var back: UIImage?
    var foundCount = 0
    var unmatched: [String] = []
}

enum EditStep: Int, CaseIterable {
    case basic, contact, business, details
    var titleKey: String {
        switch self {
        case .basic: "form.step.basic"
        case .contact: "form.step.contact"
        case .business: "form.step.business"
        case .details: "form.step.details"
        }
    }
    var fields: [ContactField] {
        switch self {
        case .basic: [.fullName, .jobTitle, .company]
        case .contact: [.phone, .phoneAlt, .email, .emailAlt, .website]
        case .business: [.address]
        case .details: [.notes]
        }
    }
}

struct ContactEditState {
    var contact = Contact(fullName: "")
    var tagIds: Set<UUID> = []
    var categories: [Category] = []
    var tags: [Tag] = []
    var front: UIImage?
    var back: UIImage?
    var isNew = true
    /// Scan review: every field on one page with a banner.
    var review = false
    var foundCount = 0
    var unmatched: [String] = []
    var step: EditStep = .basic
    var errors: [ContactField: ValidationReason] = [:]
    var saving = false
    var saveFailed = false
}

struct ContactEditScreen: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss
    let contactId: UUID?
    let draft: ContactDraft?
    var onRescan: (() -> Void)?
    let done: (UUID?, Bool) -> Void
    @State private var state = ContactEditState()
    @State private var original: Contact?
    @State private var loaded = false
    @State private var frontChanged = false
    @State private var backChanged = false
    @State private var confirmDiscard = false

    init(contactId: UUID?, draft: ContactDraft?, onRescan: (() -> Void)? = nil, done: @escaping (UUID?, Bool) -> Void) {
        self.contactId = contactId; self.draft = draft; self.onRescan = onRescan; self.done = done
    }

    var body: some View {
        ContactEditContent(state: $state, actions: ContactEditActions(
            save: save,
            addTag: { name in
                if let t = env.contacts.findOrCreateTag(name) {
                    state.tags = env.contacts.tags()
                    state.tagIds.insert(t.id)
                }
            },
            setImage: { side, image in
                if side == .front { state.front = image; frontChanged = true } else { state.back = image; backChanged = true }
            },
            rescan: onRescan
        ))
        .navigationTitle(L10n.s(state.review ? "scan.review_title" : (state.isNew ? "contact.add" : "contact.edit")))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(L10n.s("common.cancel")) { if isDirty { confirmDiscard = true } else { done(nil, false) } }
            }
        }
        .confirmationDialog(L10n.s("form.discard.title"), isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button(L10n.s("form.discard.confirm"), role: .destructive) { done(nil, false) }
        } message: {
            Text(L10n.s("form.discard.message"))
        }
        .alert(L10n.s("form.save_failed"), isPresented: $state.saveFailed) { Button(L10n.s("common.ok")) {} }
        .onAppear(perform: load)
    }

    private var isDirty: Bool {
        guard loaded else { return false }
        if state.review { return true }
        return state.contact != (original ?? Contact(fullName: "")) || frontChanged || backChanged
    }

    private func load() {
        guard !loaded else { return }
        state.categories = env.contacts.categories()
        state.tags = env.contacts.tags()
        if let contactId, let d = env.contacts.contact(contactId) {
            state.contact = d.contact
            state.tagIds = Set(d.tags.map(\.id))
            state.front = env.images.load(d.contact.frontImagePath)
            state.back = env.images.load(d.contact.backImagePath)
            state.isNew = false
        } else if let draft {
            state.contact = draft.contact
            state.front = draft.front
            state.back = draft.back
            state.review = draft.contact.source == .scan
            state.foundCount = draft.foundCount
            state.unmatched = draft.unmatched
            frontChanged = draft.front != nil
            backChanged = draft.back != nil
        }
        original = state.contact
        loaded = true
    }

    private func save() {
        var c = state.contact
        for f in ContactField.allCases { c[f] = c[f].trimmed }
        let errors = ContactValidator.validate(c)
        state.errors = errors
        guard errors.isEmpty else {
            if !state.review, let first = EditStep.allCases.first(where: { step in step.fields.contains { errors[$0] != nil } }) {
                withAnimation { state.step = first }
            }
            return
        }
        state.saving = true
        // New photos are written first; the old files are removed only after the record points at the new ones.
        var oldPaths: [String?] = []
        if frontChanged {
            oldPaths.append(c.frontImagePath)
            c.frontImagePath = state.front.flatMap(env.images.save)
        }
        if backChanged {
            oldPaths.append(c.backImagePath)
            c.backImagePath = state.back.flatMap(env.images.save)
        }
        do {
            let id = try env.contacts.save(c, tagIds: state.tagIds)
            oldPaths.forEach { env.images.delete($0) }
            done(id, state.isNew)
        } catch {
            state.saving = false
            state.saveFailed = true
        }
    }
}

enum CardSide { case front, back }

struct ContactEditActions {
    var save: () -> Void = {}
    var addTag: (String) -> Void = { _ in }
    var setImage: (CardSide, UIImage?) -> Void = { _, _ in }
    var rescan: (() -> Void)?
}

/// Stateless contact form (used directly by screenshot tests): a four-step stepper, or every section at once when reviewing a scan.
struct ContactEditContent: View {
    @Binding var state: ContactEditState
    let actions: ContactEditActions
    @State private var newTag = ""
    @State private var camera: CardSide?
    @State private var pickerSide: CardSide = .front
    @State private var pickerItem: PhotosPickerItem?
    @State private var showPicker = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if state.review {
                        reviewHeader
                        ForEach(EditStep.allCases, id: \.self) { step in
                            VCOverline(text: L10n.s(step.titleKey)).padding(.top, 6)
                            section(step)
                        }
                    } else {
                        VCStepper(steps: EditStep.allCases.map { L10n.s($0.titleKey) }, current: state.step.rawValue).padding(.bottom, 4)
                        section(state.step).id(state.step).transition(.opacity)
                    }
                }
                .padding(.horizontal, VC.screen).padding(.vertical, 16)
            }
            .scrollDismissesKeyboard(.interactively)
            bottomBar
        }
        .background(VC.background)
        .sheet(item: Binding(get: { camera.map(CameraRequest.init) }, set: { camera = $0?.side })) { req in
            CameraPicker { image in actions.setImage(req.side, image) }.ignoresSafeArea()
        }
        .photosPicker(isPresented: $showPicker, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            let side = pickerSide
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    actions.setImage(side, image)
                }
                pickerItem = nil
            }
        }
    }

    private var reviewHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let front = state.front {
                Image(uiImage: front).resizable().scaledToFit().frame(maxHeight: 180)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous)).frame(maxWidth: .infinity)
                    .accessibilityLabel(L10n.s("card.front"))
            }
            if state.foundCount > 0 {
                HStack(spacing: 12) {
                    VCIconBadge(symbol: "checkmark", tone: VC.mint, size: 40, circle: true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L10n.s("scan.success_title")).font(VCFont.titleSmall).foregroundStyle(VC.onSurface)
                        Text(L10n.plural("scan.success_found", state.foundCount)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                    }
                    Spacer()
                    if let rescan = actions.rescan {
                        Button(L10n.s("scan.rescan"), action: rescan).font(VCFont.labelLarge)
                    }
                }
                .vcCard(padding: 14)
            } else {
                VCNotice(text: L10n.s("scan.failed_message"), tone: VC.orange, symbol: "exclamationmark.triangle.fill")
            }
            VCNotice(text: L10n.s("scan.review_banner"), tone: VC.blue, symbol: "info.circle.fill")
            if !state.unmatched.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L10n.s("scan.unmatched_title")).font(VCFont.titleSmall)
                    Text(L10n.s("scan.unmatched_hint")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                    Text(state.unmatched.joined(separator: "\n")).font(VCFont.bodyMedium).textSelection(.enabled)
                    Button(L10n.s("scan.add_to_notes")) {
                        let extra = state.unmatched.joined(separator: "\n")
                        state.contact.notes = state.contact.notes.trimmed.isEmpty ? extra : state.contact.notes + "\n" + extra
                        state.unmatched = []
                    }
                    .font(VCFont.labelLarge)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .vcCard()
            }
        }
    }

    @ViewBuilder
    private func section(_ step: EditStep) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            switch step {
            case .basic:
                field(.fullName, "field.name", required: true, contentType: .name, capitalization: .words, symbol: "person")
                field(.jobTitle, "field.job_title", contentType: .jobTitle, capitalization: .words, symbol: "briefcase")
                field(.company, "field.company", contentType: .organizationName, capitalization: .words, symbol: "building.2")
            case .contact:
                field(.phone, "field.phone", keyboard: .phonePad, contentType: .telephoneNumber, symbol: "phone")
                field(.phoneAlt, "field.phone_alt", keyboard: .phonePad, contentType: .telephoneNumber, symbol: "phone")
                field(.email, "field.email", keyboard: .emailAddress, contentType: .emailAddress, capitalization: .never, symbol: "envelope")
                field(.emailAlt, "field.email_alt", keyboard: .emailAddress, contentType: .emailAddress, capitalization: .never, symbol: "envelope")
                field(.website, "field.website", keyboard: .URL, contentType: .URL, capitalization: .never, symbol: "globe")
            case .business:
                categoryPicker
                tagEditor
                field(.address, "field.address", contentType: .fullStreetAddress, axis: .vertical, symbol: "mappin.and.ellipse")
            case .details:
                field(.notes, "field.notes", axis: .vertical, symbol: "text.alignleft")
                Text(L10n.s("form.notes_private")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                if !state.review || state.front == nil { imageSlots }
            }
        }
    }

    private func field(_ f: ContactField, _ key: String, required: Bool = false, keyboard: UIKeyboardType = .default,
                       contentType: UITextContentType? = nil, capitalization: TextInputAutocapitalization = .sentences,
                       axis: Axis = .horizontal, symbol: String? = nil) -> some View {
        VCTextField(label: L10n.s(key), text: Binding(get: { state.contact[f] }, set: { state.contact[f] = $0; state.errors[f] = nil }),
                    required: required, error: state.errors[f]?.message(f), keyboard: keyboard, contentType: contentType,
                    capitalization: capitalization, axis: axis, symbol: symbol)
    }

    private var categoryPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.s("field.category")).font(VCFont.labelLarge)
            FlowLayout(spacing: 8) {
                VCChip(title: L10n.s("category.none"), selected: state.contact.categoryId == nil) { state.contact.categoryId = nil }
                ForEach(state.categories) { c in
                    VCChip(title: c.displayName, selected: state.contact.categoryId == c.id, dot: Color(argb: c.colorARGB)) { state.contact.categoryId = c.id }
                }
            }
        }
    }

    private var tagEditor: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.s("field.tags")).font(VCFont.labelLarge)
            if !state.tags.isEmpty {
                FlowLayout(spacing: 8) {
                    ForEach(state.tags) { t in
                        VCChip(title: "#" + t.name, selected: state.tagIds.contains(t.id)) {
                            if state.tagIds.contains(t.id) { state.tagIds.remove(t.id) } else { state.tagIds.insert(t.id) }
                        }
                    }
                }
            }
            HStack(spacing: 10) {
                TextField(L10n.s("form.new_tag"), text: $newTag).font(VCFont.bodyLarge).submitLabel(.done)
                    .onSubmit(addTag)
                    .padding(.horizontal, 16).frame(minHeight: 48)
                    .background(VC.field, in: Capsule()).overlay(Capsule().stroke(VC.outlineVariant))
                Button(action: addTag) { Image(systemName: "plus").frame(width: 48, height: 48).background(VC.primaryContainer, in: Circle()) }
                    .disabled(newTag.trimmed.isEmpty)
                    .accessibilityLabel(L10n.s("form.add_tag"))
            }
        }
    }

    private func addTag() {
        guard !newTag.trimmed.isEmpty else { return }
        actions.addTag(newTag)
        newTag = ""
    }

    private var imageSlots: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.s("detail.tab.card_images")).font(VCFont.labelLarge)
            HStack(spacing: 12) {
                slot(.front, state.front, L10n.s("card.front"))
                slot(.back, state.back, L10n.s("card.back"))
            }
        }
    }

    private func slot(_ side: CardSide, _ image: UIImage?, _ label: String) -> some View {
        Menu {
            if CameraPicker.isAvailable {
                Button { camera = side } label: { Label(L10n.s("scan.take_photo"), systemImage: "camera") }
            }
            Button { pickerSide = side; showPicker = true } label: { Label(L10n.s("scan.pick_gallery"), systemImage: "photo") }
            if image != nil {
                Button(role: .destructive) { actions.setImage(side, nil) } label: { Label(L10n.s("form.remove_image"), systemImage: "trash") }
            }
        } label: {
            ZStack {
                if let image {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    VStack(spacing: 6) {
                        Image(systemName: "camera.fill").font(.system(size: 20)).foregroundStyle(VC.primary)
                        Text(label).font(VCFont.labelMedium).foregroundStyle(VC.onSurfaceVariant)
                    }
                }
            }
            .frame(maxWidth: .infinity).frame(height: 100)
            .background(VC.cardHigh)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(VC.outlineVariant, style: StrokeStyle(lineWidth: 1, dash: image == nil ? [5] : [])))
        }
        .accessibilityLabel(label)
    }

    private var bottomBar: some View {
        HStack(spacing: 12) {
            if !state.review && state.step != .basic {
                VCSecondaryButton(title: L10n.s("common.back")) { withAnimation { state.step = EditStep(rawValue: state.step.rawValue - 1)! } }
                    .frame(maxWidth: 140)
            }
            if state.review || state.step == .details {
                VCPrimaryButton(title: L10n.s("common.save"), icon: "checkmark", loading: state.saving, action: actions.save)
            } else {
                VCPrimaryButton(title: L10n.s("common.next"), trailingIcon: "arrow.right") {
                    let errors = ContactValidator.validate(state.contact).filter { state.step.fields.contains($0.key) }
                    if errors.isEmpty { withAnimation { state.step = EditStep(rawValue: state.step.rawValue + 1)! } } else { state.errors.merge(errors) { $1 } }
                }
            }
        }
        .padding(.horizontal, VC.screen).padding(.vertical, 12)
        .background(VC.card.ignoresSafeArea(edges: .bottom).shadow(color: VC.shadow.opacity(0.08), radius: 8, y: -2))
    }
}

extension Contact {
    subscript(field: ContactField) -> String {
        get {
            switch field {
            case .fullName: fullName
            case .jobTitle: jobTitle
            case .company: company
            case .phone: phone
            case .phoneAlt: phoneAlt
            case .email: email
            case .emailAlt: emailAlt
            case .website: website
            case .address: address
            case .notes: notes
            }
        }
        set {
            switch field {
            case .fullName: fullName = newValue
            case .jobTitle: jobTitle = newValue
            case .company: company = newValue
            case .phone: phone = newValue
            case .phoneAlt: phoneAlt = newValue
            case .email: email = newValue
            case .emailAlt: emailAlt = newValue
            case .website: website = newValue
            case .address: address = newValue
            case .notes: notes = newValue
            }
        }
    }
}

private struct CameraRequest: Identifiable {
    let side: CardSide
    var id: Int { side == .front ? 0 : 1 }
}

/// System camera for a single photo. iOS asks for camera permission (NSCameraUsageDescription) on first use.
struct CameraPicker: UIViewControllerRepresentable {
    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }
    let onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let c = UIImagePickerController()
        c.sourceType = .camera
        c.delegate = context.coordinator
        return c
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}
