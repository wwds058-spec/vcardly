import SwiftUI
import UserNotifications

struct SettingsState {
    var myCard = MyCard()
    var theme: ThemeMode = .system
    var appLock = false
    var autoLockSeconds = 60
    var notificationsAllowed: Bool?
    var version = ""
}

struct SettingsActions {
    var openMyCard: () -> Void = {}
    var openReports: () -> Void = {}
    var openPrivacy: () -> Void = {}
    var openBackup: () -> Void = {}
    var openTransfer: () -> Void = {}
    var openPro: () -> Void = {}
    var setTheme: (ThemeMode) -> Void = { _ in }
    var setAppLock: (Bool) -> Void = { _ in }
    var setAutoLock: (Int) -> Void = { _ in }
    var requestNotifications: () -> Void = {}
}

struct SettingsScreen: View {
    @Environment(AppEnvironment.self) private var env
    let openMyCard: () -> Void
    let openReports: () -> Void
    let openPrivacy: () -> Void
    let openBackup: () -> Void
    let openTransfer: () -> Void
    let openPro: () -> Void
    @State private var notificationsAllowed: Bool?
    @State private var lockUnavailable = false

    var body: some View {
        SettingsContent(state: SettingsState(
            myCard: env.prefs.myCard, theme: env.prefs.themeMode, appLock: env.prefs.appLockEnabled,
            autoLockSeconds: env.prefs.autoLockSeconds, notificationsAllowed: notificationsAllowed,
            version: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        ), actions: SettingsActions(
            openMyCard: openMyCard, openReports: openReports, openPrivacy: openPrivacy, openBackup: openBackup, openTransfer: openTransfer, openPro: openPro,
            setTheme: { env.prefs.setThemeMode($0) },
            setAppLock: { on in
                Task {
                    // Turning the lock on or off both require proving it is the owner.
                    guard env.lock.deviceCanAuthenticate else { lockUnavailable = true; return }
                    if await env.lock.authenticate(reason: L10n.s("settings.lock_confirm")) {
                        env.prefs.setAppLockEnabled(on)
                        env.lock.unlock()
                    }
                }
            },
            setAutoLock: { env.prefs.setAutoLockSeconds($0) },
            requestNotifications: {
                Task {
                    if notificationsAllowed == false {
                        if let url = URL(string: UIApplication.openSettingsURLString) { await UIApplication.shared.open(url) }
                    } else {
                        notificationsAllowed = await env.reminders.requestPermission()
                        env.followUps.rescheduleAll()
                    }
                }
            }
        ))
        .alert(L10n.s("settings.lock_unavailable.title"), isPresented: $lockUnavailable) {
            Button(L10n.s("common.ok")) {}
        } message: {
            Text(L10n.s("settings.lock_unavailable.message"))
        }
        .task { await refreshNotifications() }
    }

    private func refreshNotifications() async {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        notificationsAllowed = switch s.authorizationStatus {
        case .authorized, .provisional, .ephemeral: true
        case .denied: false
        default: nil
        }
    }
}

/// Stateless settings (used directly by screenshot tests).
struct SettingsContent: View {
    let state: SettingsState
    let actions: SettingsActions
    static let autoLockOptions = [0, 30, 60, 300, 900]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.s("nav.settings")).font(VCFont.headlineMedium).foregroundStyle(VC.onSurface).accessibilityAddTraits(.isHeader)
                    .padding(.top, 12)
                profile
                VCOverline(text: L10n.s("settings.section.general")).padding(.top, 6)
                VStack(spacing: 0) {
                    VCNavigationRow(symbol: "chart.bar.fill", tone: VC.blue, title: L10n.s("reports.title"), subtitle: L10n.s("settings.reports_hint"), action: actions.openReports)
                        .accessibilityIdentifier("settings.reports")
                    divider
                    themeRow
                    divider
                    notificationsRow
                }
                .vcCard(padding: 4)

                VCOverline(text: L10n.s("settings.section.security")).padding(.top, 6)
                VStack(spacing: 0) {
                    HStack(spacing: 14) {
                        VCIconBadge(symbol: "faceid", tone: VC.mint, size: 40, solid: true)
                        Toggle(isOn: Binding(get: { state.appLock }, set: actions.setAppLock)) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L10n.s("settings.app_lock")).font(VCFont.titleSmall)
                                Text(L10n.s("settings.app_lock_hint")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                            }
                        }
                        .tint(VC.primary)
                    }
                    .padding(.horizontal, VC.cardPadding).padding(.vertical, 12)
                    if state.appLock {
                        divider
                        HStack(spacing: 14) {
                            VCIconBadge(symbol: "timer", tone: VC.navy, size: 40, solid: true)
                            Text(L10n.s("settings.auto_lock")).font(VCFont.titleSmall)
                            Spacer()
                            Picker(L10n.s("settings.auto_lock"), selection: Binding(get: { state.autoLockSeconds }, set: actions.setAutoLock)) {
                                ForEach(Self.autoLockOptions, id: \.self) { Text(Self.autoLockLabel($0)).tag($0) }
                            }
                            .tint(VC.primary)
                        }
                        .padding(.horizontal, VC.cardPadding).padding(.vertical, 10)
                    }
                    divider
                    VCNavigationRow(symbol: "hand.raised.fill", tone: VC.lavender, title: L10n.s("privacy.title"), subtitle: L10n.s("settings.privacy_hint"), action: actions.openPrivacy)
                }
                .vcCard(padding: 4)

                VCOverline(text: L10n.s("settings.section.data")).padding(.top, 6)
                VStack(alignment: .leading, spacing: 0) {
                    VCNavigationRow(symbol: "externaldrive.fill", tone: VC.orange, title: L10n.s("settings.backup"),
                                    subtitle: L10n.s("settings.backup_hint"), action: actions.openBackup)
                        .accessibilityIdentifier("settings.backup")
                    divider
                    VCNavigationRow(symbol: "arrow.left.arrow.right", tone: VC.blue, title: L10n.s("transfer.title"),
                                    subtitle: L10n.s("settings.transfer_hint"), action: actions.openTransfer)
                }
                .vcCard(padding: 4)

                Button(action: actions.openPro) {
                    HStack(spacing: 14) {
                        Image(systemName: "crown.fill").font(.system(size: 20)).foregroundStyle(.white)
                            .frame(width: 44, height: 44).background(.white.opacity(0.18), in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L10n.s("settings.pro_banner_title")).font(VCFont.titleSmall)
                            Text(L10n.s("settings.pro_banner_message")).font(VCFont.bodySmall).opacity(0.9)
                        }
                        Spacer()
                        Image(systemName: "chevron.forward")
                    }
                    .foregroundStyle(.white)
                    .padding(16)
                    .background(LinearGradient(colors: VC.gradientPurple, startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: VC.cardRadius, style: .continuous))
                }
                .buttonStyle(PressScaleStyle())
                .padding(.top, 6)

                if !state.version.isEmpty {
                    Text(L10n.s("settings.version", state.version)).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                        .frame(maxWidth: .infinity).padding(.top, 8)
                }
            }
            .padding(.horizontal, VC.screen).padding(.bottom, 32)
        }
        .background(VC.background)
    }

    private var divider: some View { Rectangle().fill(VC.outlineVariant).frame(height: 1).padding(.leading, 70) }

    private var profile: some View {
        Button(action: actions.openMyCard) {
            HStack(spacing: 14) {
                if state.myCard.isEmpty {
                    Image(systemName: "person.fill").font(.system(size: 22)).foregroundStyle(VC.primary).frame(width: 56, height: 56).background(VC.primaryContainer, in: Circle())
                } else {
                    VCAvatar(name: state.myCard.fullName, size: 56)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(state.myCard.isEmpty ? L10n.s("mycard.create") : state.myCard.fullName).font(VCFont.titleMedium).foregroundStyle(VC.onSurface)
                    Text(state.myCard.isEmpty ? L10n.s("settings.mycard_hint") : [state.myCard.jobTitle, state.myCard.company].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).lineLimit(1)
                }
                Spacer()
                Image(systemName: "qrcode").font(.system(size: 20)).foregroundStyle(VC.primary)
            }
            .vcCard()
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
        .accessibilityLabel(L10n.s("mycard.title"))
    }

    private var themeRow: some View {
        HStack(spacing: 14) {
            VCIconBadge(symbol: "moon.fill", tone: VC.lavender, size: 40, solid: true)
            Text(L10n.s("settings.theme")).font(VCFont.titleSmall)
            Spacer()
            Picker(L10n.s("settings.theme"), selection: Binding(get: { state.theme }, set: actions.setTheme)) {
                ForEach(ThemeMode.allCases, id: \.self) { Text(L10n.s("theme.\($0.rawValue)")).tag($0) }
            }
            .tint(VC.primary)
        }
        .padding(.horizontal, VC.cardPadding).padding(.vertical, 10)
    }

    private var notificationsRow: some View {
        VCNavigationRow(symbol: "bell.fill", tone: VC.orange, title: L10n.s("settings.notifications"),
                        subtitle: L10n.s(state.notificationsAllowed == true ? "settings.notifications_on" : (state.notificationsAllowed == false ? "settings.notifications_off" : "settings.notifications_ask")),
                        showChevron: state.notificationsAllowed != true,
                        action: state.notificationsAllowed == true ? nil : actions.requestNotifications)
    }

    static func autoLockLabel(_ s: Int) -> String {
        switch s {
        case 0: L10n.s("settings.auto_lock.immediately")
        case let x where x < 60: L10n.plural("settings.auto_lock.seconds", x)
        default: L10n.plural("settings.auto_lock.minutes", s / 60)
        }
    }
}
