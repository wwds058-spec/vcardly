import SwiftUI

/// Destinations pushed on a tab's navigation stack. Screens never navigate themselves; they call closures and the shell
/// decides (the same rule as Android's AppRoot).
enum Route: Hashable {
    case contact(UUID)
    case editContact(UUID?)
    case followUp(UUID?, contactId: UUID?)
    case myCard
    case editMyCard
    case search
    case share(UUID)
    case privacy
    case pro
    case reports
}

enum Tab: Int, CaseIterable { case home, contacts, followUps, settings }

struct RootView: View {
    @Environment(AppEnvironment.self) private var env

    var body: some View {
        ZStack {
            if env.prefs.onboardingCompleted {
                MainShell()
            } else {
                OnboardingView { env.prefs.setOnboardingCompleted(true) }
            }
            if env.prefs.appLockEnabled && env.lock.locked {
                LockView().transition(.opacity)
            }
        }
        .onAppear { if !env.prefs.appLockEnabled { env.lock.unlock() } }
    }
}

/// Four top-level tabs plus a raised centre Scan action (an action, not a fifth destination).
struct MainShell: View {
    @Environment(AppEnvironment.self) private var env
    @State private var tab: Tab = .home
    @State private var paths: [Tab: NavigationPath] = [:]
    @State private var scanning = false

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                ForEach(Tab.allCases, id: \.self) { t in
                    stack(for: t).opacity(tab == t ? 1 : 0).allowsHitTesting(tab == t)
                        .accessibilityHidden(tab != t)
                }
            }
            VCTabBar(selected: $tab, onScan: { scanning = true })
        }
        .ignoresSafeArea(.keyboard)
        .fullScreenCover(isPresented: $scanning) {
            ScanFlowView { savedId in
                scanning = false
                if let savedId {
                    tab = .contacts
                    paths[.contacts] = NavigationPath([Route.contact(savedId)])
                }
            }
            .environment(env)
        }
    }

    private func binding(_ t: Tab) -> Binding<NavigationPath> {
        Binding(get: { paths[t] ?? NavigationPath() }, set: { paths[t] = $0 })
    }

    private func push(_ t: Tab, _ r: Route) { paths[t, default: NavigationPath()].append(r) }

    @ViewBuilder
    private func stack(for t: Tab) -> some View {
        NavigationStack(path: binding(t)) {
            Group {
                switch t {
                case .home:
                    HomeScreen(
                        openSearch: { push(.home, .search) },
                        openContacts: { tab = .contacts },
                        openFollowUps: { tab = .followUps },
                        openContact: { push(.home, .contact($0)) },
                        openFollowUp: { push(.home, .followUp($0, contactId: nil)) },
                        scan: { scanning = true },
                        addContact: { push(.home, .editContact(nil)) },
                        openMyCard: { push(.home, .myCard) },
                        addFollowUp: { push(.home, .followUp(nil, contactId: nil)) }
                    )
                case .contacts:
                    ContactsScreen(
                        openContact: { push(.contacts, .contact($0)) },
                        addContact: { push(.contacts, .editContact(nil)) },
                        scan: { scanning = true }
                    )
                case .followUps:
                    FollowUpsScreen(open: { push(.followUps, .followUp($0, contactId: nil)) }, add: { push(.followUps, .followUp(nil, contactId: nil)) })
                case .settings:
                    SettingsScreen(openMyCard: { push(.settings, .myCard) }, openReports: { push(.settings, .reports) }, openPrivacy: { push(.settings, .privacy) }, openPro: { push(.settings, .pro) })
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                destination(route, in: t)
            }
        }
    }

    @ViewBuilder
    private func destination(_ route: Route, in t: Tab) -> some View {
        switch route {
        case .contact(let id):
            ContactDetailScreen(
                contactId: id,
                edit: { push(t, .editContact(id)) },
                share: { push(t, .share(id)) },
                addFollowUp: { push(t, .followUp(nil, contactId: id)) },
                openFollowUp: { push(t, .followUp($0, contactId: nil)) },
                deleted: { paths[t]?.removeLast() }
            )
        case .editContact(let id):
            ContactEditScreen(contactId: id, draft: nil, done: { savedId, wasNew in
                paths[t]?.removeLast()
                if wasNew, let savedId { push(t, .contact(savedId)) }
            })
        case .followUp(let id, let contactId):
            FollowUpEditScreen(followUpId: id, presetContactId: contactId, done: { paths[t]?.removeLast() })
        case .myCard:
            MyCardScreen(edit: { push(t, .editMyCard) })
        case .editMyCard:
            MyCardEditScreen(done: { paths[t]?.removeLast() })
        case .search:
            SearchScreen(openContact: { push(t, .contact($0)) }, openFollowUp: { push(t, .followUp($0, contactId: nil)) })
        case .share(let id):
            ContactShareScreen(contactId: id)
        case .privacy:
            PrivacyScreen()
        case .pro:
            ProScreen()
        case .reports:
            ReportsScreen()
        }
    }
}

/// Bottom bar with four tabs and a raised circular Scan button in the middle.
struct VCTabBar: View {
    @Binding var selected: Tab
    let onScan: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            item(.home, "house", "house.fill", "nav.home")
            item(.contacts, "person.2", "person.2.fill", "nav.contacts")
            Color.clear.frame(maxWidth: .infinity)
            item(.followUps, "calendar.badge.checkmark", "calendar.badge.checkmark", "nav.followups")
            item(.settings, "gearshape", "gearshape.fill", "nav.settings")
        }
        .padding(.horizontal, 6)
        .frame(height: 64)
        .background(alignment: .top) {
            UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24)
                .fill(VC.card)
                .shadow(color: VC.shadow.opacity(0.12), radius: 12, y: -2)
                .ignoresSafeArea(edges: .bottom)
        }
        .overlay(alignment: .top) {
            Button(action: onScan) {
                Image(systemName: "doc.viewfinder")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(LinearGradient(colors: VC.gradientBlue, startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                    .padding(4)
                    .background(VC.card, in: Circle())
                    .shadow(color: VC.gradientBlue[1].opacity(0.4), radius: 10, y: 4)
            }
            .buttonStyle(PressScaleStyle(scale: 0.92))
            .offset(y: -22)
            .accessibilityLabel(L10n.s("scan.action"))
        }
    }

    private func item(_ tab: Tab, _ icon: String, _ selectedIcon: String, _ label: String) -> some View {
        let isOn = selected == tab
        return Button { selected = tab } label: {
            VStack(spacing: 2) {
                Image(systemName: isOn ? selectedIcon : icon)
                    .font(.system(size: 18, weight: .semibold))
                    .frame(width: 44, height: 28)
                    .background(isOn ? VC.primaryContainer : .clear, in: Capsule())
                Text(L10n.s(label)).font(VCFont.labelSmall).lineLimit(1)
            }
            .foregroundStyle(isOn ? VC.primary : VC.onSurfaceVariant)
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? [.isSelected] : [])
    }
}
