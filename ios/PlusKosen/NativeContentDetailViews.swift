import SwiftUI
import WebKit

struct NativeContentInfoItem: Identifiable, Hashable {
    let id: String
    let label: String
    let value: String
}

struct NativeContentTab: Identifiable, Hashable {
    let id: String
    let label: String
    let url: URL?
    let isActive: Bool
}

struct NativeContentDetail: Hashable {
    let title: String
    let type: String
    let description: String
    let status: String
    let isAvailable: Bool
    let infoItems: [NativeContentInfoItem]
    let executionURL: URL?
    let tabs: [NativeContentTab]
}

struct NativeContentHistory: Hashable {
    let title: String
    let type: String
    let usageSummary: String
    let columns: [String]
    let rows: [[String]]
    let tabs: [NativeContentTab]
}

@MainActor
final class NativeContentPageLoader: NSObject, ObservableObject, WKNavigationDelegate {
    enum Kind { case detail, history }
    @Published var isLoading = true
    @Published var errorMessage: String?
    @Published var detail: NativeContentDetail?
    @Published var history: NativeContentHistory?

    private let url: URL
    private let kind: Kind
    private var authenticationStep = 0
    private var authenticationAttempt = UUID()
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
        authenticationStep = 0
        webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { fail(error) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { fail(error) }
    private func fail(_ error: Error) { isLoading = false; errorMessage = error.localizedDescription }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard let current = webView.url else { return }
        if handleAuthenticationRedirect(current, in: webView) {
            return
        }
        authenticationAttempt = UUID()
        authenticationStep = 0
        switch kind {
        case .detail: extractDetail(webView)
        case .history: extractHistory(webView)
        }
    }

    private func handleAuthenticationRedirect(_ current: URL, in webView: WKWebView) -> Bool {
        if current.path.contains("logout.php") {
            authenticationStep = max(authenticationStep, 1)
            webView.load(URLRequest(url: WebClassSession.loginURL, cachePolicy: .reloadIgnoringLocalCacheData))
            return true
        }
        guard current.path.contains("login.php") || current.host?.contains("microsoftonline.com") == true else { return false }
        if authenticationStep == 0 {
            authenticationStep = 1
            beginAuthenticationTimeout()
            webView.load(URLRequest(url: WebClassSession.logoutURL, cachePolicy: .reloadIgnoringLocalCacheData))
        } else if current.path.contains("login.php"), authenticationStep == 1 {
            authenticationStep = 2
            webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
        }
        return true
    }

    private func beginAuthenticationTimeout() {
        let attempt = UUID()
        authenticationAttempt = attempt
        Task {
            try? await Task.sleep(for: .seconds(20))
            guard authenticationAttempt == attempt, authenticationStep > 0 else { return }
            isLoading = false
            errorMessage = "WebClassへの再ログインが必要です。ホームでログインしてから再読み込みしてください。"
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

    private func extractDetail(_ webView: WKWebView) {
        let script = #"""
        (() => {
          const clean = value => (value || '').replace(/\s+/g, ' ').trim();
          const absolute = value => { try { return new URL(value, location.href).href; } catch (_) { return null; } };
          const titleEl = document.querySelector('h3.page-header,h2.wcl_pageMainTitle,.page-header');
          const data = {title: clean(titleEl?.textContent || document.title), type:'', description:'', status:'', isAvailable:true, infoItems:[], executionURL:null, tabs:[]};
          let p = titleEl?.nextElementSibling;
          while (p && p.tagName !== 'P') p = p.nextElementSibling;
          if (p) { const raw = clean(p.textContent); data.type = clean(raw.split(/[:：]/).slice(1).join('：') || raw); }
          document.querySelectorAll('ul.nav.nav-tabs li').forEach((li, index) => { const a=li.querySelector('a'); if(a)data.tabs.push({id:`tab-${index}`,label:clean(a.textContent),url:absolute(a.getAttribute('href')),isActive:li.classList.contains('active')}); });
          document.querySelectorAll('h4.contentsInfo-sectionTitle').forEach(h => {
            const label=clean(h.textContent); let next=h.nextElementSibling;
            if(label.includes('説明')) { while(next&&next.tagName!=='P')next=next.nextElementSibling; data.description=clean(next?.textContent); }
            if(label.includes('教材実行')) { while(next&&next.tagName!=='DIV')next=next.nextElementSibling; data.status=clean(next?.textContent); data.isAvailable=!data.status.includes('利用できません'); }
          });
          document.querySelectorAll('.contentsInfoList .contentsInfoListItem').forEach((item,index) => {
            const label=clean(item.querySelector('.contentsInfoListLabel')?.textContent); const value=clean(item.querySelector('.contentsInfoListData')?.textContent);
            if(label||value)data.infoItems.push({id:`info-${index}`,label,value});
          });
          const execText=document.querySelector('#execUrlTarget')?.textContent?.trim();
          const execLink=document.querySelector('a[href*="contents"],a[href*="show_frame"],a[href*="qstn_frame"]');
          data.executionURL=absolute(execText || execLink?.getAttribute('href'));
          return JSON.stringify(data);
        })()
        """#
        struct Payload: Decodable {
            let title: String; let type: String; let description: String; let status: String; let isAvailable: Bool
            let infoItems: [Info]; let executionURL: URL?; let tabs: [Tab]
            struct Info: Decodable { let id: String; let label: String; let value: String }
            struct Tab: Decodable { let id: String; let label: String; let url: URL?; let isActive: Bool }
        }
        evaluate(script) { data in
            guard let data, let value = try? JSONDecoder().decode(Payload.self, from: data) else { self.isLoading = false; self.errorMessage = "教材詳細を解析できませんでした。"; return }
            self.detail = NativeContentDetail(title: value.title, type: value.type, description: value.description, status: value.status, isAvailable: value.isAvailable, infoItems: value.infoItems.map { .init(id: $0.id, label: $0.label, value: $0.value) }, executionURL: value.executionURL, tabs: value.tabs.map { .init(id: $0.id, label: $0.label, url: $0.url, isActive: $0.isActive) })
            self.isLoading = false
        }
    }

    private func extractHistory(_ webView: WKWebView) {
        let script = #"""
        (() => {
          const clean = value => (value || '').replace(/\s+/g, ' ').trim();
          const absolute = value => { try { return new URL(value, location.href).href; } catch (_) { return null; } };
          const titleEl=document.querySelector('h3.page-header,.page-header');
          const data={title:clean(titleEl?.textContent||document.title),type:'',usageSummary:'',columns:[],rows:[],tabs:[]};
          let p=titleEl?.nextElementSibling; while(p&&p.tagName!=='P')p=p.nextElementSibling;
          if(p){const raw=clean(p.textContent);data.type=clean(raw.split(/[:：]/).slice(1).join('：')||raw);}
          document.querySelectorAll('ul.nav.nav-tabs li').forEach((li,index)=>{const a=li.querySelector('a');if(a)data.tabs.push({id:`tab-${index}`,label:clean(a.textContent),url:absolute(a.getAttribute('href')),isActive:li.classList.contains('active')});});
          data.usageSummary=[...document.querySelectorAll('p')].map(p=>clean(p.textContent)).find(t=>t.includes('利用回数'))||'';
          const table=document.querySelector('.table-responsive table,table.table');
          if(table){data.columns=[...table.querySelectorAll('thead tr th')].map(th=>clean(th.textContent));data.rows=[...table.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td,th')].map(td=>clean(td.textContent))).filter(row=>row.length);}
          return JSON.stringify(data);
        })()
        """#
        struct Payload: Decodable {
            let title: String; let type: String; let usageSummary: String; let columns: [String]; let rows: [[String]]; let tabs: [Tab]
            struct Tab: Decodable { let id: String; let label: String; let url: URL?; let isActive: Bool }
        }
        evaluate(script) { data in
            guard let data, let value = try? JSONDecoder().decode(Payload.self, from: data) else { self.isLoading = false; self.errorMessage = "履歴を解析できませんでした。"; return }
            self.history = NativeContentHistory(title: value.title, type: value.type, usageSummary: value.usageSummary, columns: value.columns, rows: value.rows, tabs: value.tabs.map { .init(id: $0.id, label: $0.label, url: $0.url, isActive: $0.isActive) })
            self.isLoading = false
        }
    }
}

struct NativeContentDetailView: View {
    @StateObject private var loader: NativeContentPageLoader
    init(url: URL) { _loader = StateObject(wrappedValue: NativeContentPageLoader(url: url, kind: .detail)) }
    var body: some View {
        Group {
            if loader.isLoading { NativePagePlaceholder() }
            else if let error = loader.errorMessage { ContentUnavailableView("教材詳細を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if let detail = loader.detail {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        ContentPageHeader(title: detail.title, type: detail.type, symbol: "doc.text.fill", color: .indigo)
                        ContentTabs(tabs: detail.tabs)
                        HStack(spacing: 7) {
                            Label(detail.isAvailable ? "利用可能" : "期間外・利用不可", systemImage: detail.isAvailable ? "checkmark.circle.fill" : "xmark.circle.fill")
                                .foregroundStyle(detail.isAvailable ? Color.green : Color.red)
                            if !detail.status.isEmpty { Text(detail.status).foregroundStyle(.secondary).lineLimit(2) }
                        }.font(.caption.weight(.semibold)).padding(12).frame(maxWidth: .infinity, alignment: .leading).appCard(cornerRadius: 14)
                        if !detail.description.isEmpty { NativeInfoSection(title: "説明", symbol: "text.alignleft") { Text(detail.description).font(.body).textSelection(.enabled) } }
                        if !detail.infoItems.isEmpty {
                            NativeInfoSection(title: "詳細情報", symbol: "info.circle.fill") {
                                ForEach(Array(detail.infoItems.enumerated()), id: \.element.id) { index, item in
                                    HStack(alignment: .top) { Text(item.label).font(.caption).foregroundStyle(.secondary).frame(maxWidth: 115, alignment: .leading); Text(item.value).frame(maxWidth: .infinity, alignment: .trailing).textSelection(.enabled) }
                                    if index < detail.infoItems.count - 1 { Divider() }
                                }
                            }
                        }
                        if let url = detail.executionURL, detail.isAvailable {
                            NavigationLink { WebClassView(startURL: url) } label: { Label("教材を開く", systemImage: "play.fill").font(.headline).frame(maxWidth: .infinity).frame(height: 52) }
                                .buttonStyle(.borderedProminent)
                        }
                    }.padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
            }
        }.navigationTitle("教材詳細").navigationBarTitleDisplayMode(.inline)
    }
}

struct NativeContentHistoryView: View {
    @StateObject private var loader: NativeContentPageLoader
    init(url: URL) { _loader = StateObject(wrappedValue: NativeContentPageLoader(url: url, kind: .history)) }
    var body: some View {
        Group {
            if loader.isLoading { NativePagePlaceholder() }
            else if let error = loader.errorMessage { ContentUnavailableView("履歴を取得できません", systemImage: "exclamationmark.triangle", description: Text(error)) }
            else if let history = loader.history {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        ContentPageHeader(title: history.title, type: history.type, symbol: "clock.arrow.circlepath", color: .teal)
                        ContentTabs(tabs: history.tabs)
                        if !history.usageSummary.isEmpty { Label(history.usageSummary, systemImage: "info.circle.fill").padding(13).frame(maxWidth: .infinity, alignment: .leading).appCard(cornerRadius: 14) }
                        AppSectionHeader(title: "アクセス履歴", subtitle: "\(history.rows.count)件", symbol: "clock.fill")
                        if history.rows.isEmpty { ContentUnavailableView("履歴はありません", systemImage: "clock.badge.xmark") }
                        ForEach(Array(history.rows.enumerated()), id: \.offset) { rowIndex, row in
                            VStack(alignment: .leading, spacing: 9) {
                                Text("#\(rowIndex + 1)").font(.caption.bold()).foregroundStyle(.indigo).padding(.horizontal, 8).padding(.vertical, 4).background(.indigo.opacity(0.12), in: Capsule())
                                ForEach(Array(row.enumerated()), id: \.offset) { index, cell in
                                    HStack(alignment: .top) {
                                        Text(index < history.columns.count ? history.columns[index] : "項目 \(index + 1)").font(.caption).foregroundStyle(.secondary).frame(maxWidth: 110, alignment: .leading)
                                        Text(cell).font(.subheadline.weight(.medium)).frame(maxWidth: .infinity, alignment: .leading).textSelection(.enabled)
                                    }
                                }
                            }.padding(14).appCard(cornerRadius: 16)
                        }
                    }.padding(16)
                }.refreshable { loader.reload() }.appScreenBackground()
            }
        }.navigationTitle("利用履歴").navigationBarTitleDisplayMode(.inline)
    }
}

private struct ContentPageHeader: View {
    let title: String; let type: String; let symbol: String; let color: Color
    var body: some View {
        HStack(spacing: 14) {
            AppIconTile(symbol: symbol, color: color, size: 52)
            VStack(alignment: .leading, spacing: 5) { Text(title).font(.title3.bold()).lineLimit(3); if !type.isEmpty { Text(type).font(.caption.weight(.semibold)).foregroundStyle(color) } }
        }.padding(15).frame(maxWidth: .infinity, alignment: .leading).appCard(cornerRadius: 18)
    }
}

private struct ContentTabs: View {
    let tabs: [NativeContentTab]
    var body: some View {
        if tabs.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tabs) { tab in
                        if tab.isActive || tab.url == nil { Text(tab.label).foregroundStyle(.white).padding(.horizontal, 13).frame(height: 34).background(.indigo, in: Capsule()) }
                        else if let url = tab.url {
                            NavigationLink { ContentTabDestination(url: url) } label: { Text(tab.label).foregroundStyle(.primary).padding(.horizontal, 13).frame(height: 34).appCard(cornerRadius: 17) }
                        }
                    }
                }
            }
        }
    }
}

private struct ContentTabDestination: View {
    let url: URL
    var body: some View { Group { if url.path.contains("history") { NativeContentHistoryView(url: url) } else { NativeContentDetailView(url: url) } } }
}

private struct NativeInfoSection<Content: View>: View {
    let title: String; let symbol: String; let content: Content
    init(title: String, symbol: String, @ViewBuilder content: () -> Content) { self.title = title; self.symbol = symbol; self.content = content() }
    var body: some View { VStack(alignment: .leading, spacing: 12) { Label(title, systemImage: symbol).font(.headline).foregroundStyle(.indigo); content }.padding(15).frame(maxWidth: .infinity, alignment: .leading).appCard(cornerRadius: 16) }
}

private struct NativePagePlaceholder: View {
    var body: some View { ScrollView { VStack(spacing: 12) { ForEach(0..<5, id: \.self) { _ in RoundedRectangle(cornerRadius: 16).fill(.quaternary).frame(height: 86) } }.padding(16) }.redacted(reason: .placeholder).allowsHitTesting(false).appScreenBackground() }
}
