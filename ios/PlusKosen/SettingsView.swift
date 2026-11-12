import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var webClass: WebClassSession
    @State private var confirmingLogout = false
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 18) {
                SettingsCard(title: "外観", symbol: "paintpalette.fill") {
                    SettingsPickerRow(symbol: "circle.lefthalf.filled", color: .indigo, title: "アプリのテーマ") {
                        Picker("アプリのテーマ", selection: $settings.appearance) {
                            Text("システム").tag("system")
                            Text("ライト").tag("light")
                            Text("ダーク").tag("dark")
                        }.labelsHidden()
                    }
                    SettingsDivider()
                    SettingsToggleRow(symbol: "moon.stars.fill", color: .purple, title: "Webページもダークにする", subtitle: "WebClassなどの表示にも適用", isOn: $settings.webViewDarkMode)
                }

                SettingsCard(title: "WebClass", symbol: "graduationcap.fill") {
                    NavigationLink { WebClassView(startURL: WebClassSession.messagesURL) } label: {
                        SettingsLinkRow(symbol: "envelope.fill", color: .blue, title: "メッセージ", subtitle: "受信トレイを開く")
                    }.buttonStyle(.plain)
                    SettingsDivider()
                    if webClass.loginState == .signedIn {
                        Button { confirmingLogout = true } label: {
                            SettingsLinkRow(symbol: "rectangle.portrait.and.arrow.right", color: .red, title: "ログアウト", subtitle: webClass.userName.isEmpty ? "現在のセッションを終了" : webClass.userName, showsChevron: false)
                        }.buttonStyle(.plain)
                    } else {
                        SettingsLinkRow(symbol: "person.crop.circle.badge.exclamationmark", color: .orange, title: "ログイン状態", subtitle: webClass.loginState == .checking ? "確認中" : "未ログイン", showsChevron: false)
                    }
                }

                SettingsCard(title: "アプリ情報", symbol: "info.circle.fill") {
                    SettingsLinkRow(symbol: "number", color: .gray, title: "バージョン", subtitle: "4.5.2 (31)", showsChevron: false)
                    SettingsDivider()
                    NavigationLink { AppInfoView() } label: {
                        SettingsLinkRow(symbol: "doc.text.magnifyingglass", color: .teal, title: "情報・ライセンス", subtitle: "取得元とOSSライセンス")
                    }.buttonStyle(.plain)
                    SettingsDivider()
                    NavigationLink { DeveloperSettingsView() } label: {
                        SettingsLinkRow(symbol: "hammer.fill", color: .orange, title: "開発者向け設定", subtitle: "試験的な機能")
                    }.buttonStyle(.plain)
                }
            }.padding(16)
        }
        .appScreenBackground()
        .navigationTitle("設定")
        .confirmationDialog("WebClassからログアウトしますか？", isPresented: $confirmingLogout, titleVisibility: .visible) {
            Button("ログアウト", role: .destructive) { webClass.logout() }
            Button("キャンセル", role: .cancel) {}
        } message: {
            Text("WebClassのセッションを終了します。Microsoftアカウントのパスワードはアプリに保存されていません。")
        }
    }
}

private struct SettingsCard<Content: View>: View {
    let title: String
    let symbol: String
    let content: Content
    init(title: String, symbol: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.symbol = symbol
        self.content = content()
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Label(title, systemImage: symbol)
                .font(.headline).foregroundStyle(.indigo)
                .padding(.horizontal, 15).padding(.top, 14).padding(.bottom, 7)
            content
        }.appCard(cornerRadius: 18)
    }
}

private struct SettingsLinkRow: View {
    let symbol: String
    let color: Color
    let title: String
    let subtitle: String
    var showsChevron = true
    var body: some View {
        HStack(spacing: 13) {
            AppIconTile(symbol: symbol, color: color, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.body.weight(.semibold)).foregroundStyle(.primary)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if showsChevron { Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary) }
        }.padding(.horizontal, 14).frame(minHeight: 66)
    }
}

private struct SettingsToggleRow: View {
    let symbol: String
    let color: Color
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 13) {
                AppIconTile(symbol: symbol, color: color, size: 40)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.body.weight(.semibold))
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
            }
        }.padding(.horizontal, 14).frame(minHeight: 66)
    }
}

private struct SettingsPickerRow<PickerContent: View>: View {
    let symbol: String
    let color: Color
    let title: String
    let picker: PickerContent
    init(symbol: String, color: Color, title: String, @ViewBuilder picker: () -> PickerContent) {
        self.symbol = symbol
        self.color = color
        self.title = title
        self.picker = picker()
    }
    var body: some View {
        HStack(spacing: 13) {
            AppIconTile(symbol: symbol, color: color, size: 40)
            Text(title).font(.body.weight(.semibold))
            Spacer()
            picker
        }.padding(.horizontal, 14).frame(minHeight: 66)
    }
}

private struct SettingsDivider: View {
    var body: some View { Divider().padding(.leading, 67) }
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

