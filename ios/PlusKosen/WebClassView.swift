import SwiftUI
import WebKit
import QuickLook

struct WebClassView: View {
    let startURL: URL
    var onNavigation: ((URL) -> Void)? = nil
    @EnvironmentObject private var settings: AppSettings
    @AppStorage("customWebClassUI") private var customWebClassUI = false
    @State private var progress = 0.0
    @State private var title = ""

    var body: some View {
        WebContainer(url: startURL, darkMode: settings.webViewDarkMode, preferMobileCourseUI: customWebClassUI, progress: $progress, pageTitle: $title, onNavigation: onNavigation)
            .background(settings.webViewDarkMode ? Color.black : Color(.systemBackground))
            .navigationTitle(title.isEmpty ? "WebClass" : title)
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct WebContainer: UIViewRepresentable {
    let url: URL
    let darkMode: Bool
    let preferMobileCourseUI: Bool
    @Binding var progress: Double
    @Binding var pageTitle: String
    var onNavigation: ((URL) -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        if darkMode {
            let source = "document.documentElement.style.background='#000';document.documentElement.style.colorScheme='dark';"
            config.userContentController.addUserScript(WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        }
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = false
        webView.backgroundColor = darkMode ? .black : .systemBackground
        context.coordinator.observe(webView)
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: WebContainer
        private var progressObservation: NSKeyValueObservation?
        private var titleObservation: NSKeyValueObservation?
        init(_ parent: WebContainer) { self.parent = parent }

        func observe(_ webView: WKWebView) {
            progressObservation = webView.observe(\.estimatedProgress, options: [.new]) { [weak self] view, _ in
                DispatchQueue.main.async { self?.parent.progress = view.estimatedProgress }
            }
            titleObservation = webView.observe(\.title, options: [.new]) { [weak self] view, _ in
                DispatchQueue.main.async { self?.parent.pageTitle = view.title ?? "" }
            }
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
            if let mime = navigationResponse.response.mimeType,
               mime == "application/pdf" || mime.hasPrefix("image/") || mime.hasPrefix("video/") {
                decisionHandler(.allow)
                return
            }
            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard parent.preferMobileCourseUI, let source = navigationAction.request.url,
                  source.host == "webclass.kosen-k.go.jp", var parts = URLComponents(url: source, resolvingAgainstBaseURL: false) else {
                decisionHandler(.allow); return
            }
            if parts.path.hasSuffix("/qstn_frame.php") {
                parts.path = "/webclass/mbl.php/questions/"
                var query = parts.queryItems ?? []
                if !query.contains(where: { $0.name == "mbl" }) { query.append(URLQueryItem(name: "mbl", value: "1")) }
                parts.queryItems = query
            } else if parts.path.hasSuffix("/show_frame.php") {
                parts.path = "/webclass/show_info.php"
                var query = parts.queryItems ?? []
                if !query.contains(where: { $0.name == "mbl" }) { query.append(URLQueryItem(name: "mbl", value: "1")) }
                parts.queryItems = query
            } else {
                decisionHandler(.allow); return
            }
            guard let mobile = parts.url, mobile != source else { decisionHandler(.allow); return }
            webView.load(URLRequest(url: mobile))
            decisionHandler(.cancel)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.scrollView.setContentOffset(.zero, animated: false)
            if let url = webView.url { parent.onNavigation?(url) }
            guard parent.darkMode else { return }
            if let path = Bundle.main.path(forResource: "darkreader-4.9.134.min", ofType: "js"),
               let script = try? String(contentsOfFile: path, encoding: .utf8) {
                let config = "DarkReader.setFetchMethod(window.fetch);DarkReader.enable({brightness:100,contrast:90,sepia:0});"
                webView.evaluateJavaScript(script + ";" + config)
            } else {
                let css = "html{color-scheme:dark!important;background:#000!important}body{background:#000!important;color:#e8e8e8!important}"
                webView.evaluateJavaScript("var s=document.createElement('style');s.textContent=\(String(reflecting: css));document.head.appendChild(s)")
            }
        }
    }
}

