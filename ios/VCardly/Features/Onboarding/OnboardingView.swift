import SwiftUI

struct OnboardingView: View {
    let onFinish: () -> Void
    @State var page = 0

    private let pages: [(String, Tone, String)] = [
        ("doc.viewfinder", VC.blue, "onboarding.1"),
        ("lock.shield.fill", VC.mint, "onboarding.2"),
        ("bell.badge.fill", VC.orange, "onboarding.3"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VCLogo()
                Spacer()
                if page < pages.count - 1 {
                    Button(L10n.s("onboarding.skip"), action: onFinish).font(VCFont.labelLarge).frame(minHeight: 44)
                }
            }
            .padding(.horizontal, VC.screen).padding(.top, 8)

            TabView(selection: $page) {
                ForEach(pages.indices, id: \.self) { i in
                    VStack(spacing: 0) {
                        Spacer()
                        hero(i)
                        Text(L10n.s("\(pages[i].2).title")).font(VCFont.headlineMedium).foregroundStyle(VC.onSurface)
                            .multilineTextAlignment(.center).padding(.top, 36)
                        Text(L10n.s("\(pages[i].2).body")).font(VCFont.bodyLarge).foregroundStyle(VC.onSurfaceVariant)
                            .multilineTextAlignment(.center).padding(.top, 12)
                        Spacer()
                    }
                    .padding(.horizontal, 28)
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut, value: page)

            HStack(spacing: 8) {
                ForEach(pages.indices, id: \.self) { i in
                    Capsule().fill(i == page ? VC.primary : VC.outlineVariant).frame(width: i == page ? 24 : 8, height: 8)
                }
            }
            .animation(.spring(response: 0.3), value: page)
            .accessibilityElement()
            .accessibilityLabel(L10n.s("onboarding.page_indicator", page + 1, pages.count))
            .padding(.bottom, 24)

            VCPrimaryButton(title: L10n.s(page == pages.count - 1 ? "onboarding.get_started" : "onboarding.next"), trailingIcon: "arrow.right") {
                if page < pages.count - 1 { withAnimation { page += 1 } } else { onFinish() }
            }
            .padding(.horizontal, VC.screen).padding(.bottom, 16)
        }
        .background(VC.background)
    }

    @ViewBuilder
    private func hero(_ i: Int) -> some View {
        let (symbol, tone, _) = pages[i]
        ZStack {
            Circle().fill(tone.container).frame(width: 260, height: 260)
            Circle().fill(tone.accent.opacity(0.18)).frame(width: 40, height: 40).offset(x: 110, y: -90)
            Circle().fill(tone.accent.opacity(0.25)).frame(width: 22, height: 22).offset(x: -120, y: 80)
            if i == 0 {
                BusinessCardArt(name: L10n.s("onboarding.sample_name"), jobTitle: L10n.s("onboarding.sample_title"), company: L10n.s("onboarding.sample_company"),
                                phone: "", email: "", website: "")
                    .frame(width: 230).rotationEffect(.degrees(-8)).shadow(color: VC.shadow.opacity(0.3), radius: 16, y: 10)
                Image(systemName: symbol).font(.system(size: 26, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 60, height: 60).background(LinearGradient(colors: VC.gradientBlue, startPoint: .top, endPoint: .bottom), in: Circle())
                    .offset(x: 90, y: 70)
            } else {
                Image(systemName: symbol).font(.system(size: 64, weight: .semibold)).foregroundStyle(tone.accent)
                    .frame(width: 140, height: 140).background(VC.card, in: Circle())
                    .shadow(color: VC.shadow.opacity(0.12), radius: 16, y: 8)
            }
        }
        .frame(height: 280)
        .accessibilityHidden(true)
    }
}

struct LockView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var failed = false
    @State private var busy = false

    var body: some View {
        LockContent(failed: failed, busy: busy, unlock: unlock)
            .task { unlock() }
    }

    private func unlock() {
        guard !busy else { return }
        busy = true
        Task {
            if await env.lock.authenticate(reason: L10n.s("lock.prompt_title")) {
                withAnimation { env.lock.unlock() }
                failed = false
            } else {
                failed = true
            }
            busy = false
        }
    }
}

/// Stateless lock screen (used directly by screenshot tests).
struct LockContent: View {
    let failed: Bool
    var busy = false
    let unlock: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VCMark(size: 64)
            Text(L10n.s("lock.title")).font(VCFont.headlineSmall).foregroundStyle(.white).padding(.top, 28)
            Text(L10n.s("lock.message")).font(VCFont.bodyMedium).foregroundStyle(.white.opacity(0.8)).multilineTextAlignment(.center).padding(.top, 8)
            if failed {
                Text(L10n.s("lock.error")).font(VCFont.bodySmall).foregroundStyle(Color(argb: 0xFFFF8FA5)).multilineTextAlignment(.center).padding(.top, 14)
            }
            Spacer()
            VCPrimaryButton(title: L10n.s("lock.unlock"), icon: "faceid", loading: busy, color: Color(argb: 0xFF2F5FEA), action: unlock)
                .padding(.bottom, 24)
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(LinearGradient(colors: [VC.navyDeep, Color(argb: 0xFF1B2D6B)], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    }
}
