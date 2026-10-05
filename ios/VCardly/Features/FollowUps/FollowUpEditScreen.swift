import SwiftUI

struct FollowUpEditState {
    var followUp = FollowUp(contactId: UUID(), title: "", dueAt: FollowUpEditState.defaultDue())
    var contactChosen = false
    var contactName = ""
    var contacts: [ContactDetails] = []
    var isNew = true
    var titleError = false
    var contactError = false
    var saveFailed = false

    /// Next full hour, at least 30 minutes from now.
    static func defaultDue(now: Date = Date()) -> Date {
        let cal = Calendar.current
        let base = now.addingTimeInterval(30 * 60)
        let hour = cal.dateInterval(of: .hour, for: base)?.end ?? base
        return hour
    }
}

struct FollowUpEditActions {
    var save: () -> Void = {}
    var setStatus: (FollowUpStatus) -> Void = { _ in }
    var delete: () -> Void = {}
}

struct FollowUpEditScreen: View {
    @Environment(AppEnvironment.self) private var env
    let followUpId: UUID?
    let presetContactId: UUID?
    let done: () -> Void
    @State private var state = FollowUpEditState()
    @State private var loaded = false
    @State private var permissionAsked = false

    var body: some View {
        FollowUpEditContent(state: $state, actions: FollowUpEditActions(
            save: save,
            setStatus: { s in
                env.followUps.setStatus(state.followUp.id, s)
                done()
            },
            delete: { env.followUps.delete(state.followUp.id); done() }
        ))
        .navigationTitle(L10n.s(state.isNew ? "followup.add" : "followup.edit"))
        .navigationBarTitleDisplayMode(.inline)
        .alert(L10n.s("form.save_failed"), isPresented: $state.saveFailed) { Button(L10n.s("common.ok")) {} }
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        state.contacts = env.contacts.contacts()
        if let followUpId, let existing = env.followUps.get(followUpId) {
            state.followUp = existing.followUp
            state.contactName = existing.contactName
            state.contactChosen = true
            state.isNew = false
        } else if let presetContactId, let c = env.contacts.contact(presetContactId) {
            state.followUp.contactId = c.id
            state.contactName = c.contact.fullName
            state.contactChosen = true
        }
    }

    private func save() {
        state.titleError = state.followUp.title.trimmed.isEmpty
        state.contactError = !state.contactChosen
        guard !state.titleError, !state.contactError else { return }
        do {
            try env.followUps.save(state.followUp)
            if state.followUp.reminderEnabled && !permissionAsked {
                permissionAsked = true
                Task { _ = await env.reminders.requestPermission() }
            }
            done()
        } catch {
            state.saveFailed = true
        }
    }
}

/// Stateless follow-up form (used directly by screenshot tests).
struct FollowUpEditContent: View {
    @Binding var state: FollowUpEditState
    let actions: FollowUpEditActions
    @State private var pickingContact = false
    @State private var confirmDelete = false

    private var f: Binding<FollowUp> { $state.followUp }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if !state.isNew && state.followUp.status != .pending {
                        VCTag(title: state.followUp.status.label, tone: state.followUp.status == .completed ? VC.mint : (state.followUp.status == .cancelled ? VC.rose : VC.orange))
                    }
                    contactField
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L10n.s("followup.type")).font(VCFont.labelLarge)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                            ForEach(FollowUpType.pickable, id: \.self) { t in typeTile(t) }
                        }
                    }
                    VCTextField(label: L10n.s("followup.title_field"), text: Binding(get: { state.followUp.title }, set: { state.followUp.title = $0; state.titleError = false }),
                                placeholder: L10n.s("followup.title_hint"), required: true, error: state.titleError ? L10n.s("validation.required") : nil)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L10n.s("followup.when")).font(VCFont.labelLarge)
                        DatePicker(L10n.s("followup.when"), selection: f.dueAt, displayedComponents: [.date, .hourAndMinute])
                            .labelsHidden()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(VC.field, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(VC.outlineVariant))
                        if state.followUp.dueAt < Date() && state.followUp.status.isActive {
                            Text(L10n.s("followup.past_warning")).font(VCFont.bodySmall).foregroundStyle(VC.orange.content)
                        }
                    }
                    reminder
                    VCTextField(label: L10n.s("field.notes"), text: f.notes, placeholder: L10n.s("followup.notes_hint"), axis: .vertical)
                    if !state.isNew { statusActions }
                }
                .padding(.horizontal, VC.screen).padding(.vertical, 16)
            }
            .scrollDismissesKeyboard(.interactively)
            VCPrimaryButton(title: L10n.s("common.save"), icon: "checkmark", action: actions.save)
                .padding(.horizontal, VC.screen).padding(.vertical, 12)
                .background(VC.card.ignoresSafeArea(edges: .bottom))
        }
        .background(VC.background)
        .sheet(isPresented: $pickingContact) {
            ContactPickerSheet(contacts: state.contacts) { d in
                state.followUp.contactId = d.id
                state.contactName = d.contact.fullName
                state.contactChosen = true
                state.contactError = false
                pickingContact = false
            }
        }
        .confirmationDialog(L10n.s("followup.delete.title"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L10n.s("common.delete"), role: .destructive, action: actions.delete)
        } message: {
            Text(L10n.s("followup.delete.message"))
        }
    }

    private var contactField: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 2) {
                Text(L10n.s("followup.contact")).font(VCFont.labelLarge)
                Text(verbatim: "*").font(VCFont.labelLarge).foregroundStyle(VC.error).accessibilityHidden(true)
            }
            Button { pickingContact = true } label: {
                HStack(spacing: 12) {
                    if state.contactChosen {
                        VCAvatar(name: state.contactName, size: 36)
                        Text(state.contactName).font(VCFont.bodyLarge).foregroundStyle(VC.onSurface)
                    } else {
                        Image(systemName: "person.crop.circle.badge.plus").foregroundStyle(VC.onSurfaceVariant)
                        Text(L10n.s("followup.choose_contact")).font(VCFont.bodyLarge).foregroundStyle(VC.onSurfaceVariant)
                    }
                    Spacer()
                    Image(systemName: "chevron.down").foregroundStyle(VC.onSurfaceVariant)
                }
                .padding(.horizontal, 16).frame(minHeight: 56)
                .background(VC.field, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(state.contactError ? VC.error : VC.outlineVariant, lineWidth: state.contactError ? 1.5 : 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(state.contactChosen ? L10n.s("followup.contact_value", state.contactName) : L10n.s("followup.choose_contact"))
            if state.contactError {
                Label(L10n.s("followup.contact_required"), systemImage: "exclamationmark.circle.fill").font(VCFont.bodySmall).foregroundStyle(VC.error)
            }
        }
    }

    private func typeTile(_ t: FollowUpType) -> some View {
        let on = state.followUp.type == t
        return Button { state.followUp.type = t } label: {
            VStack(spacing: 6) {
                Image(systemName: t.symbol).font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(on ? .white : t.tone.accent)
                    .frame(width: 44, height: 44)
                    .background(on ? t.tone.accent : t.tone.container, in: Circle())
                Text(t.label).font(VCFont.labelSmall).foregroundStyle(on ? VC.onSurface : VC.onSurfaceVariant).lineLimit(1).minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 8)
            .background(on ? VC.primaryContainer.opacity(0.5) : .clear, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(scale: 0.94))
        .accessibilityLabel(t.label)
        .accessibilityAddTraits(on ? [.isSelected] : [])
    }

    private var reminder: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: f.reminderEnabled) {
                HStack(spacing: 12) {
                    VCIconBadge(symbol: "bell.fill", tone: VC.orange, size: 36, circle: true)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(L10n.s("followup.reminder")).font(VCFont.titleSmall)
                        Text(L10n.s("followup.reminder_hint")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                    }
                }
            }
            .tint(VC.primary)
            if state.followUp.reminderEnabled {
                FlowLayout(spacing: 8) {
                    ForEach(ReminderOffsets.options, id: \.self) { m in
                        VCChip(title: Self.offsetLabel(m), selected: state.followUp.reminderOffsetMinutes == m) { state.followUp.reminderOffsetMinutes = m }
                    }
                }
            }
        }
        .vcCard()
    }

    static func offsetLabel(_ minutes: Int) -> String {
        switch minutes {
        case 0: L10n.s("reminder.at_time")
        case 1440: L10n.s("reminder.day_before")
        case let m where m % 60 == 0: L10n.plural("reminder.hours_before", m / 60)
        default: L10n.plural("reminder.minutes_before", minutes)
        }
    }

    private var statusActions: some View {
        VStack(spacing: 10) {
            if state.followUp.status.isActive {
                HStack(spacing: 10) {
                    VCTonalButton(title: L10n.s("followup.mark_done"), icon: "checkmark") { actions.setStatus(.completed) }
                    VCSecondaryButton(title: L10n.s("followup.cancel_item"), icon: "xmark") { actions.setStatus(.cancelled) }
                }
            } else {
                VCTonalButton(title: L10n.s("followup.reopen"), icon: "arrow.uturn.backward") { actions.setStatus(.pending) }
            }
            Button(role: .destructive) { confirmDelete = true } label: {
                Label(L10n.s("followup.delete.title"), systemImage: "trash").font(VCFont.labelLarge).frame(maxWidth: .infinity, minHeight: 44)
            }
            .tint(VC.error)
        }
        .padding(.top, 6)
    }
}

/// Searchable contact list in a sheet.
struct ContactPickerSheet: View {
    let contacts: [ContactDetails]
    let onPick: (ContactDetails) -> Void
    @State private var query = ""

    var body: some View {
        let q = query.trimmed.lowercased()
        let shown = q.isEmpty ? contacts : contacts.filter { ($0.contact.fullName + " " + $0.contact.company).lowercased().contains(q) }
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.s("followup.choose_contact")).font(VCFont.titleLarge).padding(.top, 20)
            VCSearchField(text: $query, placeholder: L10n.s("home.search"))
            if contacts.isEmpty {
                VCEmptyState(symbol: "person.2", title: L10n.s("followup.no_contacts.title"), message: L10n.s("followup.no_contacts.message"))
            } else {
                ScrollView {
                    LazyVStack(spacing: 4) {
                        ForEach(shown) { d in
                            Button { onPick(d) } label: {
                                HStack(spacing: 12) {
                                    VCAvatar(name: d.contact.fullName, argb: d.category?.colorARGB, size: 40)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(d.contact.fullName).font(VCFont.titleSmall).foregroundStyle(VC.onSurface)
                                        if !d.contact.company.isEmpty { Text(d.contact.company).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant) }
                                    }
                                    Spacer()
                                }
                                .padding(.vertical, 8).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, VC.screen)
        .background(VC.background)
        .presentationDetents([.medium, .large])
    }
}
