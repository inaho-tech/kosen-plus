import SwiftUI
import WebKit
import QuickLook
import UIKit

struct WebClassView: View {
    let startURL: URL
    var warnsBeforeLeaving = false
    var onNavigation: ((URL) -> Void)? = nil
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.dismiss) private var dismiss
    @AppStorage("customWebClassUI") private var customWebClassUI = false
    @State private var progress = 0.0
    @State private var title = ""
    @State private var showExitConfirmation = false

    var body: some View {
        WebContainer(url: startURL, preferMobileCourseUI: customWebClassUI, progress: $progress, pageTitle: $title, onNavigation: onNavigation)
            .background(Color(.systemBackground))
            .navigationTitle(title.isEmpty ? "WebClass" : title)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(warnsBeforeLeaving)
            .toolbar {
                if warnsBeforeLeaving {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { showExitConfirmation = true } label: {
                            Label("戻る", systemImage: "chevron.backward")
                        }
                    }
                }
            }
            .alert("コンテンツを終了しますか？", isPresented: $showExitConfirmation) {
                Button("終了して戻る", role: .destructive) { dismiss() }
                Button("キャンセル", role: .cancel) {}
            } message: {
                Text("実行中のコンテンツを終了します。学習状況が保存されない可能性があります。")
            }
    }
}

struct WebContainer: UIViewRepresentable {
    let url: URL
    let preferMobileCourseUI: Bool
    @Binding var progress: Double
    @Binding var pageTitle: String
    var onNavigation: ((URL) -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = true
        config.allowsInlineMediaPlayback = true
        config.allowsAirPlayForMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = false
        webView.backgroundColor = .systemBackground
        context.coordinator.observe(webView)
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
    }

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

        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            guard navigationAction.targetFrame == nil, let requestURL = navigationAction.request.url else { return nil }
            webView.load(URLRequest(url: requestURL))
            return nil
        }

        func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
            guard let presenter = Self.presenter(for: webView) else { completionHandler(); return }
            let alert = UIAlertController(title: webView.title ?? "WebClass", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
            presenter.present(alert, animated: true)
        }

        func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
            guard let presenter = Self.presenter(for: webView) else { completionHandler(false); return }
            let alert = UIAlertController(title: webView.title ?? "WebClass", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "キャンセル", style: .cancel) { _ in completionHandler(false) })
            alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
            presenter.present(alert, animated: true)
        }

        func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
            guard let presenter = Self.presenter(for: webView) else { completionHandler(nil); return }
            let alert = UIAlertController(title: webView.title ?? "WebClass", message: prompt, preferredStyle: .alert)
            alert.addTextField { $0.text = defaultText }
            alert.addAction(UIAlertAction(title: "キャンセル", style: .cancel) { _ in completionHandler(nil) })
            alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak alert] _ in completionHandler(alert?.textFields?.first?.text) })
            presenter.present(alert, animated: true)
        }

        private static func presenter(for webView: WKWebView) -> UIViewController? {
            var controller = webView.window?.rootViewController
            while let presented = controller?.presentedViewController { controller = presented }
            return controller
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.scrollView.setContentOffset(.zero, animated: false)
            if let url = webView.url { parent.onNavigation?(url) }
            if webView.url?.host == "webclass.kosen-k.go.jp" { Task { await WebClassCookieVault.save() } }
        }
    }
}

