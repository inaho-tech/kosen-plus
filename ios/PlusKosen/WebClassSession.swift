import Foundation
import WebKit

struct WebClassCourse: Identifiable, Codable, Hashable {
    let title: String
    let url: URL
    let code: String
    let isDueSoon: Bool
    let unreadCount: Int

    var id: String { code.isEmpty ? url.absoluteString : code }
}

private struct WebClassHomePayload: Codable {
    let userName: String
    let courses: [WebClassCourse]
}

@MainActor
final class WebClassSession: NSObject, ObservableObject, WKNavigationDelegate {
    enum LoginState: Equatable { case checking, signedOut, signedIn }

    static let homeURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/index.php")!
    static let loginURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")!
    static let logoutURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/logout.php")!
    static let messagesURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/messages.php/inbox")!

    @Published private(set) var loginState: LoginState = .checking
    @Published private(set) var courses: [WebClassCourse] = []
    @Published private(set) var userName = ""
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    private var requestedLogout = false
    private lazy var worker: WKWebView = {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = self
        return view
    }()

    override init() {
        super.init()
        if CommandLine.arguments.contains("-ui-preview") {
            loginState = .signedIn
            userName = "高専 太郎"
            courses = [
                WebClassCourse(title: "情報工学", url: Self.homeURL, code: "2026-INFO", isDueSoon: true, unreadCount: 2),
                WebClassCourse(title: "電気電子回路", url: Self.homeURL, code: "2026-EE", isDueSoon: false, unreadCount: 0),
                WebClassCourse(title: "英語コミュニケーション", url: Self.homeURL, code: "2026-ENG", isDueSoon: false, unreadCount: 1),
                WebClassCourse(title: "応用数学", url: Self.homeURL, code: "2026-MATH", isDueSoon: false, unreadCount: 0)
            ]
        } else {
            refresh()
        }
    }

    func refresh() {
        requestedLogout = false
        isLoading = true
        errorMessage = nil
        worker.load(URLRequest(url: Self.homeURL, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    func loginCompleted(at url: URL) {
        guard Self.isAuthenticatedHome(url) else { return }
        refresh()
    }

    func logout() {
        requestedLogout = true
        isLoading = true
        worker.load(URLRequest(url: Self.logoutURL))
    }

    static func isAuthenticatedHome(_ url: URL) -> Bool {
        guard url.host == "webclass.kosen-k.go.jp" else { return false }
        return url.path.hasSuffix("/index.php") || url.absoluteString.contains("/mbl.php/index")
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        errorMessage = error.localizedDescription
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        errorMessage = error.localizedDescription
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard let url = webView.url else { return }

        if requestedLogout || url.path.contains("login.php") || url.host?.contains("microsoftonline.com") == true {
            loginState = .signedOut
            courses = []
            userName = ""
            isLoading = false
            requestedLogout = false
            return
        }

        guard Self.isAuthenticatedHome(url) else {
            if url.host == "webclass.kosen-k.go.jp" {
                loginState = .signedOut
                isLoading = false
            }
            return
        }

        let script = #"""
        (() => {
          const absolute = value => { try { return new URL(value, location.href).href; } catch (_) { return value || ''; } };
          const clean = value => (value || '').replace(/\s+/g, ' ').replace(/^[»›]\s*/, '').trim();
          const selectors = ['#menu li.dropdown > a.dropdown-toggle', '.user_name', '.header_user_name', '[class*="user-name"]'];
          let userName = '';
          for (const selector of selectors) {
            const values = [...document.querySelectorAll(selector)].map(e => clean(e.childNodes[0]?.textContent || e.textContent));
            userName = values.find(v => v && !/language|言語/i.test(v)) || userName;
            if (userName) break;
          }
          const seen = new Set();
          const courses = [];
          document.querySelectorAll('#schedule-table a[href*="/course.php/"], .schedule-list a.course[href*="/course.php/"], a[href*="/course.php/"]').forEach(a => {
            const url = absolute(a.getAttribute('href'));
            if (!url || seen.has(url)) return;
            let title = clean(a.textContent).replace(/新着メッセージ\s*\(?\d+\)?/g, '').trim();
            if (!title || /開講時間外/.test(title)) return;
            seen.add(url);
            const match = url.match(/course\.php\/([^/?#]+)/);
            const row = a.closest('tr, li, .course, .list-group-item') || a.parentElement;
            const badges = row ? [...row.querySelectorAll('.badge,.label-danger')].map(e => parseInt(e.textContent, 10) || 0) : [];
            courses.push({ title, url, code: match ? match[1] : '', isDueSoon: /期限間近|締切間近/.test(a.textContent), unreadCount: badges.reduce((sum, value) => sum + value, 0) });
          });
          return JSON.stringify({ userName, courses });
        })()
        """#

        webView.evaluateJavaScript(script) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                self.isLoading = false
                if let error { self.errorMessage = error.localizedDescription; return }
                guard let json = result as? String,
                      let data = json.data(using: .utf8),
                      let payload = try? JSONDecoder().decode(WebClassHomePayload.self, from: data) else {
                    self.errorMessage = "WebClassの情報を読み取れませんでした。"
                    return
                }
                self.loginState = .signedIn
                self.userName = payload.userName
                self.courses = payload.courses
                self.errorMessage = nil
            }
        }
    }
}
