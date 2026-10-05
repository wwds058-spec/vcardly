import SwiftUI
import UIKit

// Domain-aware shared UI (iOS counterparts of Android's presentation/common).

extension FollowUpType {
    var symbol: String {
        switch self {
        case .call: "phone.fill"
        case .whatsapp: "message.fill"
        case .email: "envelope.fill"
        case .meeting: "person.3.fill"
        case .quotation: "doc.text.fill"
        case .payment: "creditcard.fill"
        case .message: "text.bubble.fill"
        case .other: "checkmark.circle.fill"
        }
    }

    var tone: Tone {
        switch self {
        case .call, .message: VC.blue
        case .whatsapp, .payment: VC.mint
        case .email: VC.lavender
        case .meeting: VC.orange
        case .quotation: VC.navy
        case .other: VC.rose
        }
    }

    var label: String { L10n.s("followup.type.\(rawValue.lowercased())") }
}

extension FollowUpStatus {
    var label: String { L10n.s("followup.status.\(rawValue.lowercased())") }
}

/// "Today · 4:30 PM", "Tomorrow · 11:00 AM", "12 Sep 2026 · 10:00 AM".
func dueLabel(_ date: Date, now: Date = Date()) -> String {
    let cal = Calendar.current
    let time = date.formatted(date: .omitted, time: .shortened)
    if cal.isDate(date, inSameDayAs: now) { return L10n.s("due.today", time) }
    if let t = cal.date(byAdding: .day, value: 1, to: now), cal.isDate(date, inSameDayAs: t) { return L10n.s("due.tomorrow", time) }
    if let y = cal.date(byAdding: .day, value: -1, to: now), cal.isDate(date, inSameDayAs: y) { return L10n.s("due.yesterday", time) }
    return L10n.s("due.date", date.formatted(date: .abbreviated, time: .omitted), time)
}

/// Follow-up card: type badge, title, contact, due time (rose when overdue), one-tap complete with a spring.
struct FollowUpCard: View {
    let item: FollowUpWithContact
    var onTap: () -> Void = {}
    var onToggle: (() -> Void)?

    var body: some View {
        let f = item.followUp
        let overdue = f.status.isActive && f.dueAt < Calendar.current.startOfDay(for: Date())
        let done = f.status == .completed
        HStack(spacing: 14) {
            VCIconBadge(symbol: f.type.symbol, tone: f.type.tone, size: 46, circle: true)
            VStack(alignment: .leading, spacing: 2) {
                Text(f.title).font(VCFont.titleSmall).foregroundStyle(f.status.isActive ? VC.onSurface : VC.onSurfaceVariant)
                    .strikethrough(done).lineLimit(2)
                let who = [item.contactName, item.contactCompany].filter { !$0.isEmpty }.joined(separator: " · ")
                if !who.isEmpty { Text(who).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).lineLimit(1) }
                HStack(spacing: 6) {
                    if overdue { Circle().fill(VC.rose.accent).frame(width: 6, height: 6) }
                    Text(overdue ? L10n.s("due.overdue", dueLabel(f.dueAt)) : dueLabel(f.dueAt))
                        .font(VCFont.labelMedium).foregroundStyle(overdue ? VC.rose.content : VC.primary)
                    if f.status == .rescheduled || f.status == .cancelled {
                        VCTag(title: f.status.label, tone: f.status == .cancelled ? VC.rose : VC.orange)
                    }
                }
                .padding(.top, 2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            if let onToggle {
                Button(action: { withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { onToggle() } }) {
                    Image(systemName: f.status.isActive ? "checkmark" : "arrow.uturn.backward")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(done ? Color.white : VC.mint.content)
                        .frame(width: 34, height: 34)
                        .background(done ? VC.mint.accent : VC.mint.container, in: Circle())
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(L10n.s(f.status.isActive ? "followup.complete_item" : "followup.reopen_item", f.title))
            }
        }
        .vcCard(padding: 14)
    }
}

/// Contact list card with favourite star and compact Call / WhatsApp / Email shortcuts for channels the contact has.
struct ContactCard: View {
    let details: ContactDetails
    var showActions = true
    let onTap: () -> Void
    let onToggleFavorite: () -> Void

    var body: some View {
        let c = details.contact
        let phone = c.phone.isEmpty ? c.phoneAlt : c.phone
        let email = c.email.isEmpty ? c.emailAlt : c.email
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 14) {
                VCAvatar(name: c.fullName, argb: details.category?.colorARGB, size: 52)
                VStack(alignment: .leading, spacing: 1) {
                    Text(c.fullName).font(VCFont.titleMedium).foregroundStyle(VC.onSurface).lineLimit(1)
                    if !c.company.isEmpty { Text(c.company).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant).lineLimit(1) }
                    if !c.jobTitle.isEmpty { Text(c.jobTitle).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).lineLimit(1) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture(perform: onTap)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                FavoriteButton(isFavorite: c.isFavorite, name: c.fullName, action: onToggleFavorite)
            }
            if showActions && (!phone.isEmpty || !email.isEmpty) {
                HStack(spacing: 4) {
                    if !phone.isEmpty {
                        QuickIcon(symbol: "phone.fill", tone: VC.blue, label: L10n.s("action.call_name", c.fullName)) { ExternalActions.call(phone) }
                        if whatsAppDigits(phone) != nil {
                            QuickIcon(symbol: "message.fill", tone: VC.mint, label: L10n.s("action.whatsapp_name", c.fullName)) { ExternalActions.whatsApp(phone) }
                        }
                    }
                    if !email.isEmpty {
                        QuickIcon(symbol: "envelope.fill", tone: VC.lavender, label: L10n.s("action.email_name", c.fullName)) { ExternalActions.email(email) }
                    }
                }
                .padding(.leading, 58)
            }
        }
        .vcCard(padding: 14)
    }
}

private struct QuickIcon: View {
    let symbol: String
    let tone: Tone
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 14, weight: .semibold)).foregroundStyle(tone.accent)
                .frame(width: 36, height: 36).background(tone.container, in: Circle())
                .frame(width: 44, height: 44)
        }
        .accessibilityLabel(label)
    }
}

struct FavoriteButton: View {
    let isFavorite: Bool
    let name: String
    var tint: Color = VC.onSurfaceVariant
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: isFavorite ? "star.fill" : "star")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(isFavorite ? VC.favorite : tint)
                .symbolEffect(.bounce, value: isFavorite)
                .frame(width: 44, height: 44)
        }
        .accessibilityLabel(L10n.s(isFavorite ? "contact.remove_favorite" : "contact.add_favorite", name))
    }
}

/// Compact tile for the "Recent contacts" carousel.
struct ContactMiniCard: View {
    let details: ContactDetails
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                VCAvatar(name: details.contact.fullName, argb: details.category?.colorARGB, size: 44)
                    .padding(.bottom, 6)
                Text(details.contact.fullName).font(VCFont.titleSmall).foregroundStyle(VC.onSurface).lineLimit(1)
                Text(details.contact.company.isEmpty ? details.contact.jobTitle : details.contact.company)
                    .font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant).lineLimit(1)
            }
            .frame(width: 104, alignment: .leading)
            .vcCard(padding: 14)
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// A visiting card drawn from the contact's details, shown when no photo exists. Decorative.
struct BusinessCardArt: View {
    let name: String
    let jobTitle: String
    let company: String
    let phone: String
    let email: String
    let website: String

    var body: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: VC.cardHero, startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().fill(Color.white.opacity(0.08)).frame(width: 180).offset(x: 200, y: -70)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 10) {
                    Text(Initials.of(company.isEmpty ? name : company)).font(VCFont.titleMedium).foregroundStyle(VC.gradientBlue[1])
                        .frame(width: 40, height: 40).background(Color.white, in: RoundedRectangle(cornerRadius: 12))
                    Text(company.isEmpty ? name : company).font(VCFont.titleMedium).foregroundStyle(.white).lineLimit(1)
                }
                Spacer()
                Text(name).font(VCFont.titleLarge).foregroundStyle(.white).lineLimit(1)
                if !jobTitle.isEmpty { Text(jobTitle).font(VCFont.bodySmall).foregroundStyle(.white.opacity(0.85)).lineLimit(1) }
                VStack(alignment: .leading, spacing: 2) {
                    ForEach([("phone.fill", phone), ("envelope.fill", email), ("globe", website)].filter { !$0.1.isEmpty }, id: \.0) { item in
                        Label(item.1, systemImage: item.0).font(VCFont.labelMedium).foregroundStyle(.white.opacity(0.92)).lineLimit(1)
                    }
                }
                .padding(.top, 8)
            }
            .padding(20)
        }
        .aspectRatio(1.7, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityHidden(true)
    }
}

/// Hand-offs to other apps. No permissions needed: these open the dialer, Mail or WhatsApp; nothing happens automatically.
enum ExternalActions {
    static func call(_ number: String) {
        let digits = number.filter { $0.isASCIIDigit || $0 == "+" }
        open("tel:\(digits)")
    }

    static func email(_ address: String) {
        open("mailto:\(address.trimmed.addingPercentEncoding(withAllowedCharacters: .urlUserAllowed) ?? "")")
    }

    static func whatsApp(_ number: String) {
        guard let d = whatsAppDigits(number) else { return }
        open("https://wa.me/\(d)")
    }

    static func website(_ site: String) {
        let s = site.trimmed
        open(s.lowercased().hasPrefix("http") ? s : "https://\(s)")
    }

    static func map(_ address: String) {
        open("http://maps.apple.com/?q=\(address.trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")")
    }

    /// Copies text and marks it so it does not sync to other devices via Universal Clipboard and expires after a while.
    static func copy(_ text: String) {
        UIPasteboard.general.setItems([[UIPasteboard.typeAutomatic: text]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(120)])
    }

    private static func open(_ s: String) {
        guard let url = URL(string: s) else { return }
        UIApplication.shared.open(url)
    }
}
