import SwiftUI

struct MyCardScreen: View {
    @Environment(AppEnvironment.self) private var env
    let edit: () -> Void

    var body: some View {
        MyCardContent(card: env.prefs.myCard, edit: edit)
            .navigationTitle(L10n.s("mycard.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !env.prefs.myCard.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) { Button(L10n.s("common.edit"), action: edit) }
                }
            }
    }
}

/// Stateless digital card + QR (used directly by screenshot tests).
struct MyCardContent: View {
    let card: MyCard
    let edit: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if card.isEmpty {
                    VCEmptyState(symbol: "person.text.rectangle", title: L10n.s("mycard.empty.title"), message: L10n.s("mycard.empty.message"), tone: VC.lavender) {
                        VCPrimaryButton(title: L10n.s("mycard.create"), icon: "plus", action: edit).frame(width: 240)
                    }
                    .padding(.top, 60)
                } else {
                    DigitalCard(card: card)
                    QRSharePanel(card: .of(card))
                }
            }
            .padding(VC.screen)
        }
        .background(VC.background)
    }
}

/// The user's own card on the brand gradient.
struct DigitalCard: View {
    let card: MyCard

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Text(Initials.of(card.fullName)).font(VCFont.titleLarge).foregroundStyle(VC.gradientBlue[1])
                    .frame(width: 56, height: 56).background(Color.white, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(card.fullName).font(VCFont.headlineSmall).foregroundStyle(.white).lineLimit(2)
                    if !card.jobTitle.isEmpty { Text(card.jobTitle).font(VCFont.bodyMedium).foregroundStyle(.white.opacity(0.88)) }
                    if !card.company.isEmpty { Text(card.company).font(VCFont.titleSmall).foregroundStyle(.white) }
                }
                Spacer(minLength: 0)
            }
            Rectangle().fill(.white.opacity(0.2)).frame(height: 1)
            VStack(alignment: .leading, spacing: 8) {
                ForEach([("phone.fill", card.phone), ("envelope.fill", card.email), ("globe", card.website), ("mappin.and.ellipse", card.address)]
                    .filter { !$0.1.trimmed.isEmpty }, id: \.0) { item in
                    Label(item.1, systemImage: item.0).font(VCFont.bodyMedium).foregroundStyle(.white).lineLimit(2)
                }
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack {
                LinearGradient(colors: VC.cardHero, startPoint: .topLeading, endPoint: .bottomTrailing)
                Circle().fill(.white.opacity(0.08)).frame(width: 220).offset(x: 140, y: -90)
                Circle().fill(.white.opacity(0.06)).frame(width: 140).offset(x: -150, y: 90)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: VC.gradientBlue[1].opacity(0.35), radius: 16, y: 8)
        .accessibilityElement(children: .combine)
    }
}

struct MyCardEditScreen: View {
    @Environment(AppEnvironment.self) private var env
    let done: () -> Void
    @State private var card = MyCard()
    @State private var errors: [ContactField: ValidationReason] = [:]
    @State private var loaded = false

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L10n.s("mycard.edit_hint")).font(VCFont.bodyMedium).foregroundStyle(VC.onSurfaceVariant)
                    f(.fullName, \.fullName, "field.name", required: true, type: .name, cap: .words)
                    f(.jobTitle, \.jobTitle, "field.job_title", type: .jobTitle, cap: .words)
                    f(.company, \.company, "field.company", type: .organizationName, cap: .words)
                    f(.phone, \.phone, "field.phone", keyboard: .phonePad, type: .telephoneNumber)
                    f(.phoneAlt, \.phoneAlt, "field.phone_alt", keyboard: .phonePad, type: .telephoneNumber)
                    f(.email, \.email, "field.email", keyboard: .emailAddress, type: .emailAddress, cap: .never)
                    f(.emailAlt, \.emailAlt, "field.email_alt", keyboard: .emailAddress, type: .emailAddress, cap: .never)
                    f(.website, \.website, "field.website", keyboard: .URL, type: .URL, cap: .never)
                    f(.address, \.address, "field.address", type: .fullStreetAddress, axis: .vertical)
                }
                .padding(VC.screen)
            }
            .scrollDismissesKeyboard(.interactively)
            VCPrimaryButton(title: L10n.s("common.save"), icon: "checkmark", action: save)
                .padding(.horizontal, VC.screen).padding(.vertical, 12)
                .background(VC.card.ignoresSafeArea(edges: .bottom))
        }
        .background(VC.background)
        .navigationTitle(L10n.s("mycard.edit"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if !loaded { card = env.prefs.myCard; loaded = true } }
    }

    private func f(_ field: ContactField, _ path: WritableKeyPath<MyCard, String>, _ key: String, required: Bool = false,
                   keyboard: UIKeyboardType = .default, type: UITextContentType? = nil, cap: TextInputAutocapitalization = .sentences,
                   axis: Axis = .horizontal) -> some View {
        VCTextField(label: L10n.s(key), text: Binding(get: { card[keyPath: path] }, set: { card[keyPath: path] = $0; errors[field] = nil }),
                    required: required, error: errors[field]?.message(field), keyboard: keyboard, contentType: type, capitalization: cap, axis: axis)
    }

    private func save() {
        // Same rules as a contact.
        let asContact = Contact(fullName: card.fullName, jobTitle: card.jobTitle, company: card.company, phone: card.phone, phoneAlt: card.phoneAlt,
                                email: card.email, emailAlt: card.emailAlt, website: card.website, address: card.address)
        errors = ContactValidator.validate(asContact)
        guard errors.isEmpty else { return }
        env.prefs.setMyCard(MyCard(fullName: card.fullName.trimmed, jobTitle: card.jobTitle.trimmed, company: card.company.trimmed,
                                   phone: card.phone.trimmed, phoneAlt: card.phoneAlt.trimmed, email: card.email.trimmed,
                                   emailAlt: card.emailAlt.trimmed, website: card.website.trimmed, address: card.address.trimmed))
        done()
    }
}
