import SwiftUI

struct ContactsState {
    var filter = ContactFilter()
    var contacts: [ContactDetails] = []
    var categories: [Category] = []
    var tags: [Tag] = []
    var loaded = false
}

struct ContactsActions {
    var setFilter: (ContactFilter) -> Void = { _ in }
    var toggleFavorite: (ContactDetails) -> Void = { _ in }
    var open: (UUID) -> Void = { _ in }
    var add: () -> Void = {}
    var scan: () -> Void = {}
}

struct ContactsScreen: View {
    @Environment(AppEnvironment.self) private var env
    let openContact: (UUID) -> Void
    let addContact: () -> Void
    let scan: () -> Void
    @State private var state = ContactsState()

    var body: some View {
        ContactsContent(state: state, actions: ContactsActions(
            setFilter: { state.filter = $0; load() },
            toggleFavorite: { env.contacts.setFavorite($0.id, !$0.contact.isFavorite) },
            open: openContact, add: addContact, scan: scan
        ))
        .task(id: env.revision.value) { load() }
    }

    private func load() {
        state.contacts = env.contacts.contacts(state.filter)
        state.categories = env.contacts.categories()
        state.tags = env.contacts.tags()
        state.loaded = true
    }
}

/// Stateless contacts list (used directly by screenshot tests).
struct ContactsContent: View {
    let state: ContactsState
    let actions: ContactsActions
    @State private var showTags = false

    private var filter: ContactFilter { state.filter }
    private func update(_ f: (inout ContactFilter) -> Void) { var x = filter; f(&x); actions.setFilter(x) }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(L10n.s("nav.contacts")).font(VCFont.headlineMedium).foregroundStyle(VC.onSurface).accessibilityAddTraits(.isHeader)
                        Text(filter.hasActiveFilters ? L10n.s("contacts.shown", state.contacts.count) : L10n.plural("contacts.count", state.contacts.count))
                            .font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
                    }
                    Spacer()
                    Menu {
                        Picker(L10n.s("contacts.sort"), selection: Binding(get: { filter.sort }, set: { s in update { $0.sort = s } })) {
                            ForEach(ContactSort.allCases, id: \.self) { Text(L10n.s("sort.\($0.rawValue)")).tag($0) }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down").font(.system(size: 17, weight: .semibold)).foregroundStyle(VC.onSurface).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(L10n.s("contacts.sort"))
                }
                .padding(.horizontal, VC.screen).padding(.top, 12)

                VCSearchField(text: Binding(get: { filter.query }, set: { q in update { $0.query = q } }), placeholder: L10n.s("home.search"))
                    .padding(.horizontal, VC.screen).padding(.top, 8)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        VCChip(title: L10n.s("contacts.filter.all"), selected: !filter.favoritesOnly && filter.categoryId == nil && filter.tagIds.isEmpty) {
                            update { $0.favoritesOnly = false; $0.categoryId = nil; $0.tagIds = [] }
                        }
                        VCChip(title: L10n.s("home.stat.favorites"), selected: filter.favoritesOnly, symbol: "star.fill") { update { $0.favoritesOnly.toggle() } }
                        ForEach(state.categories) { c in
                            VCChip(title: c.displayName, selected: filter.categoryId == c.id, dot: Color(argb: c.colorARGB)) {
                                update { $0.categoryId = $0.categoryId == c.id ? nil : c.id }
                            }
                        }
                        if !state.tags.isEmpty {
                            VCChip(title: filter.tagIds.isEmpty ? L10n.s("field.tags") : L10n.s("contacts.tags_selected", filter.tagIds.count),
                                   selected: !filter.tagIds.isEmpty, symbol: "tag.fill") { showTags = true }
                        }
                    }
                    .padding(.horizontal, VC.screen).padding(.vertical, 12)
                }

                if !state.loaded {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if state.contacts.isEmpty && filter.favoritesOnly && filter.query.isEmpty && filter.categoryId == nil {
                    VCEmptyState(symbol: "star.fill", title: L10n.s("contacts.no_favorites.title"), message: L10n.s("contacts.no_favorites.message"), tone: VC.orange)
                } else if state.contacts.isEmpty && filter.hasActiveFilters {
                    VCEmptyState(symbol: "magnifyingglass", title: L10n.s("contacts.no_results.title"), message: L10n.s("contacts.no_results.message")) {
                        VCSecondaryButton(title: L10n.s("contacts.clear_filters")) { actions.setFilter(ContactFilter(sort: filter.sort)) }.frame(width: 220)
                    }
                } else if state.contacts.isEmpty {
                    VCEmptyState(symbol: "doc.viewfinder", title: L10n.s("empty.network.title"), message: L10n.s("contacts.empty.message")) {
                        VStack(spacing: 10) {
                            VCPrimaryButton(title: L10n.s("empty.network.action"), icon: "doc.viewfinder", action: actions.scan)
                            VCSecondaryButton(title: L10n.s("contact.add"), icon: "person.badge.plus", action: actions.add)
                        }
                        .frame(width: 260)
                    }
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(state.contacts) { d in
                                ContactCard(details: d, onTap: { actions.open(d.id) }, onToggleFavorite: { actions.toggleFavorite(d) })
                                    .transition(.opacity.combined(with: .move(edge: .bottom)))
                            }
                        }
                        .padding(.horizontal, VC.screen).padding(.top, 4).padding(.bottom, 104)
                        .animation(.easeOut(duration: 0.25), value: state.contacts.map(\.id))
                    }
                }
            }
            if state.loaded && !state.contacts.isEmpty {
                VCFab(symbol: "plus", label: L10n.s("contact.add"), action: actions.add)
                    .padding(.trailing, VC.screen).padding(.bottom, 20)
            }
        }
        .background(VC.background)
        .sheet(isPresented: $showTags) {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.s("contacts.filter_tags")).font(VCFont.titleLarge)
                Text(L10n.s("contacts.filter_tags.hint")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
                FlowLayout(spacing: 8) {
                    ForEach(state.tags) { t in
                        VCChip(title: "#" + t.name, selected: filter.tagIds.contains(t.id)) {
                            update { if $0.tagIds.contains(t.id) { $0.tagIds.remove(t.id) } else { $0.tagIds.insert(t.id) } }
                        }
                    }
                }
                Spacer()
            }
            .padding(VC.screen)
            .presentationDetents([.medium])
        }
    }
}

/// Circular gradient floating action button.
struct VCFab: View {
    let symbol: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 24, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 60, height: 60)
                .background(LinearGradient(colors: [VC.gradientIndigo[0], VC.gradientPurple[1]], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                .shadow(color: VC.gradientPurple[1].opacity(0.4), radius: 10, y: 4)
        }
        .buttonStyle(PressScaleStyle(scale: 0.92))
        .accessibilityLabel(label)
    }
}

/// Wrapping layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > width && x > 0 { x = 0; y += rowHeight + spacing; rowHeight = 0 }
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX && x > bounds.minX { x = bounds.minX; y += rowHeight + spacing; rowHeight = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
