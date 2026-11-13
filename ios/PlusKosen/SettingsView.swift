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
                        .font(.system(size: 66)).foregroundStyle(.indigo)
                        .symbolRenderingMode(.hierarchical)
                    Text(webClass.userName.isEmpty ? "WebClass ユーザー" : webClass.userName).font(.title2.bold()).lineLimit(1)
                    Label(webClass.loginState == .signedIn ? "ログイン中" : webClass.loginState == .checking ? "セッションを確認中" : "未ログイン", systemImage: webClass.loginState == .signedIn ? "checkmark.circle.fill" : "person.crop.circle.badge.exclamationmark")
                        .font(.subheadline).foregroundStyle(webClass.loginState == .signedIn ? Color.green : Color.secondary)
                    HStack(spacing: 18) {
                        Label("v4.5.2", systemImage: "number")
                        Label(webClass.loginState == .signedIn ? "セッション有効" : "未接続", systemImage: "checkmark.shield")
                    }.font(.caption).foregroundStyle(.secondary)
                }.padding(.vertical, 18).frame(maxWidth: .infinity)
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
                    IOSSettingsRow(symbol: "circle.lefthalf.filled", color: .indigo, title: "アプリのテーマ", subtitle: themeLabel, trailingValue: themeLabel)
                }.foregroundStyle(.primary)
                Toggle(isOn: $settings.webViewDarkMode) {
                    IOSSettingsRow(symbol: "moon.stars.fill", color: .purple, title: "Webページもダークにする", subtitle: "Dark ReaderをWebViewへ適用")
                }
            }

            Section("一般") {
                NavigationLink { AppInfoView() } label: {
                    IOSSettingsRow(symbol: "info.circle.fill", color: .blue, title: "情報・ライセンス", subtitle: "取得元・バージョン・OSS")
                }
                NavigationLink { DeveloperSettingsView() } label: {
                    IOSSettingsRow(symbol: "hammer.fill", color: .orange, title: "開発者向け設定", subtitle: "試験的な機能と詳細設定")
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .appScreenBackground()
        .navigationTitle("マイページ")
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
            Image(systemName: symbol).font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 32, height: 32).background(color.gradient, in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 2) { Text(title).font(.body); Text(subtitle).font(.caption).foregroundStyle(.secondary) }
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
            Section("オープンソースライセンス") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Dark Reader").font(.headline)
                    Text("MIT License — Copyright (c) 2014–present Alexander Shutau").font(.caption).foregroundStyle(.secondary)
                    Link("ライセンス原文", destination: URL(string: "https://github.com/darkreader/darkreader/blob/main/LICENSE")!)
                }.padding(.vertical, 6)
            }
        }.navigationTitle("情報・ライセンス")
    }
}

struct DeveloperSettingsView: View {
    @AppStorage("customWebClassUI") private var customWebClassUI = false
    var body: some View {
        Form {
            Section("危険なエリア") {
                Toggle("教材に独自UIを反映", isOn: $customWebClassUI)
                Text("WebClassの内部仕様変更により教材を正しく表示できなくなる場合があります。").font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("開発者向け設定")
    }
}

