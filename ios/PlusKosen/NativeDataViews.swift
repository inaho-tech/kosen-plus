import SwiftUI
import WebKit

struct CourseContentItem: Identifiable, Hashable {
    let id: String
    let title: String
    let category: String
    let url: URL?
    let isNew: Bool
    let isDisabled: Bool
}

struct CourseContentSection: Identifiable, Hashable {
    let id: String
    let title: String
    let items: [CourseContentItem]
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
}

struct SyllabusDetailRow: Identifiable, Hashable {
    let id: String
    let label: String
    let value: String
}

@MainActor
final class DOMPageLoader: NSObject, ObservableObject, WKNavigationDelegate {
    enum Kind { case course, departments, subjects, syllabusDetail }

    @Published var isLoading = true
    @Published var errorMessage: String?
    @Published var courseSections: [CourseContentSection] = []
    @Published var departments: [SyllabusDepartment] = []
    @Published var subjects: [SyllabusSubject] = []
    @Published var detailTitle = ""
    @Published var detailRows: [SyllabusDetailRow] = []

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
                isDisabled: !link
              };
            })
          }));
          return JSON.stringify(sections);
        })()
        """#
        struct Payload: Decodable { let id: String; let title: String; let items: [Item] }
        struct Item: Decodable { let id: String; let title: String; let category: String; let url: URL?; let isNew: Bool; let isDisabled: Bool }
        evaluate(script) { data in
            guard let data, let payload = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.courseSections = payload.map { section in
                CourseContentSection(id: section.id, title: section.title, items: section.items.map {
                    CourseContentItem(id: $0.id, title: $0.title, category: $0.category, url: $0.url, isNew: $0.isNew, isDisabled: $0.isDisabled)
                })
            }
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
          const cells = [...row.querySelectorAll('td')].map(td => td.textContent.replace(/\s+/g, ' ').trim()).filter(Boolean);
          return { id: row.getAttribute('data-course-value') || `${index}`, name, detail: cells.filter(v => v !== name).slice(0, 4).join(' ・ '), url: link ? new URL(link.href, location.href).href : null };
        }).filter(item => item.name)))()
        """#
        struct Payload: Decodable { let id: String; let name: String; let detail: String; let url: URL? }
        evaluate(script) { data in
            guard let data, let values = try? JSONDecoder().decode([Payload].self, from: data) else { return }
            self.subjects = values.map { SyllabusSubject(id: $0.id, name: $0.name, detail: $0.detail, url: $0.url) }
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

    init(course: WebClassCourse) {
        self.course = course
        _loader = StateObject(wrappedValue: DOMPageLoader(url: course.url, kind: .course))
    }

    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("教材を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if loader.courseSections.isEmpty { ContentUnavailableView("教材がありません", systemImage: "tray") }
            else {
                List {
                    ForEach(loader.courseSections) { section in
                        Section(section.title) {
                            ForEach(section.items) { item in
                                if let url = item.url, !item.isDisabled {
                                    NavigationLink { WebClassView(startURL: url) } label: { ContentRow(item: item) }
                                } else { ContentRow(item: item).opacity(0.55) }
                            }
                        }
                    }
                }
                .refreshable { loader.reload() }
            }
        }
        .navigationTitle(course.title).navigationBarTitleDisplayMode(.inline)
        .appScreenBackground()
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
                HStack { Text(item.title); if item.isNew { Text("NEW").font(.caption2.bold()).foregroundStyle(.red) } }
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

    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("シラバスを取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        HStack(spacing: 14) {
                            AppIconTile(symbol: "doc.text.fill", color: .orange, size: 54)
                            VStack(alignment: .leading, spacing: 3) {
                                Text("高専Webシラバス").font(.title2.bold())
                                Text("学科を選んで授業計画を確認").font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer()
                        }.padding(15).appCard()
                        AppSectionHeader(title: "学科", subtitle: "\(filtered.count)件", symbol: "building.columns.fill")
                        ForEach(filtered) { department in
                            NavigationLink { SyllabusSubjectsView(department: department) } label: {
                                HStack(spacing: 13) {
                                    AppIconTile(symbol: "building.columns.fill", color: .orange)
                                    Text(department.name).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
                                }.padding(13).appCard()
                            }.buttonStyle(.plain)
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

    init(department: SyllabusDepartment) {
        self.department = department
        _loader = StateObject(wrappedValue: DOMPageLoader(url: department.url, kind: .subjects))
    }

    private var filtered: [SyllabusSubject] { query.isEmpty ? loader.subjects : loader.subjects.filter { $0.name.localizedCaseInsensitiveContains(query) } }

    var body: some View {
        Group {
            if loader.isLoading { PlaceholderList() }
            else if let error = loader.errorMessage { ContentUnavailableView("科目を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else {
                ScrollView {
                    LazyVStack(spacing: 11) {
                        AppSectionHeader(title: "科目", subtitle: "\(filtered.count)件", symbol: "books.vertical.fill")
                        ForEach(filtered) { subject in
                            if let url = subject.url {
                                NavigationLink { SyllabusDetailView(subject: subject, url: url) } label: { SubjectRow(subject: subject).padding(13).appCard() }.buttonStyle(.plain)
                            } else { SubjectRow(subject: subject).padding(13).appCard() }
                        }
                    }.padding(16)
                }.searchable(text: $query, prompt: "科目を検索").appScreenBackground()
            }
        }.navigationTitle(department.name).navigationBarTitleDisplayMode(.inline)
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
                Text(subject.name).font(.headline)
                if !subject.detail.isEmpty { Text(subject.detail).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
            }
        }.padding(.vertical, 4)
    }
}
