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
    let totalMy: Double?
    let totalCourse: Double?
}

struct CourseScorePageMeta: Hashable {
    let displayType: String
    let startDate: String
    let endDate: String
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
    @Published var scoreMeta = CourseScorePageMeta(displayType: "", startDate: "", endDate: "")
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

    func selectScoreType(_ type: String) {
        guard case .scores = kind, ["scoremax", "scoreave", "scoremin", "scoresum", "execnum", "time"].contains(type) else { return }
        isLoading = true
        let script = """
        (() => { const form=document.getElementById('PersonalScoreSheetSearch'); const radio=form?.querySelector('input[name=\"showdata\"][value=\"\(type)\"]'); if(!form||!radio)return false; radio.checked=true; form.submit(); return true; })()
        """
        webView.evaluateJavaScript(script) { _, error in
            if let error { Task { @MainActor in self.fail(error) } }
        }
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
          if (!table) return JSON.stringify({page:{displayType:'',startDate:'',endDate:''},groups:[]});
          const form=document.querySelector('form#PersonalScoreSheetSearch');
          const page={
            displayType: form?.querySelector('input[name="showdata"]:checked')?.value || '',
            startDate: form?.querySelector('input[name="summaryOption[dateRangeStart]"]')?.value || '',
            endDate: form?.querySelector('input[name="summaryOption[dateRangeEnd]"]')?.value || ''
          };
          const groups = []; let current = () => groups[groups.length - 1];
          [...table.querySelectorAll('tr')].forEach((row, index) => {
            if (row.classList.contains('unittitle')) { groups.push({ id: `g-${index}`, name: row.querySelector('th')?.textContent.trim() || '成績一覧', items: [], totalMy: null, totalCourse: null }); return; }
            if (row.classList.contains('foot')) { const cells=row.querySelectorAll('td'); if (current()) { const my=parseFloat(cells[0]?.textContent); const course=parseFloat(cells[1]?.textContent); current().totalMy=Number.isNaN(my)?null:my; current().totalCourse=Number.isNaN(course)?null:course; } return; }
            const titleCell = row.querySelector('th.contents-title'); if (!titleCell) return;
            if (!current()) groups.push({ id: 'scores', name: '成績一覧', items: [], totalMy: null, totalCourse: null });
            const cells = row.querySelectorAll('td'); if (cells.length < 2) return;
            const scoreLink = cells[0].querySelector('a'); const raw = (scoreLink?.textContent || cells[0].textContent || '').trim();
            const scoreMatch = raw.match(/[0-9]+(?:\.[0-9]+)?/); const average = parseFloat(cells[1].textContent);
            current().items.push({ id: `s-${index}`, title: (titleCell.querySelector('a')?.textContent || titleCell.textContent).trim(), scoreText: raw, score: scoreMatch ? parseFloat(scoreMatch[0]) : null, average: Number.isNaN(average) ? null : average, url: scoreLink ? new URL(scoreLink.href, location.href).href : null });
          });
          return JSON.stringify({page,groups});
        })()
        """#
        struct Payload: Decodable { let page: Page; let groups: [Group] }
        struct Page: Decodable { let displayType: String; let startDate: String; let endDate: String }
        struct Group: Decodable { let id: String; let name: String; let items: [Item]; let totalMy: Double?; let totalCourse: Double? }
        struct Item: Decodable { let id: String; let title: String; let scoreText: String; let score: Double?; let average: Double?; let url: URL? }
        evaluate(script) { data in
            guard let data, let value = try? JSONDecoder().decode(Payload.self, from: data) else { return }
            self.scoreMeta = CourseScorePageMeta(displayType: value.page.displayType, startDate: value.page.startDate, endDate: value.page.endDate)
            self.scoreGroups = value.groups.map { CourseScoreGroup(id: $0.id, name: $0.name, items: $0.items.map { CourseScoreItem(id: $0.id, title: $0.title, scoreText: $0.scoreText, score: $0.score, average: $0.average, url: $0.url) }, totalMy: $0.totalMy, totalCourse: $0.totalCourse) }
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
                NavigationLink { WebClassView(startURL: url) } label: {
                    HStack(spacing: 0) {
                        ContentRow(item: item)
                        Spacer(minLength: 10)
                        Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
                    }.padding(14).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }.buttonStyle(.plain)
            } else { ContentRow(item: item).padding(14).opacity(0.55) }
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
                        NavigationLink { NativeContentDetailView(url: detail) } label: {
                            Label("詳細", systemImage: "info.circle").font(.caption.weight(.semibold)).padding(.horizontal, 10).frame(height: 34).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                    }
                    if let history = item.historyURL {
                        NavigationLink { NativeContentHistoryView(url: history) } label: {
                            Label("履歴", systemImage: "clock.arrow.circlepath").font(.caption.weight(.semibold)).padding(.horizontal, 10).frame(height: 34).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 14).padding(.vertical, 10)
            }
        }.appCard(cornerRadius: 16).opacity(item.isDisabled ? 0.62 : 1)
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
                        ScoreSummaryCard(groups: loader.scoreGroups, meta: loader.scoreMeta)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(scoreTypes, id: \.0) { entry in
                                    Button { loader.selectScoreType(entry.0) } label: {
                                        Text(entry.1).font(.caption.weight(.semibold)).foregroundStyle(loader.scoreMeta.displayType == entry.0 ? Color.white : Color.primary)
                                            .padding(.horizontal, 12).frame(height: 34)
                                            .background(loader.scoreMeta.displayType == entry.0 ? Color.indigo : Color.clear, in: Capsule())
                                            .contentShape(Capsule())
                                    }.buttonStyle(.plain).appCard(cornerRadius: 17)
                                }
                            }
                        }
                        ForEach(loader.scoreGroups) { group in
                            AppSectionHeader(title: group.name, subtitle: "\(group.items.count)件")
                            if group.totalMy != nil || group.totalCourse != nil { ScoreTotalCard(my: group.totalMy, course: group.totalCourse) }
                            ForEach(group.items) { item in
                                ScoreCard(item: item)
                            }
                        }
                    }.padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
            }
        }
    }
    private let scoreTypes = [("scoremax", "最大得点"), ("scoreave", "平均得点"), ("scoremin", "最少得点"), ("scoresum", "合計得点"), ("execnum", "実施回数"), ("time", "利用時間")]
}

private struct ScoreSummaryCard: View {
    let groups: [CourseScoreGroup]
    let meta: CourseScorePageMeta
    private var items: [CourseScoreItem] { groups.flatMap(\.items) }
    private var scores: [Double] { items.compactMap(\.score) }
    private var courseAverages: [Double] { items.compactMap(\.average) }
    private var average: Double? { scores.isEmpty ? nil : scores.reduce(0, +) / Double(scores.count) }
    private var courseAverage: Double? { courseAverages.isEmpty ? nil : courseAverages.reduce(0, +) / Double(courseAverages.count) }
    private var aboveAverage: Int { items.filter { item in guard let score = item.score, let average = item.average else { return false }; return score >= average }.count }
    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack {
                Label("成績統計", systemImage: "chart.bar.xaxis").font(.headline).foregroundStyle(.indigo)
                Spacer()
                if !meta.startDate.isEmpty || !meta.endDate.isEmpty { Text("\(meta.startDate) 〜 \(meta.endDate)").font(.caption2).foregroundStyle(.secondary) }
            }
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 9) {
                ScoreMetric(title: "実施済み", value: "\(scores.count)/\(items.count)", color: .indigo)
                ScoreMetric(title: "自分の平均", value: value(average), color: .purple)
                ScoreMetric(title: "コース平均", value: value(courseAverage), color: .teal)
                ScoreMetric(title: "最高", value: value(scores.max()), color: .green)
                ScoreMetric(title: "最低", value: value(scores.min()), color: .orange)
                ScoreMetric(title: "平均以上", value: "\(aboveAverage)件", color: .blue)
            }
        }.padding(15).appCard(cornerRadius: 18)
    }
    private func value(_ number: Double?) -> String { guard let number else { return "—" }; return number.rounded() == number ? "\(Int(number))" : String(format: "%.1f", number) }
}

private struct ScoreMetric: View {
    let title: String; let value: String; let color: Color
    var body: some View { VStack(spacing: 4) { Text(title).font(.caption2).foregroundStyle(.secondary); Text(value).font(.headline.monospacedDigit()).foregroundStyle(color) }.padding(.vertical, 10).frame(maxWidth: .infinity).background(color.opacity(0.09), in: RoundedRectangle(cornerRadius: 11)) }
}

private struct ScoreTotalCard: View {
    let my: Double?; let course: Double?
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) { Text("カテゴリ合計").font(.subheadline.bold()); if let my, let course { Text("平均差 \(difference(my - course))").font(.caption.bold()).foregroundStyle(my >= course ? Color.green : Color.red) } }
            Spacer()
            if let course { VStack(alignment: .trailing) { Text("コース平均").font(.caption2).foregroundStyle(.secondary); Text(format(course)).font(.headline.monospacedDigit()) } }
            if let my { VStack(alignment: .trailing) { Text("自分").font(.caption2).foregroundStyle(.secondary); Text(format(my)).font(.title3.bold().monospacedDigit()).foregroundStyle(.indigo) } }
        }.padding(14).background(.indigo.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
    }
    private func format(_ value: Double) -> String { value.rounded() == value ? "\(Int(value))" : String(format: "%.1f", value) }
    private func difference(_ value: Double) -> String { (value >= 0 ? "+" : "") + String(format: "%.1f", value) }
}

private struct ScoreCard: View {
    let item: CourseScoreItem
    var body: some View {
        HStack(spacing: 0) {
            Rectangle().fill(accent).frame(width: 4)
            VStack(alignment: .leading, spacing: 9) {
                HStack(alignment: .top) { Text(item.title).font(.headline).lineLimit(2); Spacer(); Text(item.scoreText.isEmpty ? "—" : item.scoreText).font(.headline.bold()).foregroundStyle(accent).padding(.horizontal, 9).padding(.vertical, 4).background(accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 8)) }
                if let average = item.average {
                    HStack { if let score = item.score { Label(score >= average ? "平均以上" : "平均未満", systemImage: score >= average ? "arrow.up.right" : "arrow.down.right").foregroundStyle(score >= average ? Color.green : Color.red); Spacer(); Text("コース平均 \(String(format: "%.1f", average))") } else { Text("未受験・未提出"); Spacer(); Text("コース平均 \(String(format: "%.1f", average))") } }.font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                }
            }.padding(14)
        }.frame(minHeight: 86).appCard(cornerRadius: 16).clipShape(RoundedRectangle(cornerRadius: 16))
    }
    private var accent: Color { guard let score = item.score else { return .gray }; guard let average = item.average else { return .purple }; return score >= average ? .green : .orange }
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
                Text(item.title).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading).lineLimit(2)
                HStack(spacing: 6) {
                    if !item.category.isEmpty { Text(item.category).foregroundStyle(.secondary) }
                    if item.isNew { Text("NEW").foregroundStyle(.red) }
                    if item.isUnread { Text("未実施").foregroundStyle(.orange) }
                }.font(.caption.weight(.semibold))
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
    private var groups: [DepartmentGroup] {
        let general = loader.departments.filter { $0.name.contains("一般") || $0.name.contains("総合") }
        let advanced = loader.departments.filter { $0.name.contains("専攻") }
        let regular = loader.departments.filter { !general.contains($0) && !advanced.contains($0) }
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
                                    let appearance = CourseAppearance.department(department.name)
                                    HStack(spacing: 13) {
                                        AppIconTile(symbol: appearance.symbol, color: appearance.color)
                                        Text(department.name).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading).lineLimit(2)
                                        Spacer()
                                        Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
                                    }.padding(13).frame(height: 74).appCard()
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
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
                        HStack(spacing: 10) {
                            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                            TextField("科目名・コード・教員名で検索", text: $query)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            if !query.isEmpty {
                                Button { query = "" } label: {
                                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 48)
                        .appCard(cornerRadius: 14)
                        HStack(spacing: 8) {
                            FilterMenu(title: "科目区分", selected: $selectedCategory, options: categories)
                            if !requirements.isEmpty { FilterMenu(title: "必修区分", selected: $selectedRequirement, options: requirements) }
                            if !targetYears.isEmpty { FilterMenu(title: "対象学年", selected: $selectedTargetYear, options: targetYears) }
                        }
                        ForEach(grouped) { group in
                            AppSectionHeader(title: group.category, subtitle: "\(group.subjects.count)件")
                            ForEach(group.subjects) { subject in
                                if let url = subject.url {
                                    NavigationLink { SyllabusDetailView(subject: subject, url: url) } label: { SubjectRow(subject: subject).padding(14).frame(minHeight: 98).appCard(cornerRadius: 16) }.buttonStyle(.plain)
                                } else { SubjectRow(subject: subject).padding(14).frame(minHeight: 98).appCard(cornerRadius: 16) }
                            }
                        }
                    }.padding(16)
                }.appScreenBackground()
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
                Spacer(minLength: 3)
                Image(systemName: "chevron.down").font(.caption2)
            }.font(.caption.weight(.semibold)).foregroundStyle(.primary).padding(.horizontal, 10).frame(maxWidth: .infinity).frame(height: 40).appCard(cornerRadius: 11).contentShape(Rectangle())
        }
        .frame(maxWidth: .infinity)
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
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 14) {
                            SyllabusHeroCard(subject: subject, title: loader.detailTitle)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(sectionGroups, id: \.0) { section in
                                        Button { withAnimation(.easeInOut) { proxy.scrollTo(section.0, anchor: .top) } } label: {
                                            Text(section.0).font(.caption.weight(.semibold)).padding(.horizontal, 12).frame(height: 34).appCard(cornerRadius: 17).contentShape(Capsule())
                                        }.buttonStyle(.plain)
                                    }
                                }
                            }
                            ForEach(sectionGroups, id: \.0) { section in
                                VStack(alignment: .leading, spacing: 11) {
                                    AppSectionHeader(title: section.0, subtitle: "\(section.1.count)項目", symbol: sectionSymbol(section.0))
                                    if section.0 == "授業計画" {
                                        ForEach(Array(section.1.enumerated()), id: \.element.id) { index, row in SyllabusLessonRow(index: index + 1, row: row) }
                                    } else {
                                        VStack(spacing: 0) {
                                            ForEach(Array(section.1.enumerated()), id: \.element.id) { index, row in
                                                VStack(alignment: .leading, spacing: 6) {
                                                    Text(row.label).font(.caption.weight(.semibold)).foregroundStyle(.indigo)
                                                    Text(row.value).font(.body).frame(maxWidth: .infinity, alignment: .leading).textSelection(.enabled)
                                                }.padding(14)
                                                if index < section.1.count - 1 { Divider().padding(.leading, 14) }
                                            }
                                        }.appCard(cornerRadius: 16)
                                    }
                                }.id(section.0)
                            }
                        }.padding(16)
                    }
                    .appScreenBackground()
                }
            }
        }
        .navigationTitle(loader.detailTitle.isEmpty ? subject.name : loader.detailTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var sectionGroups: [(String, [SyllabusDetailRow])] {
        let ordered = ["科目基礎情報", "到達目標", "ルーブリック", "教育方法等", "授業計画", "評価割合", "その他"]
        let grouped = Dictionary(grouping: loader.detailRows, by: { section(for: $0.label) })
        return ordered.compactMap { title in grouped[title].map { (title, $0) } }
    }

    private func section(for label: String) -> String {
        if label.contains("到達目標") || label.contains("目標") { return "到達目標" }
        if label.contains("ルーブリック") || label.contains("達成度") { return "ルーブリック" }
        if label.contains("週") || label.contains("授業計画") { return "授業計画" }
        if label.contains("評価") || label.contains("割合") { return "評価割合" }
        if ["概要", "進め方", "方法", "注意点"].contains(where: label.contains) { return "教育方法等" }
        if ["学校", "年度", "科目", "授業形態", "学科", "単位", "教員", "教科書", "対象学年", "開講"].contains(where: label.contains) { return "科目基礎情報" }
        return "その他"
    }

    private func sectionSymbol(_ title: String) -> String {
        switch title { case "到達目標": "target"; case "ルーブリック": "checklist.checked"; case "教育方法等": "person.2.fill"; case "授業計画": "calendar"; case "評価割合": "chart.pie.fill"; case "科目基礎情報": "info.circle.fill"; default: "doc.text.fill" }
    }
}

private struct SyllabusHeroCard: View {
    let subject: SyllabusSubject
    let title: String
    private var appearance: CourseAppearance { .course(subject.name) }
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            AppIconTile(symbol: appearance.symbol, color: appearance.color, size: 54)
            VStack(alignment: .leading, spacing: 7) {
                Text(title.isEmpty ? subject.name : title).font(.title3.bold()).lineLimit(3)
                if !subject.teachers.isEmpty { Label(subject.teachers, systemImage: "person.fill").font(.caption).foregroundStyle(.secondary) }
                HStack(spacing: 6) {
                    if !subject.targetYear.isEmpty { Text(subject.targetYear) }
                    if !subject.requirement.isEmpty { Text(subject.requirement) }
                    if !subject.category.isEmpty { Text(subject.category) }
                }.font(.caption2.weight(.semibold)).foregroundStyle(appearance.color)
            }
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).appCard(cornerRadius: 18)
    }
}

private struct SyllabusLessonRow: View {
    let index: Int
    let row: SyllabusDetailRow
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(index)").font(.caption.bold()).foregroundStyle(.indigo).frame(width: 32, height: 32).background(.indigo.opacity(0.12), in: RoundedRectangle(cornerRadius: 9))
            VStack(alignment: .leading, spacing: 5) { Text(row.label).font(.subheadline.bold()); Text(row.value).font(.subheadline).foregroundStyle(.secondary).textSelection(.enabled) }
        }.padding(13).frame(maxWidth: .infinity, alignment: .leading).appCard(cornerRadius: 14)
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
                Text(subject.name).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading).lineLimit(2)
                if !subject.detail.isEmpty { Text(subject.detail).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                HStack(spacing: 6) {
                    if !subject.requirement.isEmpty { Text(subject.requirement) }
                    if !subject.targetYear.isEmpty { Text(subject.targetYear) }
                }.font(.caption2.weight(.semibold)).foregroundStyle(.indigo)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
        }.padding(.vertical, 4)
    }
}
