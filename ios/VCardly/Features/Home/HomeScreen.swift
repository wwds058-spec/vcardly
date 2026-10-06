import SwiftUI

enum Greeting { case morning, afternoon, evening
    static func now(_ date: Date = Date()) -> Greeting {
        let h = Calendar.current.component(.hour, from: date)
        return h < 12 ? .morning : (h < 17 ? .afternoon : .evening)
    }
    var key: String { switch self { case .morning: "home.greeting.morning"; case .afternoon: "home.greeting.afternoon"; case .evening: "home.greeting.evening" } }
}

/// Everything on Home comes from the database and preferences; nothing is hard-coded.
struct HomeState {
    var greeting: Greeting = .morning
    var firstName = ""
    var myCardName = ""
    var total = 0
    var addedThisMonth = 0
    var favorites = 0
    var counts = FollowUpCounts()
    var upcoming: [FollowUpWithContact] = []
    var recent: [ContactDetails] = []
    var isEmpty: Bool { total == 0 }
}

struct HomeActions {
    var openSearch: () -> Void = {}
    var openContacts: () -> Void = {}
    var openFollowUps: () -> Void = {}
    var openContact: (UUID) -> Void = { _ in }
    var openFollowUp: (UUID) -> Void = { _ in }
    var scan: () -> Void = {}
    var addContact: () -> Void = {}
    var openMyCard: () -> Void = {}
    var addFollowUp: () -> Void = {}
    var complete: (UUID) -> Void = { _ in }
}

struct HomeScreen: View {
    @Environment(AppEnvironment.self) private var env
    let openSearch: () -> Void, openContacts: () -> Void, openFollowUps: () -> Void
    let openContact: (UUID) -> Void, openFollowUp: (UUID) -> Void
    let scan: () -> Void, addContact: () -> Void, openMyCard: () -> Void, addFollowUp: () -> Void
    @State private var state = HomeState()

    var body: some View {
        HomeContent(state: state, actions: HomeActions(
            openSearch: openSearch, openContacts: openContacts, openFollowUps: openFollowUps, openContact: openContact,
            openFollowUp: openFollowUp, scan: scan, addContact: addContact, openMyCard: openMyCard, addFollowUp: addFollowUp,
            complete: { env.followUps.setStatus($0, .completed) }
        ))
        .task(id: env.revision.value) { load() }
        .onChange(of: env.prefs.myCard) { _, _ in load() }
    }

    private func load() {
        let all = env.contacts.contacts()
        let monthStart = Calendar.current.dateInterval(of: .month, for: Date())?.start ?? Date()
        let next = env.followUps.bucket(.overdue) + env.followUps.bucket(.today) + env.followUps.bucket(.upcoming)
        state = HomeState(
            greeting: .now(),
            firstName: String(env.prefs.myCard.fullName.trimmed.split(separator: " ").first ?? ""),
            myCardName: env.prefs.myCard.fullName,
            total: all.count,
            addedThisMonth: all.filter { $0.contact.createdAt >= monthStart }.count,
            favorites: all.filter(\.contact.isFavorite).count,
            counts: env.followUps.counts(),
            upcoming: Array(next.prefix(3)),
            recent: env.contacts.recent(limit: 8)
        )
    }
}

/// Stateless Home (used directly by screenshot tests).
struct HomeContent: View {
    let state: HomeState
    let actions: HomeActions
    @State private var appeared = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                VCSearchLauncher(placeholder: L10n.s("home.search"), action: actions.openSearch)
                stats
                    .opacity(appeared ? 1 : 0)
                    .offset(y: appeared ? 0 : 16)
                VCSectionHeader(title: L10n.s("home.quick_actions"))
                VCAdaptiveGrid {
                    VCActionTile(title: L10n.s("scan.action"), symbol: "doc.viewfinder", gradient: VC.gradientBlue, action: actions.scan)
                    VCActionTile(title: L10n.s("contact.add"), symbol: "person.badge.plus", gradient: VC.gradientIndigo, action: actions.addContact)
                    VCActionTile(title: L10n.s("mycard.title"), symbol: "person.text.rectangle", gradient: VC.gradientPurple, action: actions.openMyCard)
                    VCActionTile(title: L10n.s("followup.add"), symbol: "calendar.badge.plus", gradient: VC.gradientOrange, action: actions.addFollowUp)
                }
                if state.isEmpty {
                    emptyCard
                } else {
                    VCSectionHeader(title: L10n.s("home.upcoming"), actionTitle: L10n.s("common.see_all"), action: actions.openFollowUps)
                    if state.upcoming.isEmpty {
                        Button(action: actions.addFollowUp) {
                            HStack(spacing: 14) {
                                VCIconBadge(symbol: "calendar.badge.checkmark", tone: VC.mint, size: 46, circle: true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L10n.s("followup.none.title")).font(VCFont.titleSmall).foregroundStyle(VC.onSurface)
                                    Text(L10n.s("followup.none.message")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                                }
                                Spacer()
                            }
                            .vcCard()
                        }
                        .buttonStyle(PressScaleStyle(scale: 0.98))
                    } else {
                        ForEach(state.upcoming) { item in
                            FollowUpCard(item: item, onTap: { actions.openFollowUp(item.id) }, onToggle: { actions.complete(item.id) })
                        }
                    }
                    if !state.recent.isEmpty {
                        VCSectionHeader(title: L10n.s("home.recent"), actionTitle: L10n.s("common.see_all"), action: actions.openContacts)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(state.recent) { d in ContactMiniCard(details: d) { actions.openContact(d.id) } }
                            }
                            .padding(.vertical, 6)
                        }
                    }
                }
            }
            .padding(.horizontal, VC.screen)
            .padding(.bottom, 32)
        }
        .background(VC.background)
        .onAppear { withAnimation(.easeOut(duration: 0.35)) { appeared = true } }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L10n.s(state.greeting.key)).font(VCFont.titleMedium).foregroundStyle(VC.onSurfaceVariant)
                Text(state.firstName.isEmpty ? L10n.s("home.greeting.no_name") : L10n.s("home.greeting.name", state.firstName))
                    .font(VCFont.headlineLarge).foregroundStyle(VC.onSurface).lineLimit(1)
                Text(L10n.s("home.subtitle")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Spacer()
            Button(action: actions.openMyCard) {
                if state.myCardName.isEmpty {
                    Image(systemName: "person.fill").foregroundStyle(VC.primary).frame(width: 52, height: 52).background(VC.primaryContainer, in: Circle())
                } else {
                    VCAvatar(name: state.myCardName, size: 52)
                }
            }
            .accessibilityLabel(L10n.s("mycard.title"))
        }
        .padding(.top, 12)
    }

    private var stats: some View {
        let nf = NumberFormatter()
        func n(_ v: Int) -> String { nf.string(from: NSNumber(value: v)) ?? "\(v)" }
        return VCAdaptiveGrid {
            VCStatCard(value: n(state.total), label: L10n.s("home.stat.contacts"), symbol: "person.2.fill", tone: VC.blue,
                       supporting: state.addedThisMonth > 0 ? L10n.s("home.stat.this_month", n(state.addedThisMonth)) : nil, action: actions.openContacts)
            VCStatCard(value: n(state.favorites), label: L10n.s("home.stat.favorites"), symbol: "star.fill", tone: VC.rose, action: actions.openContacts)
            VCStatCard(value: n(state.counts.pending), label: L10n.s("nav.followups"), symbol: "calendar.badge.checkmark", tone: VC.mint,
                       supporting: state.counts.today > 0 ? L10n.s("home.stat.due_today", n(state.counts.today)) : nil, action: actions.openFollowUps)
            VCStatCard(value: n(state.counts.overdue), label: L10n.s("followup.bucket.overdue"), symbol: "bell.badge.fill", tone: VC.orange, action: actions.openFollowUps)
        }
    }

    private var emptyCard: some View {
        VStack(spacing: 0) {
            VCIllustration(symbol: "doc.viewfinder", tone: VC.blue)
            Text(L10n.s("empty.network.title")).font(VCFont.titleLarge).foregroundStyle(VC.onSurface).multilineTextAlignment(.center).padding(.top, 16)
            Text(L10n.s("empty.network.message")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant).multilineTextAlignment(.center)
                .padding(.top, 6).padding(.bottom, 20)
            VCPrimaryButton(title: L10n.s("empty.network.action"), icon: "doc.viewfinder", action: actions.scan)
        }
        .frame(maxWidth: .infinity)
        .vcCard(padding: 24)
    }
}
