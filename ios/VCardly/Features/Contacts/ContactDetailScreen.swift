import SwiftUI

enum DetailTab: CaseIterable { case details, cardImages, notes, followUps }

struct ContactDetailActions {
    var edit: () -> Void = {}
    var share: () -> Void = {}
    var delete: () -> Void = {}
    var toggleFavorite: () -> Void = {}
    var addFollowUp: () -> Void = {}
    var openFollowUp: (UUID) -> Void = { _ in }
    var toggleFollowUp: (FollowUp) -> Void = { _ in }
}

struct ContactDetailScreen: View {
    @Environment(AppEnvironment.self) private var env
    let contactId: UUID
    let edit: () -> Void
    let share: () -> Void
    let addFollowUp: () -> Void
    let openFollowUp: (UUID) -> Void
    let deleted: () -> Void
    @State private var details: ContactDetails?
    @State private var followUps: [FollowUp] = []
    @State private var loaded = false
    @State private var confirmDelete = false

    var body: some View {
        Group {
            if let details {
                ContactDetailContent(details: details, followUps: followUps, frontImage: env.images.load(details.contact.frontImagePath),
                                     backImage: env.images.load(details.contact.backImagePath),
                                     actions: ContactDetailActions(
                                        edit: edit, share: share, delete: { confirmDelete = true },
                                        toggleFavorite: { env.contacts.setFavorite(contactId, !details.contact.isFavorite) },
                                        addFollowUp: addFollowUp, openFollowUp: openFollowUp,
                                        toggleFollowUp: { f in env.followUps.setStatus(f.id, f.status.isActive ? .completed : .pending) }))
            } else if loaded {
                VCEmptyState(symbol: "person.crop.circle.badge.xmark", title: L10n.s("contact.not_found.title"), message: L10n.s("contact.not_found.message"))
            } else {
                ProgressView()
            }
        }
        .task(id: env.revision.value) {
            details = env.contacts.contact(contactId)
            followUps = env.followUps.forContact(contactId)
            loaded = true
        }
        .confirmationDialog(L10n.s("contact.delete.title"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L10n.s("common.delete"), role: .destructive) {
                try? env.contacts.delete(contactId)
                deleted()
            }
        } message: {
            Text(L10n.s("contact.delete.message", details?.contact.fullName ?? ""))
        }
    }
}

/// Stateless contact details (used directly by screenshot tests).
struct ContactDetailContent: View {
    let details: ContactDetails
    let followUps: [FollowUp]
    var frontImage: UIImage?
    var backImage: UIImage?
    let actions: ContactDetailActions
    @State var tab: DetailTab = .details
    @State private var zoom: UIImage?
    @State private var copied = false

    private var c: Contact { details.contact }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                hero
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 10) {
                            Text(c.fullName).font(VCFont.headlineSmall).foregroundStyle(VC.onSurface).accessibilityAddTraits(.isHeader)
                            if let cat = details.category {
                                VCTag(title: cat.displayName, tone: Tone(container: Color(argb: cat.colorARGB).opacity(0.15), content: VC.onSurface, accent: Color(argb: cat.colorARGB)))
                            }
                        }
                        if !c.jobTitle.isEmpty { Text(c.jobTitle).font(VCFont.bodyLarge).foregroundStyle(VC.onSurfaceVariant) }
                        if !c.company.isEmpty { Text(c.company).font(VCFont.titleSmall).foregroundStyle(VC.primary) }
                    }
                    quickActions
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            VCChip(title: L10n.s("detail.tab.details"), selected: tab == .details) { tab = .details }
                            VCChip(title: L10n.s("detail.tab.card_images"), selected: tab == .cardImages) { tab = .cardImages }
                            VCChip(title: L10n.s("field.notes"), selected: tab == .notes) { tab = .notes }
                            let active = followUps.filter { $0.status.isActive }.count
                            VCChip(title: active > 0 ? L10n.s("detail.tab.followups_count", active) : L10n.s("nav.followups"), selected: tab == .followUps) { tab = .followUps }
                        }
                    }
                    Group {
                        switch tab {
                        case .details: detailsTab
                        case .cardImages: imagesTab
                        case .notes: notesTab
                        case .followUps: followUpsTab
                        }
                    }
                    .animation(.easeInOut(duration: 0.2), value: tab)
                }
                .padding(.horizontal, VC.screen).padding(.top, 22).padding(.bottom, 32)
                .background(VC.background, in: UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28))
                .offset(y: -22)
            }
        }
        .background(VC.background)
        .ignoresSafeArea(edges: .top)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                FavoriteButton(isFavorite: c.isFavorite, name: c.fullName, tint: .white, action: actions.toggleFavorite)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: actions.share) { Image(systemName: "qrcode") }.tint(.white).accessibilityLabel(L10n.s("share.title"))
            }
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .sheet(item: Binding(get: { zoom.map(ZoomImage.init) }, set: { zoom = $0?.image })) { z in ZoomView(image: z.image) }
        .overlay(alignment: .bottom) {
            if copied {
                Text(L10n.s("common.copied")).font(VCFont.labelLarge).foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 10).background(VC.onSurface.opacity(0.9), in: Capsule())
                    .padding(.bottom, 24).transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private var hero: some View {
        ZStack {
            LinearGradient(colors: [VC.navyDeep, Color(argb: 0xFF1B2D6B)], startPoint: .top, endPoint: .bottom)
            Group {
                if let frontImage {
                    Image(uiImage: frontImage).resizable().scaledToFill()
                        .aspectRatio(1.6, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .onTapGesture { zoom = frontImage }
                        .accessibilityLabel(L10n.s("card.front"))
                        .accessibilityAddTraits(.isButton)
                } else {
                    BusinessCardArt(name: c.fullName, jobTitle: c.jobTitle, company: c.company, phone: c.phone, email: c.email, website: c.website)
                }
            }
            .rotationEffect(.degrees(-3))
            .shadow(color: .black.opacity(0.4), radius: 20, y: 10)
            .padding(.horizontal, 32).padding(.top, 100).padding(.bottom, 56)
        }
    }

    private var quickActions: some View {
        let phone = c.phone.isEmpty ? c.phoneAlt : c.phone
        let email = c.email.isEmpty ? c.emailAlt : c.email
        return HStack {
            QuickAction(symbol: "phone.fill", label: L10n.s("action.call"), tone: VC.blue, enabled: !phone.isEmpty) { ExternalActions.call(phone) }
            QuickAction(symbol: "message.fill", label: L10n.s("followup.type.whatsapp"), tone: VC.mint, enabled: whatsAppDigits(phone) != nil) { ExternalActions.whatsApp(phone) }
            QuickAction(symbol: "envelope.fill", label: L10n.s("field.email"), tone: VC.lavender, enabled: !email.isEmpty) { ExternalActions.email(email) }
            Menu {
                Button(action: actions.share) { Label(L10n.s("share.title"), systemImage: "qrcode") }
                Button(action: actions.edit) { Label(L10n.s("common.edit"), systemImage: "pencil") }
                Button(action: actions.addFollowUp) { Label(L10n.s("followup.add"), systemImage: "calendar.badge.plus") }
                Button(role: .destructive, action: actions.delete) { Label(L10n.s("common.delete"), systemImage: "trash") }
            } label: {
                QuickActionLabel(symbol: "ellipsis", label: L10n.s("common.more"), tone: VC.navy, enabled: true)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func copy(_ value: String) {
        ExternalActions.copy(value)
        withAnimation { copied = true }
        Task { try? await Task.sleep(nanoseconds: 1_500_000_000); withAnimation { copied = false } }
    }

    @ViewBuilder private var detailsTab: some View {
        let rows = infoRows
        if rows.isEmpty {
            Text(L10n.s("detail.no_info")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
        } else {
            VStack(spacing: 0) { ForEach(rows.indices, id: \.self) { rows[$0] } }.vcCard(padding: 4)
        }
        if !details.tags.isEmpty {
            VCSectionHeader(title: L10n.s("field.tags"))
            FlowLayout(spacing: 8) { ForEach(details.tags) { VCTag(title: "#" + $0.name, tone: VC.blue) } }
        }
        VCSectionHeader(title: L10n.s("detail.activity"))
        VCTimeline(entries: activity).frame(maxWidth: .infinity, alignment: .leading).vcCard()
    }

    private var infoRows: [AnyView] {
        var rows: [AnyView] = []
        func add(_ symbol: String, _ tone: Tone, _ label: String, _ value: String, _ tap: @escaping () -> Void) {
            guard !value.isEmpty else { return }
            rows.append(AnyView(VCInfoRow(symbol: symbol, tone: tone, label: label, value: value, onTap: tap,
                                          onCopy: { copy(value) }, copyLabel: L10n.s("common.copy_item", label))))
        }
        add("phone.fill", VC.blue, L10n.s("field.phone"), c.phone) { ExternalActions.call(c.phone) }
        add("phone.fill", VC.blue, L10n.s("field.phone_alt"), c.phoneAlt) { ExternalActions.call(c.phoneAlt) }
        let wa = c.phone.isEmpty ? c.phoneAlt : c.phone
        if whatsAppDigits(wa) != nil { add("message.fill", VC.mint, L10n.s("followup.type.whatsapp"), wa) { ExternalActions.whatsApp(wa) } }
        add("envelope.fill", VC.lavender, L10n.s("field.email"), c.email) { ExternalActions.email(c.email) }
        add("envelope.fill", VC.lavender, L10n.s("field.email_alt"), c.emailAlt) { ExternalActions.email(c.emailAlt) }
        add("globe", VC.blue, L10n.s("field.website"), c.website) { ExternalActions.website(c.website) }
        add("mappin.and.ellipse", VC.orange, L10n.s("field.address"), c.address) { ExternalActions.map(c.address) }
        return rows
    }

    /// Derived only from stored timestamps; nothing is invented.
    private var activity: [VCTimelineEntry] {
        var events: [(Date, VCTimelineEntry)] = []
        func t(_ d: Date) -> String { d.formatted(date: .abbreviated, time: .shortened) }
        let createdKey = switch c.source { case .scan: "activity.created_scan"; case .importFile: "activity.created_import"; case .manual: "activity.created" }
        events.append((c.createdAt, VCTimelineEntry(symbol: c.source == .scan ? "doc.viewfinder" : "person.badge.plus", tone: VC.blue, title: L10n.s(createdKey), time: t(c.createdAt))))
        if c.updatedAt.timeIntervalSince(c.createdAt) > 60 {
            events.append((c.updatedAt, VCTimelineEntry(symbol: "pencil", tone: VC.lavender, title: L10n.s("activity.updated"), time: t(c.updatedAt))))
        }
        for f in followUps {
            events.append((f.createdAt, VCTimelineEntry(symbol: "calendar.badge.plus", tone: VC.orange, title: L10n.s("activity.followup_created", f.title), time: t(f.createdAt))))
            if f.status == .completed, let d = f.completedAt {
                events.append((d, VCTimelineEntry(symbol: "checkmark.circle.fill", tone: VC.mint, title: L10n.s("activity.followup_completed", f.title), time: t(d))))
            }
            if f.status == .cancelled {
                events.append((f.updatedAt, VCTimelineEntry(symbol: "xmark.circle.fill", tone: VC.rose, title: L10n.s("activity.followup_cancelled", f.title), time: t(f.updatedAt))))
            }
        }
        return events.sorted { $0.0 > $1.0 }.prefix(8).map(\.1)
    }

    @ViewBuilder private var imagesTab: some View {
        let images = [(frontImage, L10n.s("card.front")), (backImage, L10n.s("card.back"))].compactMap { img, label in img.map { ($0, label) } }
        if images.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "photo.badge.plus").font(.system(size: 34)).foregroundStyle(VC.blue.accent)
                Text(L10n.s("detail.no_cards.title")).font(VCFont.titleMedium)
                Text(L10n.s("detail.no_cards.message")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant).multilineTextAlignment(.center)
                VCTonalButton(title: L10n.s("detail.add_card_images"), icon: "plus", fullWidth: false, action: actions.edit)
            }
            .frame(maxWidth: .infinity).vcCard(padding: 24)
        } else {
            ForEach(images.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 6) {
                    Text(images[i].1).font(VCFont.titleSmall)
                    Image(uiImage: images[i].0).resizable().scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .onTapGesture { zoom = images[i].0 }
                        .accessibilityLabel(images[i].1).accessibilityAddTraits(.isButton)
                }
            }
            VCTonalButton(title: L10n.s("detail.manage_images"), icon: "pencil", action: actions.edit)
        }
    }

    private var notesTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "text.alignleft").foregroundStyle(VC.lavender.accent)
                Text(L10n.s("field.notes")).font(VCFont.titleMedium)
                Spacer()
                Button(action: actions.edit) { Image(systemName: "pencil").frame(width: 44, height: 44) }.accessibilityLabel(L10n.s("detail.edit_notes"))
            }
            Text(c.notes.isEmpty ? L10n.s("detail.no_notes") : c.notes).font(VCFont.bodyLarge)
                .foregroundStyle(c.notes.isEmpty ? VC.onSurfaceVariant : VC.onSurface)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .vcCard(padding: 20)
    }

    @ViewBuilder private var followUpsTab: some View {
        if followUps.isEmpty {
            Text(L10n.s("followup.none.title")).font(VCFont.titleSmall)
            Text(L10n.s("detail.followups_empty")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
        }
        ForEach(followUps) { f in
            FollowUpCard(item: FollowUpWithContact(followUp: f, contactName: c.fullName, contactCompany: c.company),
                         onTap: { actions.openFollowUp(f.id) }, onToggle: { actions.toggleFollowUp(f) })
        }
        VCTonalButton(title: L10n.s("followup.add"), icon: "plus", action: actions.addFollowUp)
    }
}

private struct QuickAction: View {
    let symbol: String, label: String, tone: Tone, enabled: Bool, action: () -> Void
    var body: some View {
        Button(action: action) { QuickActionLabel(symbol: symbol, label: label, tone: tone, enabled: enabled) }
            .disabled(!enabled).frame(maxWidth: .infinity).buttonStyle(PressScaleStyle(scale: 0.94))
    }
}

private struct QuickActionLabel: View {
    let symbol: String, label: String, tone: Tone, enabled: Bool
    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: symbol).font(.system(size: 20, weight: .semibold))
                .foregroundStyle(enabled ? tone.accent : VC.outline)
                .frame(width: 54, height: 54).background(enabled ? tone.container : VC.cardHigh, in: Circle())
            Text(label).font(VCFont.labelMedium).foregroundStyle(enabled ? VC.onSurface : VC.onSurfaceVariant).lineLimit(1).minimumScaleFactor(0.6)
        }
    }
}

private struct ZoomImage: Identifiable { let image: UIImage; var id: ObjectIdentifier { ObjectIdentifier(image) } }

/// Full-screen viewer: pinch to zoom, double-tap to reset.
private struct ZoomView: View {
    let image: UIImage
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            Image(uiImage: image).resizable().scaledToFit().scaleEffect(scale)
                .gesture(MagnificationGesture().onChanged { scale = min(max($0, 1), 5) })
                .onTapGesture(count: 2) { withAnimation { scale = 1 } }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Button { dismiss() } label: {
                Image(systemName: "xmark").foregroundStyle(.white).frame(width: 44, height: 44).background(.white.opacity(0.16), in: Circle())
            }
            .padding()
            .accessibilityLabel(L10n.s("common.close"))
        }
    }
}
