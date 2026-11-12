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

@MainActor
final class DOMPageLoader: NSObject, ObservableObject, WKNavigationDelegate {
    enum Kind { case course, departments, subjects }

    @Published var isLoading = true
    @Published var errorMessage: String?
    @Published var courseSections: [CourseContentSection] = []
    @Published var departments: [SyllabusDepartment] = []
    @Published var subjects: [SyllabusSubject] = []

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
        (() => JSON.stringify([...document.querySelectorAll('.cl-contentsList_folder')].map((folder, sectionIndex) => ({
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
        }))))
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
    }
}

private struct ContentRow: View {
    let item: CourseContentItem
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text.fill").foregroundStyle(.indigo).frame(width: 28)
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
            .redacted(reason: .placeholder).allowsHitTesting(false)
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
                List(filtered) { department in
                    NavigationLink { SyllabusSubjectsView(department: department) } label: {
                        Label(department.name, systemImage: "building.columns.fill").padding(.vertical, 5)
                    }
                }.searchable(text: $query, prompt: "学科を検索").refreshable { loader.reload() }
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
                List(filtered) { subject in
                    if let url = subject.url {
                        NavigationLink { WebClassView(startURL: url) } label: { SubjectRow(subject: subject) }
                    } else { SubjectRow(subject: subject) }
                }.searchable(text: $query, prompt: "科目を検索")
            }
        }.navigationTitle(department.name).navigationBarTitleDisplayMode(.inline)
    }
}

private struct SubjectRow: View {
    let subject: SyllabusSubject
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(subject.name).font(.headline)
            if !subject.detail.isEmpty { Text(subject.detail).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
        }.padding(.vertical, 4)
    }
}
