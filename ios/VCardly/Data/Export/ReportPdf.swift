import UIKit

/// Layout-neutral description of the PDF report (same idea as Android's PdfDoc), so the content is unit-tested apart from drawing.
enum PdfBlock: Equatable {
    case heading(String)
    case paragraph(String)
    case keyValues([KeyValue])
    /// `fraction` is 0...1 of the largest bar.
    case bars([BarRow])
    /// Column `weights` are relative widths.
    case table(header: [String], rows: [[String]], weights: [CGFloat])
}

struct KeyValue: Equatable { let key: String; let value: String }
struct BarRow: Equatable { let label: String; let value: String; let fraction: CGFloat }

struct PdfDoc: Equatable {
    let title: String
    let subtitle: String
    let blocks: [PdfBlock]
}

enum ReportPdfContent {
    /// Builds the report from the same figures the Reports screen shows, plus a contact directory.
    static func build(_ r: ReportsState, contacts: [ContactDetails], now: Date = Date()) -> PdfDoc {
        let nf = NumberFormatter()
        nf.numberStyle = .decimal
        func n(_ v: Int) -> String { nf.string(from: NSNumber(value: v)) ?? "\(v)" }
        func bars(_ items: [(String, Int)]) -> PdfBlock {
            let maxValue = max(items.map(\.1).max() ?? 1, 1)
            return .bars(items.map { BarRow(label: $0.0, value: n($0.1), fraction: CGFloat($0.1) / CGFloat(maxValue)) })
        }
        let monthFormat = DateFormatter()
        monthFormat.setLocalizedDateFormatFromTemplate("MMM yyyy")

        var blocks: [PdfBlock] = [
            .heading(L10n.s("report.pdf.summary")),
            .keyValues([
                KeyValue(key: L10n.s("home.stat.contacts"), value: n(r.total)),
                KeyValue(key: L10n.s("home.stat.favorites"), value: n(r.favorites)),
                KeyValue(key: L10n.s("reports.scanned"), value: n(r.scanned)),
                KeyValue(key: L10n.s("report.pdf.added_6_months"), value: n(r.growth.map(\.count).reduce(0, +))),
            ]),
            .heading(L10n.s("reports.growth")),
            bars(r.growth.map { (monthFormat.string(from: $0.month), $0.count) }),
            .heading(L10n.s("reports.followups")),
        ]
        let statuses: [FollowUpStatus] = [.pending, .rescheduled, .completed, .cancelled]
        let followUpTotal = statuses.map { r.followUpsByStatus[$0] ?? 0 }.reduce(0, +)
        blocks.append(followUpTotal == 0 ? .paragraph(L10n.s("report.pdf.none")) : bars(statuses.map { ($0.label, r.followUpsByStatus[$0] ?? 0) }))
        blocks.append(.heading(L10n.s("reports.by_category")))
        blocks.append(r.byCategory.isEmpty ? .paragraph(L10n.s("report.pdf.none")) : bars(r.byCategory.map { ($0.name, $0.count) }))
        blocks.append(.heading(L10n.s("reports.top_tags")))
        blocks.append(r.topTags.isEmpty ? .paragraph(L10n.s("report.pdf.none")) : bars(r.topTags.map { ("#" + $0.name, $0.count) }))
        if !contacts.isEmpty {
            blocks.append(.heading(L10n.s("report.pdf.directory")))
            blocks.append(.table(header: ["field.name", "field.company", "field.phone", "field.email"].map { L10n.s($0) },
                                 rows: contacts.map { [$0.contact.fullName, $0.contact.company, $0.contact.phone, $0.contact.email] },
                                 weights: [3, 3, 3, 4]))
        }
        return PdfDoc(title: L10n.s("report.pdf.title"),
                      subtitle: L10n.s("report.pdf.generated", now.formatted(date: .long, time: .shortened)),
                      blocks: blocks)
    }
}

/// Draws a PdfDoc onto A4 pages with UIGraphicsPDFRenderer. Fixed light colours (it is for printing and sharing).
enum PdfRenderer {
    static let page = CGRect(x: 0, y: 0, width: 595, height: 842) // A4 in points
    private static let margin: CGFloat = 40
    private static let ink = UIColor(argb: 0xFF0F172A)
    private static let muted = UIColor(argb: 0xFF545E73)
    private static let brand = UIColor(argb: 0xFF2456F0)
    private static let track = UIColor(argb: 0xFFEAF0FF)
    private static let rule = UIColor(argb: 0xFFE1E6F0)

    private static func font(_ weight: String, _ size: CGFloat) -> UIFont {
        UIFont(name: "PlusJakartaSans-\(weight)", size: size) ?? .systemFont(ofSize: size, weight: weight == "Bold" ? .bold : (weight == "SemiBold" ? .semibold : .regular))
    }

    static func render(_ doc: PdfDoc, to url: URL) throws {
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [kCGPDFContextTitle as String: doc.title, kCGPDFContextCreator as String: L10n.s("app.name")]
        let renderer = UIGraphicsPDFRenderer(bounds: page, format: format)
        try renderer.writePDF(to: url) { ctx in
            var y: CGFloat = 0
            let width = page.width - 2 * margin
            func newPage() { ctx.beginPage(); y = margin }
            func ensure(_ h: CGFloat) { if y + h > page.height - margin { newPage() } }
            func text(_ s: String, _ f: UIFont, _ color: UIColor, x: CGFloat, w: CGFloat, maxLines: Int = 0) -> CGFloat {
                let style = NSMutableParagraphStyle()
                style.lineBreakMode = maxLines == 1 ? .byTruncatingTail : .byWordWrapping
                let attrs: [NSAttributedString.Key: Any] = [.font: f, .foregroundColor: color, .paragraphStyle: style]
                let str = NSAttributedString(string: s, attributes: attrs)
                let h = maxLines == 1 ? ceil(f.lineHeight) : ceil(str.boundingRect(with: CGSize(width: w, height: .greatestFiniteMagnitude),
                                                                                   options: [.usesLineFragmentOrigin], context: nil).height)
                str.draw(with: CGRect(x: x, y: y, width: w, height: h), options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], context: nil)
                return h
            }

            newPage()
            y += text(doc.title, font("Bold", 24), ink, x: margin, w: width) + 4
            y += text(doc.subtitle, font("Regular", 10), muted, x: margin, w: width) + 14

            for block in doc.blocks {
                switch block {
                case .heading(let s):
                    ensure(44)
                    y += 10
                    y += text(s, font("SemiBold", 14), brand, x: margin, w: width) + 6
                case .paragraph(let s):
                    ensure(18)
                    y += text(s, font("Regular", 10), muted, x: margin, w: width) + 6
                case .keyValues(let rows):
                    for kv in rows {
                        ensure(18)
                        _ = text(kv.key, font("Regular", 11), ink, x: margin, w: width * 0.6, maxLines: 1)
                        let h = text(kv.value, font("SemiBold", 11), ink, x: margin + width * 0.6, w: width * 0.4, maxLines: 1)
                        y += h + 4
                    }
                case .bars(let rows):
                    let labelW = width * 0.32, valueW: CGFloat = 50, barW = width - labelW - valueW - 16
                    for b in rows {
                        ensure(18)
                        let h = text(b.label, font("Regular", 10), ink, x: margin, w: labelW, maxLines: 1)
                        let barRect = CGRect(x: margin + labelW + 8, y: y + h / 2 - 4, width: barW, height: 8)
                        track.setFill(); UIBezierPath(roundedRect: barRect, cornerRadius: 4).fill()
                        if b.fraction > 0 {
                            brand.setFill()
                            UIBezierPath(roundedRect: CGRect(x: barRect.minX, y: barRect.minY, width: max(barW * b.fraction, 4), height: 8), cornerRadius: 4).fill()
                        }
                        _ = text(b.value, font("SemiBold", 10), ink, x: margin + width - valueW, w: valueW, maxLines: 1)
                        y += h + 6
                    }
                case .table(let header, let rows, let weights):
                    let total = weights.reduce(0, +)
                    let widths = weights.map { width * $0 / total }
                    func drawRow(_ cells: [String], _ f: UIFont, _ color: UIColor) {
                        ensure(18)
                        var x = margin
                        var h: CGFloat = 0
                        for (i, cell) in cells.enumerated() where i < widths.count {
                            h = max(h, text(cell, f, color, x: x, w: widths[i] - 6, maxLines: 1))
                            x += widths[i]
                        }
                        y += h + 3
                        rule.setFill(); UIRectFill(CGRect(x: margin, y: y, width: width, height: 0.5))
                        y += 3
                    }
                    drawRow(header, font("SemiBold", 9), muted)
                    for r in rows { drawRow(r, font("Regular", 9), ink) }
                }
            }
        }
    }
}
