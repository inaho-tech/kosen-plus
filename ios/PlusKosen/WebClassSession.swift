import Foundation
import WebKit

struct WebClassCourse: Identifiable, Codable, Hashable {
    let title: String
    let url: URL
    let code: String
    let isDueSoon: Bool
    let unreadCount: Int
    let day: String?
    let period: Int?

    var id: String { code.isEmpty ? url.absoluteString : code }
}

private struct WebClassHomePayload: Codable {
    let userName: String
    let courses: [WebClassCourse]
    let years: [WebClassOption]
    let semesters: [WebClassOption]
    let currentYear: String
    let currentSemester: String
}

struct WebClassOption: Identifiable, Codable, Hashable {
    let label: String
    let value: String
    var id: String { value }
}

@MainActor
final class WebClassSession: NSObject, ObservableObject, WKNavigationDelegate {
    enum LoginState: Equatable { case checking, signedOut, signedIn }

    static let homeURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/index.php")!
    static let loginURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")!
    static let logoutURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/logout.php")!
    static let messagesURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/messages.php/inbox")!
    static let accountURL = URL(string: "https://webclass.kosen-k.go.jp/webclass/user.php/config?acs_=a0008c6c")!

    @Published private(set) var loginState: LoginState = .checking
    @Published private(set) var courses: [WebClassCourse] = []
    @Published private(set) var userName = ""
    @Published private(set) var email = ""
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var years: [WebClassOption] = []
    @Published private(set) var semesters: [WebClassOption] = []
    @Published private(set) var currentYear = ""
    @Published private(set) var currentSemester = ""

    private var requestedLogout = false
    private var isStartingSession = false
    private var attemptedSilentLogin = false
    private var authenticationStep = 0
    private var startupAttempt = UUID()
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
            email = "student@example.ac.jp"
            courses = [
                WebClassCourse(title: "情報工学", url: Self.homeURL, code: "2026-INFO", isDueSoon: true, unreadCount: 2, day: "月", period: 1),
                WebClassCourse(title: "電気電子回路", url: Self.homeURL, code: "2026-EE", isDueSoon: false, unreadCount: 0, day: "火", period: 2),
                WebClassCourse(title: "英語コミュニケーション", url: Self.homeURL, code: "2026-ENG", isDueSoon: false, unreadCount: 1, day: "水", period: 3),
                WebClassCourse(title: "応用数学", url: Self.homeURL, code: "2026-MATH", isDueSoon: false, unreadCount: 0, day: nil, period: nil)
            ]
        } else {
            Task {
                let restoredCookies = await WebClassCookieVault.restore()
                beginStartup(restoredCookies: restoredCookies)
            }
        }
    }

    private func beginStartup(restoredCookies: Bool) {
        guard restoredCookies else {
            startupAttempt = UUID()
            isStartingSession = false
            isLoading = false
            attemptedSilentLogin = false
            authenticationStep = 0
            loginState = .signedOut
            return
        }
        isStartingSession = true
        attemptedSilentLogin = false
        authenticationStep = 0
        let attempt = UUID()
        startupAttempt = attempt
        refresh()
        Task {
            try? await Task.sleep(for: .seconds(30))
            guard startupAttempt == attempt, loginState == .checking else { return }
            isStartingSession = false
            isLoading = false
            loginState = .signedOut
        }
    }

    func refresh() {
        requestedLogout = false
        isLoading = true
        errorMessage = nil
        worker.load(URLRequest(url: Self.homeURL, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    func selectSchedule(year: String? = nil, semester: String? = nil) {
        let selectedYear = year ?? currentYear
        let selectedSemester = semester ?? currentSemester
        guard var components = URLComponents(url: Self.homeURL, resolvingAgainstBaseURL: false) else { return }
        components.queryItems = [URLQueryItem(name: "year", value: selectedYear), URLQueryItem(name: "semester", value: selectedSemester)]
        guard let url = components.url else { return }
        isLoading = true
        worker.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    func loginCompleted(at url: URL) {
        guard Self.isAuthenticatedHome(url) else { return }
        isStartingSession = false
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

        if requestedLogout {
            let wasLogout = requestedLogout
            loginState = .signedOut
            courses = []
            userName = ""
            email = ""
            isLoading = false
            requestedLogout = false
            if wasLogout { Task { await WebClassCookieVault.clear() } }
            return
        }

        if url.path.contains("logout.php"), authenticationStep == 1 {
            authenticationStep = 2
            worker.load(URLRequest(url: Self.loginURL, cachePolicy: .reloadIgnoringLocalCacheData))
            return
        }

        if url.path.contains("login.php") && !attemptedSilentLogin {
            isStartingSession = true
            attemptedSilentLogin = true
            authenticationStep = 1
            loginState = .checking
            startAuthenticationGracePeriod()
            worker.load(URLRequest(url: Self.logoutURL, cachePolicy: .reloadIgnoringLocalCacheData))
            return
        }

        if url.path.contains("login.php"), authenticationStep == 2 {
            authenticationStep = 3
            worker.load(URLRequest(url: Self.homeURL, cachePolicy: .reloadIgnoringLocalCacheData))
            return
        }

        if isStartingSession && (url.path.contains("login.php") || url.host?.contains("microsoftonline.com") == true) {
            return
        }

        if url.path.contains("login.php") || url.host?.contains("microsoftonline.com") == true {
            loginState = .signedOut
            courses = []
            userName = ""
            email = ""
            isLoading = false
            return
        }

        guard Self.isAuthenticatedHome(url) else {
            if url.host == "webclass.kosen-k.go.jp" {
                loginState = .signedOut
                isLoading = false
            }
            return
        }

        isStartingSession = false
        attemptedSilentLogin = false
        authenticationStep = 0
        startupAttempt = UUID()

        let script = #"""
        (() => {
          const absolute = value => { try { return new URL(value, location.href).href; } catch (_) { return value || ''; } };
          const clean = value => (value || '').replace(/\s+/g, ' ').replace(/^[»›]\s*/, '').trim();
          const dueSoonText = '締切が近い課題があります。';
          let userName = '';
          for (const item of document.querySelectorAll('#menu li.dropdown')) {
            const toggle = item.querySelector(':scope > a.dropdown-toggle');
            if (item.querySelector('a[href*="user.php/config"]') && toggle) {
              userName = clean(toggle.childNodes[0]?.textContent || toggle.textContent); break;
            }
          }
          if (!userName) {
            const selectors = ['#menu .visible-xs-block li.dropdown > a.dropdown-toggle', '.user_name', '.header_user_name', '[class*="user-name"]'];
            for (const selector of selectors) {
              const values = [...document.querySelectorAll(selector)].map(e => clean(e.childNodes[0]?.textContent || e.textContent));
              userName = values.find(v => v && !/language|言語/i.test(v)) || userName;
              if (userName) break;
            }
          }
          const seen = new Set();
          const courses = [];
          const optionData = selector => {
            const select = document.querySelector(selector);
            return { current: select?.value || '', values: [...(select?.options || [])].map(option => ({ label: clean(option.textContent), value: option.value })) };
          };
          const yearData = optionData('select[name="set_year"],select[name="year"]');
          const semesterData = optionData('select[name="set_semester"],select[name="semester"]');
          const schedule = new Map();
          const table = document.querySelector('#schedule-table');
          if (table) {
            const header = table.querySelector('thead tr') || table.querySelector('tr');
            const days = [...(header?.children || [])].map(cell => clean(cell.textContent).replace(/曜日/g, '').slice(0, 1));
            [...table.querySelectorAll('tbody tr')].forEach(row => {
              const cells = [...row.children];
              const rawPeriod = parseInt(clean(cells[0]?.textContent).replace(/\D/g, ''), 10);
              const period = rawPeriod ? Math.ceil(rawPeriod / 2) : null;
              cells.forEach((cell, index) => cell.querySelectorAll('a[href*="/course.php/"]').forEach(link => {
                const href = absolute(link.getAttribute('href'));
                if (href) schedule.set(href, { day: days[index] || null, period });
              }));
            });
          }
          document.querySelectorAll('#schedule-table a[href*="/course.php/"], .schedule-list a.course[href*="/course.php/"], a[href*="/course.php/"]').forEach(a => {
            const url = absolute(a.getAttribute('href'));
            if (!url || seen.has(url)) return;
            const rawTitle = clean(a.textContent);
            const isDueSoon = rawTitle.includes(dueSoonText) || /期限間近|締切間近/.test(rawTitle);
            let title = rawTitle
              .replaceAll(dueSoonText, '')
              .replace(/期限間近|締切間近/g, '')
              .replace(/新着メッセージ\s*\(?\d+\)?/g, '')
              .trim();
            if (!title || /開講期間外/.test(rawTitle)) return;
            seen.add(url);
            const match = url.match(/course\.php\/([^/?#]+)/);
            const row = a.closest('tr, li, .course, .list-group-item') || a.parentElement;
            const badges = row ? [...row.querySelectorAll('.badge,.label-danger')].map(e => parseInt(e.textContent, 10) || 0) : [];
            const slot = schedule.get(url) || {};
            courses.push({ title, url, code: match ? match[1] : '', isDueSoon, unreadCount: badges.reduce((sum, value) => sum + value, 0), day: slot.day || null, period: slot.period || null });
          });
          return JSON.stringify({ userName, courses, years: yearData.values, semesters: semesterData.values, currentYear: yearData.current, currentSemester: semesterData.current });
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
                self.years = payload.years
                self.semesters = payload.semesters
                self.currentYear = payload.currentYear
                self.currentSemester = payload.currentSemester
                self.errorMessage = nil
                self.refreshProfileEmail()
                Task { await WebClassCookieVault.save() }
            }
        }
    }

    private func refreshProfileEmail() {
        Task {
            let cookies = await withCheckedContinuation { continuation in
                WKWebsiteDataStore.default().httpCookieStore.getAllCookies { continuation.resume(returning: $0) }
            }
            var request = URLRequest(url: Self.accountURL, cachePolicy: .reloadIgnoringLocalCacheData)
            let cookieHeader = HTTPCookie.requestHeaderFields(with: cookies)["Cookie"]
            if let cookieHeader { request.setValue(cookieHeader, forHTTPHeaderField: "Cookie") }
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  (response as? HTTPURLResponse)?.statusCode == 200,
                  let html = String(data: data, encoding: .utf8) else { return }
            let patterns = [
                #"name=[\"']email[\"'][^>]*value=[\"']([^\"']+)[\"']"#,
                #"value=[\"']([^\"']+)[\"'][^>]*name=[\"']email[\"']"#
            ]
            for pattern in patterns {
                guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
                      let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
                      let range = Range(match.range(at: 1), in: html) else { continue }
                email = String(html[range]).replacingOccurrences(of: "&amp;", with: "&")
                break
            }
        }
    }

    private func startAuthenticationGracePeriod() {
        let attempt = UUID()
        startupAttempt = attempt
        Task {
            try? await Task.sleep(for: .seconds(30))
            guard startupAttempt == attempt, isStartingSession else { return }
            isStartingSession = false
            isLoading = false
            loginState = .signedOut
        }
    }
}
