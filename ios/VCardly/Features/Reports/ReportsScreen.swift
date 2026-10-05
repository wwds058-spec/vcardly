import Charts
import SwiftUI

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

/// Figures derived from stored contacts and follow-ups only.
struct ReportsState: Equatable {
    var total = 0
    var favorites = 0
    var scanned = 0
    var byCategory: [CategorySlice] = []
    var growth: [MonthCount] = []
    var followUpsByStatus: [FollowUpStatus: Int] = [:]

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
        return s
    }
}

struct ReportsScreen: View {
    @Environment(AppEnvironment.self) private var env
    @State private var state = ReportsState()

    var body: some View {
        ReportsContent(state: state)
            .navigationTitle(L10n.s("reports.title"))
            .navigationBarTitleDisplayMode(.inline)
            .task(id: env.revision.value) { state = .build(contacts: env.contacts.contacts(), followUps: env.followUps.all()) }
    }
}

/// Stateless reports (used directly by screenshot tests).
struct ReportsContent: View {
    let state: ReportsState

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if state.total == 0 {
                    VCEmptyState(symbol: "chart.bar.fill", title: L10n.s("reports.empty.title"), message: L10n.s("reports.empty.message")).padding(.top, 60)
                } else {
                    Grid(horizontalSpacing: 12, verticalSpacing: 12) {
                        GridRow {
                            VCStatCard(value: "\(state.total)", label: L10n.s("home.stat.contacts"), symbol: "person.2.fill", tone: VC.blue)
                            VCStatCard(value: "\(state.favorites)", label: L10n.s("home.stat.favorites"), symbol: "star.fill", tone: VC.rose)
                        }
                        GridRow {
                            VCStatCard(value: "\(state.scanned)", label: L10n.s("reports.scanned"), symbol: "doc.viewfinder", tone: VC.lavender)
                            VCStatCard(value: "\(state.followUpsByStatus[.completed] ?? 0)", label: L10n.s("reports.followups_done"), symbol: "checkmark.circle.fill", tone: VC.mint)
                        }
                    }
                    categories
                    growth
                    followUps
                }
            }
            .padding(VC.screen)
        }
        .background(VC.background)
    }

    private var categories: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.s("reports.by_category")).font(VCFont.titleMedium).accessibilityAddTraits(.isHeader)
            HStack(spacing: 18) {
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
                }
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(state.byCategory.prefix(6)) { s in
                        HStack(spacing: 8) {
                            Circle().fill(Color(argb: s.argb)).frame(width: 10, height: 10)
                            Text(s.name).font(VCFont.bodySmall).lineLimit(1)
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
