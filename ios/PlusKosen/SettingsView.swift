import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    var body: some View {
        Form {
            Section("表示") {
                Picker("アプリのテーマ", selection: $settings.appearance) {
                    Text("システムに従う").tag("system")
                    Text("ライト").tag("light")
                    Text("ダーク").tag("dark")
                }
                Toggle("ウェブビューもダークモードにする", isOn: $settings.webViewDarkMode)
            }
            Section("WebClass") {
                NavigationLink("WebClassを開く") { WebClassView(startURL: URL(string: "https://webclass.kosen-k.go.jp/webclass/")!) }
                NavigationLink("メッセージ") { WebClassView(startURL: URL(string: "https://webclass.kosen-k.go.jp/webclass/messages.php/inbox")!) }
            }
            Section("情報") {
                LabeledContent("バージョン", value: "4.5.2 (31)")
                NavigationLink("情報・ライセンス") { AppInfoView() }
            }
            Section("開発者設定") {
                NavigationLink("開発者向け設定") { DeveloperSettingsView() }
            }
        }.navigationTitle("設定")
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
    @AppStorage("forceNewFeatures") private var forceNewFeatures = false
    @AppStorage("customWebClassUI") private var customWebClassUI = false
    var body: some View {
        Form {
            Section { Toggle("常に新しい機能ポップアップを表示", isOn: $forceNewFeatures) }
            Section("危険なエリア") {
                Toggle("教材に独自UIを反映", isOn: $customWebClassUI)
                Text("WebClassの内部仕様変更により教材を正しく表示できなくなる場合があります。").font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("開発者向け設定")
    }
}

