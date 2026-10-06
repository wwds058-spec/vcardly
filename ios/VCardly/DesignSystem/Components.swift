import SwiftUI

// Reusable VCardly components (iOS counterparts of Android's core/designsystem/component).

/// Subtle press feedback for tappable cards and tiles.
struct PressScaleStyle: ButtonStyle {
    var scale: CGFloat = 0.97
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// The one obvious action on a screen: a full pill in the deep brand colour.
struct VCPrimaryButton: View {
    let title: String
    var icon: String?
    var trailingIcon: String?
    var loading = false
    var color: Color = VC.cta
    var foreground: Color = .white
    let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if loading {
                    ProgressView().tint(foreground)
                } else {
                    if let icon { Image(systemName: icon).font(.system(size: 16, weight: .semibold)) }
                    Text(title).font(VCFont.titleSmall)
                    if let trailingIcon { Image(systemName: trailingIcon).font(.system(size: 16, weight: .semibold)) }
                }
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .foregroundStyle(isEnabled ? foreground : VC.onSurfaceVariant)
            .background(isEnabled ? color : VC.onSurface.opacity(0.12), in: Capsule())
            .accessibilityLabel(title)
        }
        .buttonStyle(PressScaleStyle())
        .disabled(loading)
    }
}

/// Outlined pill for secondary actions.
struct VCSecondaryButton: View {
    let title: String
    var icon: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 15, weight: .semibold)) }
                Text(title).font(VCFont.titleSmall)
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .padding(.horizontal, 16)
            .foregroundStyle(VC.onSurface)
            .overlay(Capsule().stroke(VC.outline.opacity(0.6), lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// Soft-blue pill for weighty secondary actions ("Add follow-up", "Edit").
struct VCTonalButton: View {
    let title: String
    var icon: String?
    var fullWidth = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 15, weight: .semibold)) }
                Text(title).font(VCFont.titleSmall)
            }
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: 48)
            .padding(.horizontal, 18)
            .foregroundStyle(VC.onPrimaryContainer)
            .background(VC.primaryContainer, in: Capsule())
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// Tinted rounded square or circle holding an SF Symbol.
struct VCIconBadge: View {
    let symbol: String
    let tone: Tone
    var size: CGFloat = 40
    var circle = false
    var solid = false

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(solid ? Color.white : tone.accent)
            .frame(width: size, height: size)
            .background(solid ? tone.accent : tone.container, in: RoundedRectangle(cornerRadius: circle ? size / 2 : 12, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Dashboard figure on a softly tinted card, read by VoiceOver as one element.
struct VCStatCard: View {
    let value: String
    let label: String
    let symbol: String
    let tone: Tone
    var supporting: String?
    var supportingColor: Color = VC.mint.content
    var action: (() -> Void)?

    var body: some View {
        let content = VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                Text(value).font(VCFont.stat).foregroundStyle(tone.content).lineLimit(1).minimumScaleFactor(0.6)
                Spacer()
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tone.accent)
                    .frame(width: 34, height: 34)
                    .background(VC.card.opacity(0.85), in: Circle())
                    .accessibilityHidden(true)
            }
            Text(label).font(VCFont.bodyMedium).foregroundStyle(VC.onSurface).vcLineLimit(2)
            if let supporting { Text(supporting).font(VCFont.labelMedium).foregroundStyle(supportingColor).vcLineLimit(2) }
        }
        .padding(VC.cardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.container, in: RoundedRectangle(cornerRadius: VC.cardRadius, style: .continuous))
        .accessibilityElement(children: .combine)

        if let action {
            Button(action: action) { content }.buttonStyle(PressScaleStyle())
        } else {
            content
        }
    }
}

/// Gradient quick-action tile.
struct VCActionTile: View {
    let title: String
    let symbol: String
    let gradient: [Color]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: symbol)
                    .font(.system(size: 16, weight: .semibold))
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .accessibilityHidden(true)
                Text(title).font(VCFont.titleSmall).vcLineLimit(2).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 60)
            .background(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: VC.tileRadius, style: .continuous))
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// Section title with an optional trailing action ("See all"), marked as a header for VoiceOver.
struct VCSectionHeader: View {
    let title: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack {
            Text(title).font(VCFont.titleMedium).foregroundStyle(VC.onSurface).accessibilityAddTraits(.isHeader)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle, action: action).font(VCFont.labelLarge).foregroundStyle(VC.primary)
            }
        }
        .frame(minHeight: 36)
    }
}

/// Pill search field.
struct VCSearchField: View {
    @Binding var text: String
    let placeholder: String
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(VC.onSurfaceVariant).accessibilityHidden(true)
            TextField(typeSize <= .large ? placeholder : L10n.s("search.short"), text: $text)
                .font(VCFont.bodyLarge)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(VC.onSurfaceVariant) }
                    .accessibilityLabel(L10n.s("common.clear_search"))
            }
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 52)
        .background(VC.cardHigh, in: Capsule())
    }
}

/// Looks like the search field; tapping opens search (Home).
struct VCSearchLauncher: View {
    let placeholder: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").accessibilityHidden(true)
                Text(placeholder).font(VCFont.bodyLarge)
                Spacer()
            }
            .foregroundStyle(VC.onSurfaceVariant)
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .background(VC.cardHigh, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Two columns of cards, one column at accessibility text sizes so labels never truncate.
struct VCAdaptiveGrid<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder let content: () -> Content
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: spacing, alignment: .top), count: typeSize.isAccessibilitySize ? 1 : 2),
                  spacing: spacing, content: content)
    }
}

/// Selectable pill chip; exposes the selected state to VoiceOver.
struct VCChip: View {
    let title: String
    let selected: Bool
    var symbol: String?
    var dot: Color?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let dot { Circle().fill(selected ? Color.white : dot).frame(width: 8, height: 8) }
                if let symbol { Image(systemName: symbol).font(.system(size: 12, weight: .semibold)) }
                Text(title).font(VCFont.labelLarge).lineLimit(1)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 40)
            .foregroundStyle(selected ? VC.onPrimary : VC.onSurface)
            .background(selected ? VC.primary : VC.card, in: Capsule())
            .overlay(Capsule().stroke(selected ? Color.clear : VC.outlineVariant, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

/// Small static label pill ("Customer", "#VIP", "Rescheduled").
struct VCTag: View {
    let title: String
    let tone: Tone
    var body: some View {
        Text(title).font(VCFont.labelMedium).foregroundStyle(tone.content).vcLineLimit(1)
            .padding(.horizontal, 10).padding(.vertical, 4)
            .background(tone.container, in: Capsule())
    }
}

/// Initials on a soft gradient of the category colour; ink is black or white, whichever has more contrast.
struct VCAvatar: View {
    let name: String
    var argb: UInt32?
    var size: CGFloat = 48

    var body: some View {
        let base = argb ?? 0xFF2456F0
        // Drawn in a Canvas: decorative pixels at a fixed size so they always fit the circle (the name is read elsewhere).
        let initials = Text(Initials.of(name)).font(.custom("PlusJakartaSans-SemiBold", fixedSize: size * 0.36))
            .foregroundColor(Contrast.readableOnIsWhite(base) ? .white : .black)
        Canvas { ctx, s in ctx.draw(initials, at: CGPoint(x: s.width / 2, y: s.height / 2)) }
            .frame(width: size, height: size)
            .background(LinearGradient(colors: [Color(argb: base).opacity(0.85), Color(argb: base)], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
            .accessibilityHidden(true)
    }
}

/// One piece of information: tinted icon, label and value, an optional tap action and a trailing copy action.
struct VCInfoRow: View {
    let symbol: String
    let tone: Tone
    let label: String
    let value: String
    var onTap: (() -> Void)?
    var onCopy: (() -> Void)?
    var copyLabel: String = ""

    var body: some View {
        HStack(spacing: 14) {
            VCIconBadge(symbol: symbol, tone: tone, size: 40, circle: true)
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(VCFont.labelMedium).foregroundStyle(VC.onSurfaceVariant)
                Text(value).font(VCFont.bodyLarge).foregroundStyle(VC.onSurface).vcLineLimit(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { onTap?() }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(onTap != nil ? .isButton : [])
            if let onCopy {
                Button(action: onCopy) { Image(systemName: "doc.on.doc").foregroundStyle(VC.onSurfaceVariant).frame(width: 44, height: 44) }
                    .accessibilityLabel(copyLabel)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
    }
}

/// Inline notice for warnings and confirmations.
struct VCNotice: View {
    let text: String
    let tone: Tone
    let symbol: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).foregroundStyle(tone.accent).accessibilityHidden(true)
            Text(text).font(VCFont.bodyMedium).foregroundStyle(tone.content)
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(tone.container, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Designed empty state: layered illustration, friendly title, short message, optional actions.
struct VCEmptyState<Actions: View>: View {
    let symbol: String
    let title: String
    let message: String
    var tone: Tone = VC.blue
    @ViewBuilder var actions: () -> Actions

    var body: some View {
        VStack(spacing: 0) {
            VCIllustration(symbol: symbol, tone: tone)
            Text(title).font(VCFont.titleLarge).foregroundStyle(VC.onSurface).multilineTextAlignment(.center).padding(.top, 24)
            Text(message).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant).multilineTextAlignment(.center).padding(.top, 8)
            actions().padding(.top, 24)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

extension VCEmptyState where Actions == EmptyView {
    init(symbol: String, title: String, message: String, tone: Tone = VC.blue) {
        self.init(symbol: symbol, title: title, message: message, tone: tone) { EmptyView() }
    }
}

/// Tinted blobs, a tilted card and the icon: hints at the product. Decorative.
struct VCIllustration: View {
    let symbol: String
    let tone: Tone
    var body: some View {
        ZStack {
            Circle().fill(tone.container).frame(width: 150, height: 150)
            Circle().fill(tone.accent.opacity(0.16)).frame(width: 34, height: 34).offset(x: 62, y: -50)
            Circle().fill(tone.accent.opacity(0.22)).frame(width: 18, height: 18).offset(x: -66, y: 48)
            RoundedRectangle(cornerRadius: 12).fill(LinearGradient(colors: [tone.accent.opacity(0.35), tone.accent.opacity(0.12)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 104, height: 66).rotationEffect(.degrees(-10))
            Image(systemName: symbol).font(.system(size: 28, weight: .semibold)).foregroundStyle(tone.accent)
                .frame(width: 64, height: 64).background(VC.card, in: Circle())
        }
        .frame(width: 168, height: 168)
        .accessibilityHidden(true)
    }
}

/// Small uppercase label above a group of rows.
struct VCOverline: View {
    let text: String
    var body: some View {
        Text(text.uppercased()).font(VCFont.overline).tracking(0.8).foregroundStyle(VC.onSurfaceVariant).accessibilityAddTraits(.isHeader)
    }
}

/// Settings-style row: solid icon badge, title, description, chevron.
struct VCNavigationRow: View {
    let symbol: String
    let tone: Tone
    let title: String
    var subtitle: String?
    var showChevron = true
    var action: (() -> Void)?

    var body: some View {
        let row = HStack(spacing: 14) {
            VCIconBadge(symbol: symbol, tone: tone, size: 40, solid: true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(VCFont.titleSmall).foregroundStyle(VC.onSurface)
                if let subtitle { Text(subtitle).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant) }
            }
            Spacer()
            if showChevron && action != nil { Image(systemName: "chevron.forward").font(.system(size: 13, weight: .semibold)).foregroundStyle(VC.onSurfaceVariant) }
        }
        .padding(.horizontal, VC.cardPadding).padding(.vertical, 10)
        .frame(minHeight: 64)
        .contentShape(Rectangle())

        if let action { Button(action: action) { row }.buttonStyle(.plain) } else { row }
    }
}

/// Progress stepper for multi-step forms.
struct VCStepper: View {
    let steps: [String]
    let current: Int
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        if typeSize.isAccessibilitySize {
            // Four labels cannot fit side by side at the largest sizes: show the dots and one readable line.
            VStack(alignment: .leading, spacing: 6) {
                dots(labels: false)
                Text(L10n.s("form.step_of", current + 1, steps.count, steps[current])).font(VCFont.labelLarge).foregroundStyle(VC.primary)
            }
            .accessibilityElement(children: .combine)
        } else {
            dots(labels: true)
        }
    }

    private func dots(labels: Bool) -> some View {
        HStack(alignment: .top, spacing: 0) {
            ForEach(Array(steps.enumerated()), id: \.offset) { i, label in
                VStack(spacing: 6) {
                    HStack(spacing: 0) {
                        Rectangle().fill(i == 0 ? .clear : (i <= current ? VC.primary : VC.outlineVariant)).frame(height: 2)
                        Circle().fill(i <= current ? VC.primary : VC.outlineVariant).frame(width: i == current ? 20 : 14, height: i == current ? 20 : 14)
                            .overlay { if i == current { Circle().fill(VC.onPrimary).frame(width: 8, height: 8) } }
                        Rectangle().fill(i == steps.count - 1 ? .clear : (i < current ? VC.primary : VC.outlineVariant)).frame(height: 2)
                    }
                    .frame(height: 24)
                    if labels {
                        Text(label).font(VCFont.labelMedium).foregroundStyle(i == current ? VC.primary : VC.onSurfaceVariant).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
            }
        }
    }
}

/// One timeline event.
struct VCTimelineEntry: Identifiable {
    let id = UUID()
    let symbol: String
    let tone: Tone
    let title: String
    let time: String
}

struct VCTimeline: View {
    let entries: [VCTimelineEntry]
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(entries.enumerated()), id: \.element.id) { i, e in
                HStack(alignment: .top, spacing: 12) {
                    VStack(spacing: 0) {
                        VCIconBadge(symbol: e.symbol, tone: e.tone, size: 32, circle: true)
                        if i < entries.count - 1 { Rectangle().fill(VC.outlineVariant).frame(width: 2).frame(minHeight: 18) }
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(e.title).font(VCFont.bodyMedium).foregroundStyle(VC.onSurface)
                        Text(e.time).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
                    }
                    .padding(.top, 4).padding(.bottom, 14)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }
}

/// The VCardly "V" mark (two folded ribbons). Decorative.
struct VCMark: View {
    var size: CGFloat = 32
    var body: some View {
        Canvas { ctx, s in
            let w = s.width, h = s.height
            var left = Path()
            left.move(to: CGPoint(x: 0, y: h * 0.08)); left.addLine(to: CGPoint(x: w * 0.30, y: h * 0.08))
            left.addLine(to: CGPoint(x: w * 0.56, y: h * 0.70)); left.addLine(to: CGPoint(x: w * 0.42, y: h * 0.96)); left.closeSubpath()
            var right = Path()
            right.move(to: CGPoint(x: w * 0.42, y: h * 0.96)); right.addLine(to: CGPoint(x: w * 0.70, y: h * 0.08))
            right.addLine(to: CGPoint(x: w, y: h * 0.08)); right.addLine(to: CGPoint(x: w * 0.58, y: h * 0.96)); right.closeSubpath()
            ctx.fill(left, with: .linearGradient(Gradient(colors: [Color(argb: 0xFF3B6FFF), Color(argb: 0xFF2456F0)]), startPoint: .zero, endPoint: CGPoint(x: w, y: h)))
            ctx.fill(right, with: .linearGradient(Gradient(colors: [Color(argb: 0xFF14276B), Color(argb: 0xFF2456F0)]), startPoint: CGPoint(x: w, y: 0), endPoint: CGPoint(x: 0, y: h)))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct VCLogo: View {
    var color: Color = VC.onSurface
    var body: some View {
        HStack(spacing: 8) {
            VCMark(size: 30)
            Text(L10n.s("app.name")).font(VCFont.headlineSmall).foregroundStyle(color)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L10n.s("app.name"))
    }
}

enum Initials {
    /// Up to two initials from the first and last words (grapheme-safe for Telugu, Devanagari, Arabic, emoji).
    static func of(_ name: String) -> String {
        let words = name.split(whereSeparator: \.isWhitespace)
        guard let first = words.first?.first else { return "" }
        if words.count == 1 { return String(first).uppercased() }
        return (String(first) + String(words.last!.first!)).uppercased()
    }
}

/// WCAG contrast helpers on ARGB values (same maths as Android's Contrast).
enum Contrast {
    static func luminance(_ argb: UInt32) -> Double {
        func ch(_ shift: UInt32) -> Double {
            let c = Double((argb >> shift) & 0xFF) / 255
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    static func ratio(_ a: UInt32, _ b: UInt32) -> Double {
        let la = luminance(a), lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    static func readableOnIsWhite(_ background: UInt32) -> Bool { ratio(0xFFFFFFFF, background) >= ratio(0xFF000000, background) }
}
