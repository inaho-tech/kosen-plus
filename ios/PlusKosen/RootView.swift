import SwiftUI

struct RootView: View {
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack { HomeView() }
                .tabItem { Label("ホーム", systemImage: "house.fill") }.tag(0)
            NavigationStack { TimetableHubView() }
                .tabItem { Label("時刻表", systemImage: "clock.fill") }.tag(1)
            NavigationStack { ToolsView() }
                .tabItem { Label("ツール", systemImage: "square.grid.2x2.fill") }.tag(2)
            NavigationStack { SettingsView() }
                .tabItem { Label("設定", systemImage: "gearshape.fill") }.tag(3)
        }
        .tint(.indigo)
    }
}

struct HomeView: View {
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                NavigationLink { WebClassView(startURL: URL(string: "https://webclass.kosen-k.go.jp/webclass/")!) } label: {
                    FeatureCard(title: "WebClass", subtitle: "授業・課題・教材を確認", symbol: "graduationcap.fill", color: .indigo)
                }
                NavigationLink { WebClassView(startURL: URL(string: "https://webclass.kosen-k.go.jp/webclass/messages.php/inbox")!) } label: {
                    FeatureCard(title: "メッセージ", subtitle: "受信箱を開く", symbol: "envelope.fill", color: .blue)
                }
                NavigationLink { SyllabusView() } label: {
                    FeatureCard(title: "シラバス", subtitle: "科目情報と授業計画を検索", symbol: "book.closed.fill", color: .orange)
                }
                NavigationLink { BookmarksView() } label: {
                    FeatureCard(title: "ブックマーク", subtitle: "保存した教材を表示", symbol: "bookmark.fill", color: .pink)
                }
            }
            .padding()
        }
        .navigationTitle("高専プラス")
    }
}

struct FeatureCard: View {
    let title: String
    let subtitle: String
    let symbol: String
    let color: Color

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: symbol).font(.title2).foregroundStyle(.white)
                .frame(width: 48, height: 48).background(color.gradient, in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline).foregroundStyle(.primary)
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
        }
        .padding(16).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
}

struct SyllabusView: View {
    @State private var query = ""
    var body: some View {
        WebClassView(startURL: URL(string: "https://syllabus.kosen-k.go.jp/Pages/PublicDepartments?school_id=14")!)
            .navigationTitle("シラバス")
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct BookmarksView: View {
    var body: some View {
        ContentUnavailableView("ブックマークはありません", systemImage: "bookmark", description: Text("WebClassの教材画面から追加できます"))
            .navigationTitle("ブックマーク")
    }
}

