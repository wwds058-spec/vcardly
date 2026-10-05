import SwiftUI

/// Label-above text field with an optional required marker and an inline error.
struct VCTextField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var required = false
    var error: String?
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?
    var capitalization: TextInputAutocapitalization = .sentences
    var axis: Axis = .horizontal
    var symbol: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 2) {
                Text(label).font(VCFont.labelLarge).foregroundStyle(VC.onSurface)
                if required { Text(verbatim: "*").font(VCFont.labelLarge).foregroundStyle(VC.error).accessibilityHidden(true) }
            }
            HStack(alignment: axis == .vertical ? .top : .center, spacing: 10) {
                if let symbol { Image(systemName: symbol).foregroundStyle(VC.onSurfaceVariant).accessibilityHidden(true) }
                TextField(placeholder, text: $text, axis: axis)
                    .font(VCFont.bodyLarge)
                    .keyboardType(keyboard)
                    .textContentType(contentType)
                    .textInputAutocapitalization(capitalization)
                    .autocorrectionDisabled(keyboard != .default)
                    .lineLimit(axis == .vertical ? 3...8 : 1...1)
                    .accessibilityLabel(required ? L10n.s("form.required_label", label) : label)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background(VC.field, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(error != nil ? VC.error : VC.outlineVariant, lineWidth: error != nil ? 1.5 : 1))
            if let error {
                Label(error, systemImage: "exclamationmark.circle.fill").font(VCFont.bodySmall).foregroundStyle(VC.error)
            }
        }
    }
}

extension ValidationReason {
    func message(_ field: ContactField) -> String {
        switch self {
        case .required: L10n.s("validation.required")
        case .tooLong: L10n.s("validation.too_long")
        case .invalidFormat:
            switch field {
            case .phone, .phoneAlt: L10n.s("validation.invalid_phone")
            case .email, .emailAlt: L10n.s("validation.invalid_email")
            case .website: L10n.s("validation.invalid_website")
            default: L10n.s("validation.invalid")
            }
        }
    }
}
