import SwiftUI

@main
struct VCardlyApp: App {
    @State private var env = VCardlyApp.makeEnvironment()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(env)
                .preferredColorScheme(env.prefs.themeMode.colorScheme)
                .tint(VC.primary)
                .task { env.followUps.rescheduleAll() }
        }
        .onChange(of: phase) { _, newPhase in
            switch newPhase {
            case .background: env.lock.didEnterBackground()
            case .active: env.lock.willEnterForeground(enabled: env.prefs.appLockEnabled, timeoutSeconds: env.prefs.autoLockSeconds)
            default: break
            }
        }
    }
}

extension VCardlyApp {
    @MainActor
    static func makeEnvironment() -> AppEnvironment {
        #if DEBUG
        if UITestSeed.isActive { return UITestSeed.makeEnvironment() }
        #endif
        // Unit tests run inside the app: give them a throwaway in-memory store.
        return AppEnvironment(inMemory: ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil)
    }
}

extension ThemeMode {
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}
