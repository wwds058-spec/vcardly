import SwiftUI

struct SearchScreen: View {
    @Environment(AppEnvironment.self) private var env
    let openContact: (UUID) -> Void
    let openFollowUp: (UUID) -> Void
    @State private var query = ""
    @State private var contacts: [ContactDetails] = []
    @State private var followUps: [FollowUpWithContact] = []
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            VCSearchField(text: $query, placeholder: L10n.s("home.search"))
                .focused($focused)
                .padding(.horizontal, VC.screen).padding(.vertical, 10)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if query.trimmed.isEmpty {
                        VCEmptyState(symbol: "magnifyingglass", title: L10n.s("search.start.title"), message: L10n.s("search.start.message")).padding(.top, 40)
                    } else if contacts.isEmpty && followUps.isEmpty {
                        VCEmptyState(symbol: "magnifyingglass", title: L10n.s("contacts.no_results.title"), message: L10n.s("search.no_results.message")).padding(.top, 40)
                    } else {
                        if !contacts.isEmpty {
                            VCSectionHeader(title: L10n.s("nav.contacts"))
                            ForEach(contacts) { d in
                                ContactCard(details: d, showActions: false, onTap: { openContact(d.id) }, onToggleFavorite: { env.contacts.setFavorite(d.id, !d.contact.isFavorite) })
                            }
                        }
                        if !followUps.isEmpty {
                            VCSectionHeader(title: L10n.s("nav.followups"))
                            ForEach(followUps) { f in FollowUpCard(item: f, onTap: { openFollowUp(f.id) }) }
                        }
                    }
                }
                .padding(.horizontal, VC.screen).padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(VC.background)
        .navigationTitle(L10n.s("search.title"))
        .navigationBarTitleDisplayMode(.inline)
        .task(id: "\(query)|\(env.revision.value)") { run() }
        .onAppear { focused = true }
    }

    private func run() {
        let q = query.trimmed
        guard !q.isEmpty else { contacts = []; followUps = []; return }
        contacts = env.contacts.contacts(ContactFilter(query: q))
        let tokens = q.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        followUps = env.followUps.all().filter { item in
            let hay = [item.followUp.title, item.followUp.notes, item.contactName, item.contactCompany].joined(separator: " ").lowercased()
            return tokens.allSatisfy { hay.contains($0) }
        }
    }
}

struct PrivacyScreen: View {
    private let sections: [(String, String, Tone)] = [
        ("iphone", "privacy.stays", VC.blue),
        ("person.crop.circle.badge.xmark", "privacy.no_account", VC.lavender),
        ("checkmark.shield.fill", "privacy.permissions", VC.mint),
        ("text.viewfinder", "privacy.ocr", VC.orange),
        ("square.and.arrow.up", "privacy.sharing", VC.blue),
        ("faceid", "privacy.lock", VC.navy),
        ("externaldrive.fill", "privacy.backup", VC.orange),
        ("hand.raised.fill", "privacy.control", VC.rose),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.s("privacy.intro")).font(VCFont.bodyLarge).foregroundStyle(VC.onSurfaceVariant)
                ForEach(sections, id: \.1) { symbol, key, tone in
                    HStack(alignment: .top, spacing: 14) {
                        VCIconBadge(symbol: symbol, tone: tone, size: 40, circle: true)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L10n.s("\(key).title")).font(VCFont.titleSmall).accessibilityAddTraits(.isHeader)
                            Text(L10n.s("\(key).body")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .vcCard()
                }
            }
            .padding(VC.screen)
        }
        .background(VC.background)
        .navigationTitle(L10n.s("privacy.title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Pro on iOS: purchases need App Store Connect products (not set up), so this says so plainly and sells nothing.
struct ProScreen: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                VStack(spacing: 10) {
                    Image(systemName: "crown.fill").font(.system(size: 34)).foregroundStyle(.white)
                        .frame(width: 76, height: 76).background(.white.opacity(0.18), in: Circle())
                    Text(L10n.s("pro.hero_title")).font(VCFont.headlineSmall).foregroundStyle(.white)
                    Text(L10n.s("pro.intro")).font(VCFont.bodyMedium).foregroundStyle(.white.opacity(0.9)).multilineTextAlignment(.center)
                }
                .padding(24).frame(maxWidth: .infinity)
                .background(LinearGradient(colors: VC.gradientPurple, startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 24, style: .continuous))

                VStack(spacing: 0) {
                    feature("infinity", "pro.feature_scans", VC.blue)
                    feature("nosign", "pro.feature_no_ads", VC.orange)
                }
                .vcCard(padding: 4)

                VCNotice(text: L10n.s("pro.not_configured_ios"), tone: VC.orange, symbol: "info.circle.fill")
                VCPrimaryButton(title: L10n.s("pro.not_available"), icon: "lock.fill") {}.disabled(true)
            }
            .padding(VC.screen)
        }
        .background(VC.background)
        .navigationTitle(L10n.s("pro.title"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func feature(_ symbol: String, _ key: String, _ tone: Tone) -> some View {
        HStack(spacing: 14) {
            VCIconBadge(symbol: symbol, tone: tone, size: 40, circle: true)
            Text(L10n.s(key)).font(VCFont.titleSmall)
            Spacer()
            Image(systemName: "checkmark").foregroundStyle(VC.mint.accent)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
    }
}
