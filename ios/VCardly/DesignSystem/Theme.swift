import SwiftUI
import UIKit

// VCardly design tokens for iOS, the same values as Android's core/designsystem/theme (Color.kt, Type.kt, Spacing.kt).
// Light: clean white/soft surfaces. Dark: deep navy surfaces (designed, not an inversion).

extension UIColor {
    convenience init(argb: UInt32) {
        self.init(red: CGFloat((argb >> 16) & 0xFF) / 255, green: CGFloat((argb >> 8) & 0xFF) / 255,
                  blue: CGFloat(argb & 0xFF) / 255, alpha: CGFloat((argb >> 24) & 0xFF) / 255)
    }
}

extension Color {
    init(argb: UInt32) { self.init(uiColor: UIColor(argb: argb)) }

    /// A colour that follows the light/dark appearance.
    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(argb: dark) : UIColor(argb: light) })
    }
}

/// One accent family: tinted container, ink for text on it, bright accent for icons and charts.
struct Tone {
    let container: Color
    let content: Color
    let accent: Color
}

enum VC {
    // Surfaces and text
    static let background = Color.dynamic(0xFFF5F7FC, 0xFF081230)
    static let card = Color.dynamic(0xFFFFFFFF, 0xFF111F45)
    static let cardHigh = Color.dynamic(0xFFF0F3FA, 0xFF17264F)
    static let field = Color.dynamic(0xFFFFFFFF, 0xFF111F45)
    static let onSurface = Color.dynamic(0xFF0F172A, 0xFFE7ECF8)
    static let onSurfaceVariant = Color.dynamic(0xFF545E73, 0xFFA9B4D0)
    static let outline = Color.dynamic(0xFF8A93A8, 0xFF6B78A0)
    static let outlineVariant = Color.dynamic(0xFFE1E6F0, 0xFF223463)
    static let primary = Color.dynamic(0xFF2456F0, 0xFF8FAEFF)
    static let onPrimary = Color.dynamic(0xFFFFFFFF, 0xFF06194F)
    static let primaryContainer = Color.dynamic(0xFFE5ECFF, 0xFF1E3B95)
    static let onPrimaryContainer = Color.dynamic(0xFF0B2A86, 0xFFDCE5FF)
    static let error = Color.dynamic(0xFFC62042, 0xFFFF8FA5)
    /// Strong call to action (Get Started, Next, Save).
    static let cta = Color.dynamic(0xFF14276B, 0xFF2F5FEA)
    static let favorite = Color.dynamic(0xFFE09400, 0xFFFFC233)
    static let whatsapp = Color.dynamic(0xFF128C4B, 0xFF3FD48F)
    static let shadow = Color(argb: 0xFF1B3A8A)

    // Tone families
    static let blue = Tone(container: .dynamic(0xFFEAF0FF, 0xFF14275C), content: .dynamic(0xFF1D46C7, 0xFFB9CBFF), accent: .dynamic(0xFF2F68FF, 0xFF7FA2FF))
    static let rose = Tone(container: .dynamic(0xFFFFEDF1, 0xFF3A1730), content: .dynamic(0xFFB4163C, 0xFFFFB3C3), accent: .dynamic(0xFFF0466B, 0xFFFF7896))
    static let mint = Tone(container: .dynamic(0xFFE6F7EE, 0xFF0F3533), content: .dynamic(0xFF0E7342, 0xFF9EEBC4), accent: .dynamic(0xFF19B36B, 0xFF3FD48F))
    static let orange = Tone(container: .dynamic(0xFFFFF0E5, 0xFF3B2419), content: .dynamic(0xFFAD4A08, 0xFFFFC79E), accent: .dynamic(0xFFF27A1A, 0xFFFF9C52))
    static let lavender = Tone(container: .dynamic(0xFFF0EBFF, 0xFF261E55), content: .dynamic(0xFF5235C9, 0xFFD3C6FF), accent: .dynamic(0xFF7B5CF5, 0xFFA48CFF))
    static let navy = Tone(container: .dynamic(0xFFE6EAF5, 0xFF17264F), content: .dynamic(0xFF14276B, 0xFFDCE5FF), accent: .dynamic(0xFF14276B, 0xFF8FAEFF))

    // Gradients (white text on every stop is contrast-checked on Android, same values)
    static let gradientBlue = [Color(argb: 0xFF2F63F5), Color(argb: 0xFF1D46C7)]
    static let gradientIndigo = [Color(argb: 0xFF3A5BE0), Color(argb: 0xFF223BA8)]
    static let gradientPurple = [Color(argb: 0xFF7A55EE), Color(argb: 0xFF5530C4)]
    static let gradientOrange = [Color(argb: 0xFFC9480A), Color(argb: 0xFFA83A05)]
    static let cardHero = [Color(argb: 0xFF3B6FFF), Color(argb: 0xFF2747C9), Color(argb: 0xFF5B3CD6)]
    static let navyDeep = Color(argb: 0xFF0B1638)
    static let navyBrand = Color(argb: 0xFF14276B)

    // Spacing (4pt grid)
    static let screen: CGFloat = 20
    static let cardPadding: CGFloat = 16
    static let cardRadius: CGFloat = 20
    static let tileRadius: CGFloat = 16
}

/// Plus Jakarta Sans (bundled, SIL OFL). `relativeTo` makes every style follow Dynamic Type.
enum VCFont {
    private static func f(_ weight: String, _ size: CGFloat, _ style: Font.TextStyle) -> Font {
        .custom("PlusJakartaSans-\(weight)", size: size, relativeTo: style)
    }

    static let display = f("Bold", 34, .largeTitle)
    static let headlineLarge = f("Bold", 30, .largeTitle)
    static let headlineMedium = f("Bold", 26, .title)
    static let headlineSmall = f("Bold", 22, .title2)
    static let titleLarge = f("SemiBold", 20, .title3)
    static let titleMedium = f("SemiBold", 16, .headline)
    static let titleSmall = f("SemiBold", 14, .subheadline)
    static let bodyLarge = f("Regular", 16, .body)
    static let bodyMedium = f("Regular", 14, .callout)
    static let bodySmall = f("Regular", 12, .footnote)
    static let labelLarge = f("SemiBold", 14, .subheadline)
    static let labelMedium = f("Medium", 12, .caption)
    static let labelSmall = f("Medium", 11, .caption2)
    static let stat = f("Bold", 28, .title)
    static let overline = f("SemiBold", 12, .caption)
}

extension View {
    /// The standard elevated card: rounded, soft brand-tinted shadow in light mode, hairline border in dark mode.
    func vcCard(padding: CGFloat = VC.cardPadding, color: Color = VC.card, radius: CGFloat = VC.cardRadius) -> some View {
        modifier(CardModifier(padding: padding, color: color, radius: radius))
    }
}

private struct CardModifier: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    let padding: CGFloat
    let color: Color
    let radius: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(color, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay {
                if scheme == .dark { RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(VC.outlineVariant.opacity(0.6), lineWidth: 1) }
            }
            .shadow(color: scheme == .dark ? .clear : VC.shadow.opacity(0.08), radius: 10, x: 0, y: 4)
    }
}
