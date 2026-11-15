import SwiftUI
import WebKit

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var webClass: WebClassSession
    @State private var confirmingLogout = false
    @State private var confirmingCacheClear = false
    @State private var cacheCleared = false
    var body: some View {
        List {
            Section {
                VStack(spacing: 11) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 52)).foregroundStyle(.indigo)
                        .symbolRenderingMode(.hierarchical)
                    Text(webClass.userName.isEmpty ? "WebClass ユーザー" : webClass.userName).font(.title2.bold()).lineLimit(1)
                    if !webClass.email.isEmpty {
                        Label(webClass.email, systemImage: "envelope.fill").font(.subheadline).foregroundStyle(.secondary).textSelection(.enabled)
                    }
                    Label(webClass.loginState == .signedIn ? "ログイン中" : webClass.loginState == .checking ? "セッションを確認中" : "未ログイン", systemImage: webClass.loginState == .signedIn ? "checkmark.circle.fill" : "person.crop.circle.badge.exclamationmark")
                        .font(.subheadline).foregroundStyle(webClass.loginState == .signedIn ? Color.green : Color.secondary)
                }.padding(.vertical, 12).frame(maxWidth: .infinity)
            }

            Section("アカウント") {
                NavigationLink { WebClassView(startURL: WebClassSession.messagesURL) } label: {
                    IOSSettingsRow(symbol: "envelope.fill", color: .blue, title: "メッセージ", subtitle: "WebClassの受信トレイ")
                }
                Button { confirmingCacheClear = true } label: {
                    IOSSettingsRow(symbol: "trash.fill", color: .teal, title: "キャッシュをクリア", subtitle: "ログイン状態は保持されます")
                }.foregroundStyle(.primary)
                if webClass.loginState == .signedIn {
                    Button(role: .destructive) { confirmingLogout = true } label: {
                        IOSSettingsRow(symbol: "rectangle.portrait.and.arrow.right", color: .red, title: "ログアウト", subtitle: "WebClassのセッションを終了")
                    }
                }
            }

            Section("外観") {
                Menu {
                    Button("システムに従う") { settings.appearance = "system" }
                    Button("ライト") { settings.appearance = "light" }
                    Button("ダーク") { settings.appearance = "dark" }
                } label: {
                    IOSSettingsRow(symbol: "circle.lefthalf.filled", color: .indigo, title: "アプリのテーマ", subtitle: "", trailingValue: themeLabel)
                }.foregroundStyle(.primary)
            }

            Section("表示と操作") {
                Toggle(isOn: $settings.focusMode) {
                    IOSSettingsRow(symbol: "scope", color: .blue, title: "フォーカスモード", subtitle: "システム表示を隠して学習に集中")
                }
                Toggle(isOn: $settings.hideOpeningAnimation) {
                    IOSSettingsRow(symbol: "forward.end.fill", color: .orange, title: "起動アニメーションを省略", subtitle: "次回起動からすぐホームを表示")
                }
                Toggle(isOn: $settings.lightweightMode) {
                    IOSSettingsRow(symbol: "bolt.fill", color: .green, title: "軽量モード", subtitle: "画面遷移アニメーションを抑制")
                }
            }

            Section("一般") {
                NavigationLink { AppInfoView() } label: {
                    IOSSettingsRow(symbol: "info.circle.fill", color: .blue, title: "情報・ライセンス", subtitle: "取得元・バージョン・OSS")
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .appScreenBackground()
        .navigationTitle("マイページ")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("WebClassからログアウトしますか？", isPresented: $confirmingLogout, titleVisibility: .visible) {
            Button("ログアウト", role: .destructive) { webClass.logout() }
            Button("キャンセル", role: .cancel) {}
        } message: {
            Text("WebClassのセッションを終了します。Microsoftアカウントのパスワードはアプリに保存されていません。")
        }
        .confirmationDialog("キャッシュをクリアしますか？", isPresented: $confirmingCacheClear, titleVisibility: .visible) {
            Button("クリア", role: .destructive) { clearWebCache() }
            Button("キャンセル", role: .cancel) {}
        } message: { Text("Cookieとログイン状態は保持されます。") }
        .alert("キャッシュをクリアしました", isPresented: $cacheCleared) { Button("OK", role: .cancel) {} }
    }

    private var themeLabel: String {
        switch settings.appearance {
        case "light": "ライト"
        case "dark": "ダーク"
        default: "システムに従う"
        }
    }

    private func clearWebCache() {
        let types: Set<String> = [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache, WKWebsiteDataTypeOfflineWebApplicationCache]
        WKWebsiteDataStore.default().removeData(ofTypes: types, modifiedSince: .distantPast) { Task { @MainActor in cacheCleared = true } }
    }
}

private struct IOSSettingsRow: View {
    let symbol: String
    let color: Color
    let title: String
    let subtitle: String
    var trailingValue: String? = nil
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 17, weight: .medium)).foregroundStyle(.indigo)
                .frame(width: 28, height: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body)
                if !subtitle.isEmpty { Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            }
            Spacer()
            if let trailingValue { Text(trailingValue).font(.subheadline).foregroundStyle(.secondary) }
        }.padding(.vertical, 3).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
    }
}

struct AppInfoView: View {
    var body: some View {
        List {
            Section("情報取得元") {
                Link("WebClass", destination: URL(string: "https://webclass.kosen-k.go.jp/webclass/")!)
                Link("高専Webシラバス", destination: URL(string: "https://syllabus.kosen-k.go.jp/")!)
                Link("どこトレ（アプリ内名称：電車）", destination: URL(string: "https://doko-train.jp/sp/")!)
            }
        }.navigationTitle("情報・ライセンス")
    }
}

