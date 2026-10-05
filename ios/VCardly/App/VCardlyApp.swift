import SwiftUI

@main
struct VCardlyApp: App {
    @State private var env = AppEnvironment(inMemory: ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil)
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

extension ThemeMode {
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}
