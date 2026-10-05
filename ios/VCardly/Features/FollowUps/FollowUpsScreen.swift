import SwiftUI

struct FollowUpsState {
    var bucket: FollowUpBucket = .today
    var items: [FollowUpWithContact] = []
    var counts = FollowUpCounts()
    var loaded = false
}

struct FollowUpsActions {
    var setBucket: (FollowUpBucket) -> Void = { _ in }
    var open: (UUID) -> Void = { _ in }
    var add: () -> Void = {}
    var setStatus: (UUID, FollowUpStatus) -> Void = { _, _ in }
    var delete: (UUID) -> Void = { _ in }
}

struct FollowUpsScreen: View {
    @Environment(AppEnvironment.self) private var env
    let open: (UUID) -> Void
    let add: () -> Void
    @State private var state = FollowUpsState()

    var body: some View {
        FollowUpsContent(state: state, actions: FollowUpsActions(
            setBucket: { state.bucket = $0; load() },
            open: open, add: add,
            setStatus: { env.followUps.setStatus($0, $1) },
            delete: { env.followUps.delete($0) }
        ))
        .task(id: env.revision.value) { load() }
    }

    private func load() {
        state.items = env.followUps.bucket(state.bucket)
        state.counts = env.followUps.counts()
        state.loaded = true
    }
}

extension FollowUpBucket {
    var titleKey: String {
        switch self {
        case .today: "followup.bucket.today"
        case .upcoming: "followup.bucket.upcoming"
        case .overdue: "followup.bucket.overdue"
        case .completed: "followup.bucket.completed"
        }
    }

    func count(_ c: FollowUpCounts) -> Int {
        switch self {
        case .today: c.today
        case .upcoming: c.upcoming
        case .overdue: c.overdue
        case .completed: c.completed
        }
    }
}

/// Stateless follow-ups list (used directly by screenshot tests).
struct FollowUpsContent: View {
    let state: FollowUpsState
    let actions: FollowUpsActions
    @State private var pendingDelete: FollowUpWithContact?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(L10n.s("nav.followups")).font(VCFont.headlineMedium).foregroundStyle(VC.onSurface).accessibilityAddTraits(.isHeader)
                    Text(L10n.s("followup.subtitle", state.counts.pending)).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
                }
                .padding(.horizontal, VC.screen).padding(.top, 12)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(FollowUpBucket.allCases, id: \.self) { b in
                            let n = b.count(state.counts)
                            VCChip(title: n > 0 ? L10n.s("followup.bucket_count", L10n.s(b.titleKey), n) : L10n.s(b.titleKey), selected: state.bucket == b) { actions.setBucket(b) }
                        }
                    }
                    .padding(.horizontal, VC.screen).padding(.vertical, 14)
                }

                if !state.loaded {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if state.items.isEmpty {
                    empty
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            if state.bucket == .overdue {
                                VCNotice(text: L10n.s("followup.overdue_notice"), tone: VC.rose, symbol: "exclamationmark.circle.fill")
                            }
                            ForEach(state.items) { item in
                                FollowUpCard(item: item, onTap: { actions.open(item.id) }, onToggle: {
                                    actions.setStatus(item.id, item.followUp.status.isActive ? .completed : .pending)
                                })
                                .contextMenu { menu(item) }
                                .transition(.opacity.combined(with: .scale(scale: 0.96)))
                            }
                        }
                        .padding(.horizontal, VC.screen).padding(.top, 2).padding(.bottom, 104)
                        .animation(.easeOut(duration: 0.25), value: state.items.map(\.id))
                    }
                }
            }
            VCFab(symbol: "plus", label: L10n.s("followup.add"), action: actions.add)
                .padding(.trailing, VC.screen).padding(.bottom, 20)
        }
        .background(VC.background)
        .confirmationDialog(L10n.s("followup.delete.title"), isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                            titleVisibility: .visible) {
            Button(L10n.s("common.delete"), role: .destructive) { if let p = pendingDelete { actions.delete(p.id) }; pendingDelete = nil }
        } message: {
            Text(L10n.s("followup.delete.message"))
        }
    }

    @ViewBuilder
    private func menu(_ item: FollowUpWithContact) -> some View {
        let f = item.followUp
        Button { actions.open(f.id) } label: { Label(L10n.s("common.edit"), systemImage: "pencil") }
        if f.status.isActive {
            Button { actions.setStatus(f.id, .completed) } label: { Label(L10n.s("followup.mark_done"), systemImage: "checkmark.circle") }
            Button { actions.setStatus(f.id, .cancelled) } label: { Label(L10n.s("followup.cancel_item"), systemImage: "xmark.circle") }
        } else {
            Button { actions.setStatus(f.id, .pending) } label: { Label(L10n.s("followup.reopen"), systemImage: "arrow.uturn.backward") }
        }
        Button(role: .destructive) { pendingDelete = item } label: { Label(L10n.s("common.delete"), systemImage: "trash") }
    }

    private var empty: some View {
        let (symbol, tone, key): (String, Tone, String) = switch state.bucket {
        case .today: ("sun.max.fill", VC.orange, "followup.empty.today")
        case .upcoming: ("calendar", VC.blue, "followup.empty.upcoming")
        case .overdue: ("checkmark.seal.fill", VC.mint, "followup.empty.overdue")
        case .completed: ("checkmark.circle.fill", VC.lavender, "followup.empty.completed")
        }
        return VCEmptyState(symbol: symbol, title: L10n.s("\(key).title"), message: L10n.s("\(key).message"), tone: tone) {
            if state.bucket != .completed && state.bucket != .overdue {
                VCTonalButton(title: L10n.s("followup.add"), icon: "plus", fullWidth: false, action: actions.add)
            }
        }
    }
}
