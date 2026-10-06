import Charts
import SwiftUI
import UniformTypeIdentifiers

struct CategorySlice: Identifiable, Equatable {
    let name: String
    let argb: UInt32
    let count: Int
    var id: String { name }
}

struct MonthCount: Identifiable, Equatable {
    let month: Date
    let count: Int
    var id: Date { month }
}

struct TagCount: Identifiable, Equatable {
    let name: String
    let count: Int
    var id: String { name }
}

/// Figures derived from stored contacts and follow-ups only.
struct ReportsState: Equatable {
    var total = 0
    var favorites = 0
    var scanned = 0
    var byCategory: [CategorySlice] = []
    var growth: [MonthCount] = []
    var followUpsByStatus: [FollowUpStatus: Int] = [:]
    var topTags: [TagCount] = []

    static func build(contacts: [ContactDetails], followUps: [FollowUpWithContact], now: Date = Date(), calendar: Calendar = .current) -> ReportsState {
        var s = ReportsState()
        s.total = contacts.count
        s.favorites = contacts.filter(\.contact.isFavorite).count
        s.scanned = contacts.filter { $0.contact.source == .scan }.count
        var groups: [String: (UInt32, Int)] = [:]
        for d in contacts {
            let name = d.category?.displayName ?? L10n.s("category.none")
            let argb = d.category?.colorARGB ?? 0xFF8A93A8
            groups[name, default: (argb, 0)].1 += 1
        }
        s.byCategory = groups.map { CategorySlice(name: $0.key, argb: $0.value.0, count: $0.value.1) }.sorted { $0.count > $1.count }
        let thisMonth = calendar.dateInterval(of: .month, for: now)!.start
        s.growth = (0..<6).reversed().map { back in
            let start = calendar.date(byAdding: .month, value: -back, to: thisMonth)!
            let end = calendar.date(byAdding: .month, value: 1, to: start)!
            return MonthCount(month: start, count: contacts.filter { $0.contact.createdAt >= start && $0.contact.createdAt < end }.count)
        }
        for f in followUps { s.followUpsByStatus[f.followUp.status, default: 0] += 1 }
        var tagCounts: [String: Int] = [:]
        for d in contacts { for t in d.tags { tagCounts[t.name, default: 0] += 1 } }
        s.topTags = tagCounts.map { TagCount(name: $0.key, count: $0.value) }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.name.lowercased() < $1.name.lowercased() }
            .prefix(8).map { $0 }
        return s
    }
}

enum ReportExportFormat: CaseIterable {
    case pdf, csv, xlsx

    var contentType: UTType {
        switch self {
        case .pdf: .pdf
        case .csv: .commaSeparatedText
        case .xlsx: UTType(filenameExtension: "xlsx") ?? .data
        }
    }
    var fileNameKey: String {
        switch self {
        case .pdf: "export.file.pdf"
        case .csv: "export.file.csv"
        case .xlsx: "export.file.xlsx"
        }
    }
    /// Android sells PDF and Excel as Pro; see ProFeatures.
    var needsPro: Bool { self != .csv && ProFeatures.exportsRequirePro }
}

enum ReportExportState: Equatable { case idle, working, done, failed }

/// A file already written to a private temporary folder, handed to the system "Save to Files" sheet.
struct ExportFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    let url: URL
    init(url: URL) { self.url = url }
    init(configuration: ReadConfiguration) throws { throw CocoaError(.featureUnsupported) }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { try FileWrapper(url: url, options: []) }
}

enum ReportExporter {
    /// Writes the chosen format into `dir` and returns the file. Runs off the main thread.
    static func write(_ format: ReportExportFormat, report: ReportsState, contacts: [ContactDetails], followUps: [FollowUpWithContact],
                      dir: URL) throws -> URL {
        let url = dir.appendingPathComponent(L10n.s(format.fileNameKey))
        switch format {
        case .csv: try Data(CsvWriter.write(ExportTables.contacts(contacts)).utf8).write(to: url, options: [.atomic, .completeFileProtection])
        case .xlsx: try XlsxWriter.write([ExportTables.contacts(contacts), ExportTables.followUps(followUps)], to: url)
        case .pdf: try PdfRenderer.render(ReportPdfContent.build(report, contacts: contacts), to: url)
        }
        return url
    }
}

struct ReportsScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var state = ReportsState()
    @State private var exportState: ReportExportState = .idle
    @State private var file: (url: URL, dir: URL, type: UTType)?

    var body: some View {
        ReportsContent(state: state, exportState: exportState, onExport: export)
            .navigationTitle(L10n.s("reports.title"))
            .navigationBarTitleDisplayMode(.inline)
            .task(id: env.revision.value) { state = .build(contacts: env.contacts.contacts(), followUps: env.followUps.all()) }
            .fileExporter(isPresented: Binding(get: { file != nil }, set: { if !$0 { finish(nil) } }),
                          document: file.map { ExportFileDocument(url: $0.url) }, contentType: file?.type ?? .data,
                          defaultFilename: file?.url.lastPathComponent) { result in
                if case .success = result { finish(.done) } else { finish(.idle) }
            }
    }

    private func export(_ format: ReportExportFormat) {
        let contacts = env.contacts.contacts()
        let followUps = env.followUps.all()
        let report = state
        exportState = .working
        Task {
            do {
                let dir = FileManager.default.temporaryDirectory.appendingPathComponent("export-\(UUID().uuidString)", isDirectory: true)
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                let url = try await Task.detached(priority: .userInitiated) {
                    try ReportExporter.write(format, report: report, contacts: contacts, followUps: followUps, dir: dir)
                }.value
                file = (url, dir, format.contentType)
            } catch {
                exportState = .failed
            }
        }
    }

    /// Deletes the temporary copy once the user has saved it (or cancelled).
    private func finish(_ next: ReportExportState?) {
        if let f = file { try? FileManager.default.removeItem(at: f.dir) }
        file = nil
        if let next { exportState = next } else if exportState == .working { exportState = .idle }
    }
}

/// Stateless reports (used directly by screenshot tests).
struct ReportsContent: View {
    let state: ReportsState
    @Environment(\.dynamicTypeSize) private var typeSize
    var exportState: ReportExportState = .idle
    var onExport: (ReportExportFormat) -> Void = { _ in }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if state.total == 0 {
                    VCEmptyState(symbol: "chart.bar.fill", title: L10n.s("reports.empty.title"), message: L10n.s("reports.empty.message")).padding(.top, 60)
                } else {
                    VCAdaptiveGrid {
                        VCStatCard(value: "\(state.total)", label: L10n.s("home.stat.contacts"), symbol: "person.2.fill", tone: VC.blue)
                        VCStatCard(value: "\(state.favorites)", label: L10n.s("home.stat.favorites"), symbol: "star.fill", tone: VC.rose)
                        VCStatCard(value: "\(state.scanned)", label: L10n.s("reports.scanned"), symbol: "doc.viewfinder", tone: VC.lavender)
                        VCStatCard(value: "\(state.followUpsByStatus[.completed] ?? 0)", label: L10n.s("reports.followups_done"), symbol: "checkmark.circle.fill", tone: VC.mint)
                    }
                    categories
                    growth
                    followUps
                    exportSection
                }
            }
            .padding(VC.screen)
        }
        .background(VC.background)
    }

    private var exportSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.s("export.title")).font(VCFont.titleMedium).accessibilityAddTraits(.isHeader)
            HStack(spacing: 10) {
                exportTile(.pdf, "doc.richtext.fill", VC.rose, "export.tile.pdf")
                exportTile(.csv, "tablecells", VC.blue, "export.tile.csv")
                exportTile(.xlsx, "tablecells.fill", VC.mint, "export.tile.xlsx")
            }
            .disabled(exportState == .working)
            switch exportState {
            case .working:
                HStack(spacing: 10) { ProgressView(); Text(L10n.s("export.working")).font(VCFont.bodySmall) }
            case .done:
                VCNotice(text: L10n.s("export.done"), tone: VC.mint, symbol: "checkmark.circle.fill")
            case .failed:
                VCNotice(text: L10n.s("export.failed"), tone: VC.rose, symbol: "xmark.octagon.fill")
            case .idle:
                EmptyView()
            }
            Text(L10n.s("export.privacy_note")).font(VCFont.bodySmall).foregroundStyle(VC.onSurfaceVariant)
        }
        .vcCard()
    }

    private func exportTile(_ f: ReportExportFormat, _ symbol: String, _ tone: Tone, _ key: String) -> some View {
        Button { onExport(f) } label: {
            VStack(spacing: 8) {
                Image(systemName: f.needsPro ? "lock.fill" : symbol).font(.system(size: 20, weight: .semibold)).foregroundStyle(tone.accent)
                    .frame(width: 44, height: 44).background(tone.container, in: Circle())
                Text(f.needsPro ? L10n.s("export.pro_label", L10n.s(key)) : L10n.s(key)).font(VCFont.labelLarge).foregroundStyle(VC.onSurface)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 12)
            .background(VC.cardHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(L10n.s("export.tile_label", L10n.s(key)))
    }

    private var categories: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.s("reports.by_category")).font(VCFont.titleMedium).accessibilityAddTraits(.isHeader)
            let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 16)) : AnyLayout(HStackLayout(spacing: 18))
            layout {
                Chart(state.byCategory) { s in
                    SectorMark(angle: .value(L10n.s("reports.contacts"), s.count), innerRadius: .ratio(0.62), angularInset: 1.5)
                        .cornerRadius(3)
                        .foregroundStyle(Color(argb: s.argb))
                }
                .chartLegend(.hidden)
                .frame(width: 130, height: 130)
                .overlay {
                    VStack(spacing: 0) {
                        Text("\(state.total)").font(VCFont.headlineSmall)
                        Text(L10n.s("reports.contacts")).font(VCFont.labelSmall).foregroundStyle(VC.onSurfaceVariant)
                    }
                    .dynamicTypeSize(...DynamicTypeSize.xLarge) // must fit inside the ring; the legend carries the numbers
                }
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(state.byCategory.prefix(6)) { s in
                        HStack(spacing: 8) {
                            Circle().fill(Color(argb: s.argb)).frame(width: 10, height: 10)
                            Text(s.name).font(VCFont.bodySmall).vcLineLimit(1)
                            Spacer()
                            Text("\(s.count)").font(VCFont.labelLarge)
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        }
        .vcCard()
    }

    private var growth: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.s("reports.growth")).font(VCFont.titleMedium).accessibilityAddTraits(.isHeader)
            Chart(state.growth) { m in
                BarMark(x: .value(L10n.s("reports.month"), m.month, unit: .month), y: .value(L10n.s("reports.contacts"), m.count), width: .ratio(0.5))
                    .cornerRadius(4)
                    .foregroundStyle(VC.blue.accent)
            }
            .chartXAxis { AxisMarks(values: .stride(by: .month)) { _ in AxisValueLabel(format: .dateTime.month(.abbreviated), centered: true) } }
            .chartYAxis { AxisMarks(position: .leading) { _ in AxisGridLine().foregroundStyle(VC.outlineVariant); AxisValueLabel() } }
            .frame(height: 170)
        }
        .vcCard()
    }

    private var followUps: some View {
        let rows: [(FollowUpStatus, Tone)] = [(.pending, VC.blue), (.rescheduled, VC.orange), (.completed, VC.mint), (.cancelled, VC.rose)]
        let maxValue = max(1, rows.map { state.followUpsByStatus[$0.0] ?? 0 }.max() ?? 1)
        return VStack(alignment: .leading, spacing: 12) {
            Text(L10n.s("reports.followups")).font(VCFont.titleMedium).accessibilityAddTraits(.isHeader)
            ForEach(rows, id: \.0) { status, tone in
                let n = state.followUpsByStatus[status] ?? 0
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(status.label).font(VCFont.bodyMedium)
                        Spacer()
                        Text("\(n)").font(VCFont.labelLarge)
                    }
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(VC.cardHigh)
                            Capsule().fill(tone.accent).frame(width: max(n > 0 ? 6 : 0, g.size.width * CGFloat(n) / CGFloat(maxValue)))
                        }
                    }
                    .frame(height: 8)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .vcCard()
    }
}
