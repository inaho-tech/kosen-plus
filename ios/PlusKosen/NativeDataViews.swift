import SwiftUI
import WebKit

struct CourseContentItem: Identifiable, Hashable {
    let id: String
    let title: String
    let category: String
    let url: URL?
    let isNew: Bool
    let isDisabled: Bool
    let detailURL: URL?
    let historyURL: URL?
    let usageCount: Int?
    let availablePeriod: String?
    let hasSubmitted: Bool
    let isUnread: Bool
}

struct CourseContentSection: Identifiable, Hashable {
    let id: String
    let title: String
    let items: [CourseContentItem]
}

struct CourseScoreItem: Identifiable, Hashable {
    let id: String
    let title: String
    let scoreText: String
    let score: Double?
    let average: Double?
    let url: URL?
}

struct CourseScoreGroup: Identifiable, Hashable {
    let id: String
    let name: String
    let items: [CourseScoreItem]
    let total: Double?
}

struct CourseReportItem: Identifiable, Hashable {
    let id: String
    let taskName: String
    let questionNumber: String
    let submissionDate: String
    let result: String
    let scoreText: String
    let comment: String?
    let taskURL: URL?
    let scoreURL: URL?
}

struct SyllabusDepartment: Identifiable, Hashable {
    let id: String
    let name: String
    let url: URL
}

struct SyllabusSubject: Identifiable, Hashable {
    let id: String
    let name: String
    let detail: String
    let url: URL?
    let category: String
    let requirement: String
    let code: String
    let teachers: String
    let targetYear: String
}

struct SyllabusDetailRow: Identifiable, Hashable {
    let id: String
    let label: String
    let value: String
}

private struct SubjectGroup: Identifiable {
    let category: String
    let subjects: [SyllabusSubject]
    var id: String { category }
}

private struct DepartmentGroup: Identifiable {
    let title: String
    let departments: [SyllabusDepartment]
    var id: String { title }
}

@MainActor
final class DOMPageLoader: NSObject, ObservableObject, WKNavigationDelegate {
    enum Kind { case course, scores, reports, departments, subjects, syllabusDetail }

    @Published var isLoading = true
    @Published var errorMessage: String?
    @Published var courseSections: [CourseContentSection] = []
    @Published var departments: [SyllabusDepartment] = []
    @Published var subjects: [SyllabusSubject] = []
    @Published var detailTitle = ""
    @Published var detailRows: [SyllabusDetailRow] = []
    @Published var scoreGroups: [CourseScoreGroup] = []
    @Published var reports: [CourseReportItem] = []

    private let url: URL
    private let kind: Kind
    private lazy var webView: WKWebView = {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = self
        return view
    }()

    init(url: URL, kind: Kind) {
        self.url = url
        self.kind = kind
        super.init()
        reload()
    }

    func reload() {
        isLoading = true
        errorMessage = nil
        webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { fail(error) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { fail(error) }
    private func fail(_ error: Error) { isLoading = false; errorMessage = error.localizedDescription }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard let current = webView.url else { return }
        if current.path.contains("login.php") || current.host?.contains("microsoftonline.com") == true {
            isLoading = false
            errorMessage = "WebClassへのログインが必要です。ホームからログインしてください。"
            return
        }
        switch kind {
        case .course: extractCourse(from: webView)
        case .scores: extractScores(from: webView)
        case .reports: extractReports(from: webView)
        case .departments: extractDepartments(from: webView)
        case .subjects: extractSubjects(from: webView)
        case .syllabusDetail: extractSyllabusDetail(from: webView)
        }
    }

    private func evaluate(_ script: String, completion: @escaping (Data?) -> Void) {
        webView.evaluateJavaScript(script) { result, error in
            Task { @MainActor in
                guard error == nil, let string = result as? String else {
                    self.isLoading = false
                    self.errorMessage = error?.localizedDescription ?? "ページを読み取れませんでした。"
                    completion(nil)
                    return
                }
                completion(string.data(using: .utf8))
            }
        }
    }

    private func extractCourse(from webView: WKWebView) {
        let script = #"""
        (() => {
          const sections = [...document.querySelectorAll('.cl-contentsList_folder')].map((folder, sectionIndex) => ({
            id: folder.id || `section-${sectionIndex}`,
            title: (folder.querySelector('.panel-title')?.textContent || '教材').replace(/\s+/g, ' ').trim(),
            items: [...folder.querySelectorAll('.cl-contentsList_listGroupItem')].map((item, itemIndex) => {
              const root = item.querySelector('.cm-contentsList_contentName');
              const link = root?.querySelector('a');
              const href = link?.getAttribute('href');
              return {
                id: `${sectionIndex}-${itemIndex}`,
                title: (link?.textContent || root?.textContent || '名称なし').replace(/\s+/g, ' ').replace(/New!?/gi, '').trim(),
                category: (item.querySelector('.cl-contentsList_categoryLabel')?.textContent || '').trim(),
                url: href ? new URL(href, location.href).href : null,
                isNew: !!root?.querySelector('.cl-contentsList_new'),
                isDisabled: !link,
                detailURL: [...item.querySelectorAll('.cl-contentsList_contentDetailListItemData a')].find(a => a.href.includes('/contents/') && !a.href.includes('history'))?.href || null,
                historyURL: [...item.querySelectorAll('.cl-contentsList_contentDetailListItemData a')].find(a => a.href.includes('history'))?.href || null,
                usageCount: (() => { const text = [...item.querySelectorAll('.cl-contentsList_contentDetailListItemData a')].find(a => a.href.includes('history'))?.textContent || ''; const match = text.match(/利用回数\s*(\d+)/); return match ? parseInt(match[1], 10) : null; })(),
                availablePeriod: [...item.querySelectorAll('.cm-contentsList_contentDetailListItem')].find(block => block.querySelector('.cm-contentsList_contentDetailListItemLabel')?.textContent.includes('利用可能期間'))?.querySelector('.cm-contentsList_contentDetailListItemData')?.textContent.trim() || null,
                hasSubmitted: (() => { const text = [...item.querySelectorAll('.cl-contentsList_contentDetailListItemData a')].find(a => a.href.includes('history'))?.textContent || ''; const match = text.match(/利用回数\s*(\d+)/); return !!match && parseInt(match[1], 10) > 0; })(),
                isUnread: /未読|未実施/.test(item.querySelector('.cl-contentsList_listStatus')?.textContent || '')
              };
            })
          }));
          return JSON.stringify(sections);
        })()
        """#
        struct Payload: Decodable { let id: String; let title: String; let items: [Item] }
        struct Item: Decodable { let id: String; let title: String; let category: String; let url: URL?; let isNew: Bool; let isDisabled: Bool; let detailURL: URL?; let historyURL: URL?; let usageCount: Int?; let availablePeriod: String?; let hasSubmitted: Bool; let isUnread: Bool }
        evaluate(script) { data in
            guard let data, let payload = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.courseSections = payload.map { section in
                CourseContentSection(id: section.id, title: section.title, items: section.items.map {
                    CourseContentItem(id: $0.id, title: $0.title, category: $0.category, url: $0.url, isNew: $0.isNew, isDisabled: $0.isDisabled, detailURL: $0.detailURL, historyURL: $0.historyURL, usageCount: $0.usageCount, availablePeriod: $0.availablePeriod, hasSubmitted: $0.hasSubmitted, isUnread: $0.isUnread)
                })
            }
            self.isLoading = false
        }
    }

    private func extractScores(from webView: WKWebView) {
        let script = #"""
        (() => {
          const table = document.querySelector('table#PersonalScoreSheet') || [...document.querySelectorAll('table')].find(t => t.querySelector('th.contents-title, tr.unittitle'));
          if (!table) return JSON.stringify([]);
          const groups = []; let current = () => groups[groups.length - 1];
          [...table.querySelectorAll('tr')].forEach((row, index) => {
            if (row.classList.contains('unittitle')) { groups.push({ id: `g-${index}`, name: row.querySelector('th')?.textContent.trim() || '成績一覧', items: [], total: null }); return; }
            if (row.classList.contains('foot')) { if (current()) current().total = parseFloat(row.querySelector('td')?.textContent) || null; return; }
            const titleCell = row.querySelector('th.contents-title'); if (!titleCell) return;
            if (!current()) groups.push({ id: 'scores', name: '成績一覧', items: [], total: null });
            const cells = row.querySelectorAll('td'); if (cells.length < 2) return;
            const scoreLink = cells[0].querySelector('a'); const raw = (scoreLink?.textContent || cells[0].textContent || '').trim();
            const scoreMatch = raw.match(/[0-9]+(?:\.[0-9]+)?/); const average = parseFloat(cells[1].textContent);
            current().items.push({ id: `s-${index}`, title: (titleCell.querySelector('a')?.textContent || titleCell.textContent).trim(), scoreText: raw, score: scoreMatch ? parseFloat(scoreMatch[0]) : null, average: Number.isNaN(average) ? null : average, url: scoreLink ? new URL(scoreLink.href, location.href).href : null });
          });
          return JSON.stringify(groups);
        })()
        """#
        struct Payload: Decodable { let id: String; let name: String; let items: [Item]; let total: Double? }
        struct Item: Decodable { let id: String; let title: String; let scoreText: String; let score: Double?; let average: Double?; let url: URL? }
        evaluate(script) { data in
            guard let data, let values = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.scoreGroups = values.map { CourseScoreGroup(id: $0.id, name: $0.name, items: $0.items.map { CourseScoreItem(id: $0.id, title: $0.title, scoreText: $0.scoreText, score: $0.score, average: $0.average, url: $0.url) }, total: $0.total) }
            self.isLoading = false
        }
    }

    private func extractReports(from webView: WKWebView) {
        let script = #"""
        (() => {
          const table = document.querySelector('div.table-responsive table.table'); if (!table) return JSON.stringify([]);
          return JSON.stringify([...table.querySelectorAll('tbody tr')].map((row, index) => {
            const cells = row.querySelectorAll('td'); if (cells.length < 6) return null;
            const taskLink = cells[0].querySelector('a'); const scoreLink = cells[5].querySelector('a');
            return { id: `r-${index}`, taskName: (taskLink?.textContent || cells[0].textContent).trim(), questionNumber: cells[1].textContent.trim(), submissionDate: cells[3].textContent.trim(), result: cells[4].textContent.trim(), scoreText: (scoreLink?.textContent || cells[5].textContent).trim(), comment: cells[2].querySelector('.comment,.text-report-reader.comment')?.textContent.trim() || null, taskURL: taskLink ? new URL(taskLink.href, location.href).href : null, scoreURL: scoreLink ? new URL(scoreLink.href, location.href).href : null };
          }).filter(Boolean));
        })()
        """#
        struct Payload: Decodable { let id: String; let taskName: String; let questionNumber: String; let submissionDate: String; let result: String; let scoreText: String; let comment: String?; let taskURL: URL?; let scoreURL: URL? }
        evaluate(script) { data in
            guard let data, let values = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.reports = values.map { CourseReportItem(id: $0.id, taskName: $0.taskName, questionNumber: $0.questionNumber, submissionDate: $0.submissionDate, result: $0.result, scoreText: $0.scoreText, comment: $0.comment, taskURL: $0.taskURL, scoreURL: $0.scoreURL) }
            self.isLoading = false
        }
    }

    private func extractDepartments(from webView: WKWebView) {
        let script = #"""
        (() => {
          const result = [];
          document.querySelectorAll('h4.list-group-item-heading').forEach((heading, index) => {
            const container = heading.closest('.list-group-item,.row,.panel') || heading.parentElement;
            const link = [...(container?.querySelectorAll('a[href*="PublicSubjects"]') || [])][0];
            if (link) result.push({ id: new URL(link.href, location.href).searchParams.get('department_id') || `${index}`, name: heading.textContent.trim(), url: new URL(link.href, location.href).href });
          });
          return JSON.stringify(result);
        })()
        """#
        struct Payload: Decodable { let id: String; let name: String; let url: URL }
        evaluate(script) { data in
            guard let data, let values = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.departments = values.map { SyllabusDepartment(id: $0.id, name: $0.name, url: $0.url) }
            self.isLoading = false
        }
    }

    private func extractSubjects(from webView: WKWebView) {
        let script = #"""
        (() => JSON.stringify([...document.querySelectorAll('tr[data-course-value], table tbody tr')].map((row, index) => {
          const link = row.querySelector('.subject-item a.mcc-show, a[href*="PublicSyllabus"]');
          const name = (link?.textContent || row.querySelector('.subject-item')?.textContent || '').replace(/\s+/g, ' ').trim();
          const tdElements = [...row.querySelectorAll('td')];
          const cells = tdElements.map(td => td.textContent.replace(/\s+/g, ' ').trim());
          const href = link ? new URL(link.href, location.href) : null;
          const categoryCell = tdElements.find(td => td.classList.contains('c1') && !td.classList.contains('c1m'));
          let targetYear = '';
          for (let year = 1; year <= 5; year++) {
            if ([...row.querySelectorAll(`.c${year}m`)].some(cell => (parseInt(cell.textContent, 10) || 0) > 0)) { targetYear = `${year}年`; break; }
          }
          const teachers = [...cells].reverse().find(value => value.length > 1 && /[ぁ-んァ-ヶ一-龠]/.test(value) && value !== name) || '';
          return { id: row.getAttribute('data-course-value') || `${index}`, name, detail: cells.filter(v => v && v !== name).slice(0, 4).join(' ・ '), url: href?.href || null, category: categoryCell?.textContent.trim() || cells[0] || 'その他', requirement: cells[1] || '', code: href?.searchParams.get('subject_code') || '', teachers, targetYear };
        }).filter(item => item.name)))()
        """#
        struct Payload: Decodable { let id: String; let name: String; let detail: String; let url: URL?; let category: String; let requirement: String; let code: String; let teachers: String; let targetYear: String }
        evaluate(script) { data in
            guard let data, let values = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.subjects = values.map { SyllabusSubject(id: $0.id, name: $0.name, detail: $0.detail, url: $0.url, category: $0.category, requirement: $0.requirement, code: $0.code, teachers: $0.teachers, targetYear: $0.targetYear) }
            self.isLoading = false
        }
    }

    private func extractSyllabusDetail(from webView: WKWebView) {
        let script = #"""
        (() => {
          const clean = value => (value || '').replace(/\s+/g, ' ').trim();
          const title = clean(document.querySelector('h1,h2,.page-header,.subject-name')?.textContent || document.title);
          const rows = [];
          document.querySelectorAll('table tr').forEach((row, index) => {
            const cells = [...row.querySelectorAll('th,td')].map(cell => clean(cell.textContent)).filter(Boolean);
            if (cells.length >= 2) rows.push({ id: `${index}`, label: cells[0], value: cells.slice(1).join(' ・ ') });
          });
          document.querySelectorAll('dl').forEach((list, listIndex) => {
            const labels = [...list.querySelectorAll('dt')];
            labels.forEach((label, index) => {
              const value = label.nextElementSibling?.matches('dd') ? clean(label.nextElementSibling.textContent) : '';
              if (value) rows.push({ id: `dl-${listIndex}-${index}`, label: clean(label.textContent), value });
            });
          });
          return JSON.stringify({ title, rows });
        })()
        """#
        struct Payload: Decodable { let title: String; let rows: [Row] }
        struct Row: Decodable { let id: String; let label: String; let value: String }
        evaluate(script) { data in
            guard let data, let value = try? JSONDecoder().decode(Payload.self, from: data) else { return }
            self.detailTitle = value.title
            self.detailRows = value.rows.map { SyllabusDetailRow(id: $0.id, label: $0.label, value: $0.value) }
            self.isLoading = false
        }
    }
}

struct CourseContentsView: View {
    let course: WebClassCourse
    @StateObject private var loader: DOMPageLoader
    @State private var query = ""
    @State private var selectedCategory: String?
    @State private var showOnlyAvailable = false
    @State private var selectedPage = 0

    init(course: WebClassCourse) {
        self.course = course
        _loader = StateObject(wrappedValue: DOMPageLoader(url: course.url, kind: .course))
    }

    private var categories: [String] { Array(Set(loader.courseSections.flatMap(\.items).map(\.category).filter { !$0.isEmpty })).sorted() }
    private var filteredSections: [CourseContentSection] {
        loader.courseSections.compactMap { section in
            let items = section.items.filter {
                (query.isEmpty || $0.title.localizedCaseInsensitiveContains(query)) &&
                (selectedCategory == nil || $0.category == selectedCategory) &&
                (!showOnlyAvailable || !$0.isDisabled)
            }
            return items.isEmpty ? nil : CourseContentSection(id: section.id, title: section.title, items: items)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("コース", selection: $selectedPage) {
                Label("教材", systemImage: "books.vertical.fill").tag(0)
                Label("成績", systemImage: "chart.bar.fill").tag(1)
                Label("レポート", systemImage: "doc.text.fill").tag(2)
            }.pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 10)
            if selectedPage == 1 {
                CourseScoresView(course: course)
            } else if selectedPage == 2 {
                CourseReportsView(course: course)
            } else { contentList }
        }
        .navigationTitle(course.title).navigationBarTitleDisplayMode(.inline)
        .appScreenBackground()
    }

    @ViewBuilder private var contentList: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("教材を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if loader.courseSections.isEmpty { ContentUnavailableView("教材がありません", systemImage: "tray") }
            else {
                ScrollView {
                    LazyVStack(spacing: 11) {
                        HStack {
                            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                            TextField("教材名で検索", text: $query)
                            if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) } }
                        }.padding(.horizontal, 13).frame(height: 46).appCard(cornerRadius: 14)
                        HStack(spacing: 8) {
                            if !categories.isEmpty { FilterMenu(title: "カテゴリ", selected: $selectedCategory, options: categories) }
                            Button { showOnlyAvailable.toggle() } label: {
                                Label("利用可能", systemImage: showOnlyAvailable ? "checkmark.circle.fill" : "circle")
                                    .font(.caption.weight(.semibold)).foregroundStyle(showOnlyAvailable ? Color.indigo : Color.primary)
                                    .padding(.horizontal, 10).frame(height: 36).appCard(cornerRadius: 11)
                            }.buttonStyle(.plain)
                            Spacer()
                        }
                        ForEach(filteredSections) { section in
                            AppSectionHeader(title: section.title, subtitle: "\(section.items.count)件")
                            ForEach(section.items) { item in
                                CourseContentCardView(item: item)
                            }
                        }
                    }.padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
            }
        }
    }
}

private struct CourseContentCardView: View {
    let item: CourseContentItem
    var body: some View {
        VStack(spacing: 0) {
            if let url = item.url, !item.isDisabled {
                NavigationLink { WebClassView(startURL: url) } label: { ContentRow(item: item).padding(13) }.buttonStyle(.plain)
            } else { ContentRow(item: item).padding(13).opacity(0.55) }
            if item.usageCount != nil || item.availablePeriod != nil || item.detailURL != nil || item.historyURL != nil {
                Divider().opacity(0.5)
                HStack(spacing: 9) {
                    if let count = item.usageCount {
                        Label("利用 \(count)回", systemImage: item.hasSubmitted ? "checkmark.circle.fill" : "clock")
                            .font(.caption).foregroundStyle(item.hasSubmitted ? Color.green : Color.secondary)
                    }
                    if let period = item.availablePeriod, !period.isEmpty {
                        Text(period).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer()
                    if let detail = item.detailURL {
                        NavigationLink { WebClassView(startURL: detail) } label: { Label("詳細", systemImage: "info.circle").font(.caption.weight(.semibold)) }
                    }
                    if let history = item.historyURL {
                        NavigationLink { WebClassView(startURL: history) } label: { Label("履歴", systemImage: "clock.arrow.circlepath").font(.caption.weight(.semibold)) }
                    }
                }.padding(.horizontal, 13).padding(.vertical, 9)
            }
        }.appCard().opacity(item.isDisabled ? 0.62 : 1)
    }
}

private struct CourseScoresView: View {
    let course: WebClassCourse
    @StateObject private var loader: DOMPageLoader
    init(course: WebClassCourse) {
        self.course = course
        let url = URL(string: "https://webclass.kosen-k.go.jp/webclass/course.php/\(course.code)/scores")!
        _loader = StateObject(wrappedValue: DOMPageLoader(url: url, kind: .scores))
    }
    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("成績を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if loader.scoreGroups.isEmpty { ContentUnavailableView("成績がありません", systemImage: "chart.bar") }
            else {
                ScrollView {
                    LazyVStack(spacing: 11) {
                        ForEach(loader.scoreGroups) { group in
                            AppSectionHeader(title: group.name, subtitle: group.total.map { "合計 \(formatNumber($0))" })
                            ForEach(group.items) { item in
                                ScoreCard(item: item)
                            }
                        }
                    }.padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
            }
        }
    }
    private func formatNumber(_ value: Double) -> String { value.rounded() == value ? String(Int(value)) : String(format: "%.1f", value) }
}

private struct ScoreCard: View {
    let item: CourseScoreItem
    var body: some View {
        HStack(spacing: 13) {
            AppIconTile(symbol: "chart.bar.fill", color: .purple)
            VStack(alignment: .leading, spacing: 5) {
                Text(item.title).font(.headline).lineLimit(2)
                if let average = item.average { Text("平均 \(String(format: "%.1f", average))").font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
            Text(item.scoreText.isEmpty ? "—" : item.scoreText).font(.title3.bold()).foregroundStyle(item.score == nil ? Color.secondary : Color.purple)
        }.padding(13).frame(minHeight: 78).appCard()
    }
}

private struct CourseReportsView: View {
    let course: WebClassCourse
    @StateObject private var loader: DOMPageLoader
    init(course: WebClassCourse) {
        self.course = course
        let url = URL(string: "https://webclass.kosen-k.go.jp/webclass/course.php/\(course.code)/my-reports")!
        _loader = StateObject(wrappedValue: DOMPageLoader(url: url, kind: .reports))
    }
    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("レポートを取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if loader.reports.isEmpty { ContentUnavailableView("提出済みレポートはありません", systemImage: "doc.text") }
            else {
                ScrollView {
                    LazyVStack(spacing: 11) {
                        ForEach(loader.reports) { report in
                            VStack(alignment: .leading, spacing: 9) {
                                HStack { Text(report.taskName).font(.headline); Spacer(); Text(report.result).font(.caption.bold()).foregroundStyle(report.result == "○" || report.result == "OK" ? Color.green : Color.secondary) }
                                HStack { if !report.questionNumber.isEmpty { Text("設問 \(report.questionNumber)") }; Spacer(); Text(report.scoreText) }.font(.caption).foregroundStyle(.secondary)
                                if !report.submissionDate.isEmpty { Label(report.submissionDate, systemImage: "calendar").font(.caption).foregroundStyle(.secondary) }
                                if let comment = report.comment, !comment.isEmpty { Text(comment).font(.subheadline).padding(9).frame(maxWidth: .infinity, alignment: .leading).background(.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 9)) }
                                if report.taskURL != nil || report.scoreURL != nil {
                                    HStack {
                                        Spacer()
                                        if let url = report.taskURL { NavigationLink { WebClassView(startURL: url) } label: { Label("課題", systemImage: "doc.text").font(.caption.weight(.semibold)) } }
                                        if let url = report.scoreURL { NavigationLink { WebClassView(startURL: url) } label: { Label("採点詳細", systemImage: "chart.bar.doc.horizontal").font(.caption.weight(.semibold)) } }
                                    }
                                }
                            }.padding(14).appCard()
                        }
                    }.padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
            }
        }
    }
}

private struct ContentRow: View {
    let item: CourseContentItem
    private var appearance: CourseAppearance { .assignment(item.category) }
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: appearance.symbol)
                .foregroundStyle(appearance.color)
                .frame(width: 38, height: 38)
                .background(appearance.color.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))
            VStack(alignment: .leading, spacing: 3) {
                HStack { Text(item.title); if item.isNew { Text("NEW").font(.caption2.bold()).foregroundStyle(.red) }; if item.isUnread { Text("未実施").font(.caption2.bold()).foregroundStyle(.orange) } }
                if !item.category.isEmpty { Text(item.category).font(.caption).foregroundStyle(.secondary) }
            }
        }.padding(.vertical, 3)
    }
}

private struct PlaceholderList: View {
    var body: some View {
        List(0..<6, id: \.self) { _ in RoundedRectangle(cornerRadius: 10).fill(.quaternary).frame(height: 52) }
            .redacted(reason: .placeholder).allowsHitTesting(false).scrollContentBackground(.hidden).appScreenBackground()
    }
}

struct SyllabusView: View {
    private static let rootURL = URL(string: "https://syllabus.kosen-k.go.jp/Pages/PublicDepartments?school_id=14")!
    @StateObject private var loader = DOMPageLoader(url: rootURL, kind: .departments)
    @State private var query = ""

    private var filtered: [SyllabusDepartment] {
        query.isEmpty ? loader.departments : loader.departments.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }
    private var groups: [DepartmentGroup] {
        let general = filtered.filter { $0.name.contains("一般") || $0.name.contains("総合") }
        let advanced = filtered.filter { $0.name.contains("専攻") }
        let regular = filtered.filter { !general.contains($0) && !advanced.contains($0) }
        return [DepartmentGroup(title: "本科", departments: regular), DepartmentGroup(title: "一般科目", departments: general), DepartmentGroup(title: "専攻科", departments: advanced)].filter { !$0.departments.isEmpty }
    }

    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("シラバスを取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(groups) { group in
                            AppSectionHeader(title: group.title, subtitle: "\(group.departments.count)件")
                            ForEach(group.departments) { department in
                                NavigationLink { SyllabusSubjectsView(department: department) } label: {
                                    HStack(spacing: 13) {
                                        AppIconTile(symbol: "building.columns.fill", color: .orange)
                                        Text(department.name).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading).lineLimit(2)
                                        Spacer()
                                        Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
                                    }.padding(13).frame(height: 74).appCard()
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(16)
                }.searchable(text: $query, prompt: "学科を検索").refreshable { loader.reload() }.appScreenBackground()
            }
        }.navigationTitle("シラバス")
    }
}

struct SyllabusSubjectsView: View {
    let department: SyllabusDepartment
    @StateObject private var loader: DOMPageLoader
    @State private var query = ""
    @State private var selectedCategory: String?
    @State private var selectedRequirement: String?
    @State private var selectedTargetYear: String?

    init(department: SyllabusDepartment) {
        self.department = department
        _loader = StateObject(wrappedValue: DOMPageLoader(url: department.url, kind: .subjects))
    }

    private var categories: [String] { Array(Set(loader.subjects.map { $0.category.isEmpty ? "その他" : $0.category })).sorted() }
    private var requirements: [String] { Array(Set(loader.subjects.map(\.requirement).filter { !$0.isEmpty })).sorted() }
    private var targetYears: [String] { Array(Set(loader.subjects.map(\.targetYear).filter { !$0.isEmpty })).sorted() }
    private var filtered: [SyllabusSubject] {
        loader.subjects.filter {
            (query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) || $0.code.localizedCaseInsensitiveContains(query) || $0.teachers.localizedCaseInsensitiveContains(query)) &&
            (selectedCategory == nil || ($0.category.isEmpty ? "その他" : $0.category) == selectedCategory) &&
            (selectedRequirement == nil || $0.requirement == selectedRequirement) &&
            (selectedTargetYear == nil || $0.targetYear == selectedTargetYear)
        }
    }

    private var grouped: [SubjectGroup] {
        Dictionary(grouping: filtered, by: { $0.category.isEmpty ? "その他" : $0.category })
            .map { SubjectGroup(category: $0.key, subjects: $0.value) }
            .sorted { $0.category < $1.category }
    }

    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("科目を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else {
                ScrollView {
                    LazyVStack(spacing: 11) {
                        HStack(spacing: 8) {
                            FilterMenu(title: "科目区分", selected: $selectedCategory, options: categories)
                            if !requirements.isEmpty { FilterMenu(title: "必修区分", selected: $selectedRequirement, options: requirements) }
                            if !targetYears.isEmpty { FilterMenu(title: "対象学年", selected: $selectedTargetYear, options: targetYears) }
                        }
                        ForEach(grouped) { group in
                            AppSectionHeader(title: group.category, subtitle: "\(group.subjects.count)件")
                            ForEach(group.subjects) { subject in
                                if let url = subject.url {
                                    NavigationLink { SyllabusDetailView(subject: subject, url: url) } label: { SubjectRow(subject: subject).padding(13).frame(height: 92).appCard() }.buttonStyle(.plain)
                                } else { SubjectRow(subject: subject).padding(13).frame(height: 92).appCard() }
                            }
                        }
                    }.padding(16)
                }.searchable(text: $query, prompt: "科目名・コード・教員名で検索").appScreenBackground()
            }
        }.navigationTitle(department.name).navigationBarTitleDisplayMode(.inline)
    }
}

private struct FilterMenu: View {
    let title: String
    @Binding var selected: String?
    let options: [String]
    var body: some View {
        Menu {
            Button("すべて") { selected = nil }
            ForEach(options, id: \.self) { value in Button(value) { selected = value } }
        } label: {
            HStack(spacing: 5) {
                if selected != nil { Image(systemName: "checkmark") }
                Text(selected ?? title).lineLimit(1)
                Image(systemName: "chevron.down").font(.caption2)
            }.font(.caption.weight(.semibold)).foregroundStyle(.primary).padding(.horizontal, 10).frame(height: 36).appCard(cornerRadius: 11)
        }
    }
}

struct SyllabusDetailView: View {
    let subject: SyllabusSubject
    @StateObject private var loader: DOMPageLoader

    init(subject: SyllabusSubject, url: URL) {
        self.subject = subject
        _loader = StateObject(wrappedValue: DOMPageLoader(url: url, kind: .syllabusDetail))
    }

    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("シラバスを取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else {
                List {
                    ForEach(loader.detailRows) { row in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(row.label).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                            Text(row.value).textSelection(.enabled)
                        }.padding(.vertical, 5)
                    }
                }.scrollContentBackground(.hidden)
            }
        }
        .navigationTitle(loader.detailTitle.isEmpty ? subject.name : loader.detailTitle)
        .navigationBarTitleDisplayMode(.inline)
        .appScreenBackground()
    }
}

private struct SubjectRow: View {
    let subject: SyllabusSubject
    private var appearance: CourseAppearance { .course(subject.name) }
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: appearance.symbol).foregroundStyle(appearance.color)
                .frame(width: 42, height: 42).background(appearance.color.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 5) {
            Text(subject.name).font(.headline).lineLimit(2)
            if !subject.detail.isEmpty { Text(subject.detail).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            }
        }.padding(.vertical, 4)
    }
}
