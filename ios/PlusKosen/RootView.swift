import SwiftUI

struct RootView: View {
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack { HomeView() }
                .tabItem { Label("ホーム", systemImage: "graduationcap.fill") }.tag(0)
            NavigationStack { SyllabusView() }
                .tabItem { Label("シラバス", systemImage: "doc.text.fill") }.tag(1)
            NavigationStack { TimetableHubView() }
                .tabItem { Label("時刻表", systemImage: "clock.fill") }.tag(2)
            NavigationStack { ToolsView() }
                .tabItem { Label("ツール", systemImage: "hammer.fill") }.tag(3)
            NavigationStack { SettingsView() }
                .tabItem { Label("設定", systemImage: "person.crop.circle.fill") }.tag(4)
        }
        .tint(.indigo)
    }
}

struct HomeView: View {
    @EnvironmentObject private var session: WebClassSession
    @State private var showLogin = false
    @State private var query = ""
    @State private var filter = CourseFilter.all

    private enum CourseFilter: String, CaseIterable, Identifiable {
        case all = "すべて"
        case tasks = "期限間近"
        var id: Self { self }
    }

    private var visibleCourses: [WebClassCourse] {
        session.courses.filter { course in
            let matchesQuery = query.isEmpty || course.title.localizedCaseInsensitiveContains(query) || course.code.localizedCaseInsensitiveContains(query)
            return matchesQuery && (filter == .all || course.isDueSoon)
        }
    }

    var body: some View {
        Group {
            if session.loginState == .signedOut && session.courses.isEmpty { signedOutView }
            else { courseList }
        }
        .navigationTitle("WebClass+")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink { WebClassView(startURL: WebClassSession.messagesURL) } label: { Image(systemName: "envelope") }
                Button { session.refresh() } label: { Image(systemName: "arrow.clockwise") }.disabled(session.isLoading)
            }
        }
        .fullScreenCover(isPresented: $showLogin) {
            NavigationStack {
                WebClassView(startURL: WebClassSession.loginURL) { url in
                    if WebClassSession.isAuthenticatedHome(url) {
                        session.loginCompleted(at: url)
                        showLogin = false
                    }
                }
                .navigationTitle("WebClassにログイン")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("閉じる") { showLogin = false } } }
            }
        }
    }

    private var signedOutView: some View {
        ContentUnavailableView {
            Label("WebClassにログイン", systemImage: "person.badge.key.fill")
        } description: {
            Text("学校のMicrosoftアカウントでログインすると、授業と教材をアプリの画面で確認できます。認証情報はアプリに保存しません。")
        } actions: {
            Button("ログイン") { showLogin = true }.buttonStyle(.borderedProminent)
        }
    }

    private var courseList: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if session.isLoading && session.courses.isEmpty {
                    ForEach(0..<5, id: \.self) { _ in RoundedRectangle(cornerRadius: 18).fill(.quaternary).frame(height: 92) }
                } else {
                    if !session.userName.isEmpty {
                        HStack {
                            Image(systemName: "person.crop.circle.fill").foregroundStyle(.indigo)
                            Text(session.userName).font(.headline)
                            Spacer()
                        }
                        .padding(14).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    }
                    TextField("コース名やIDで検索", text: $query).textFieldStyle(.roundedBorder)
                    Picker("表示", selection: $filter) {
                        ForEach(CourseFilter.allCases) { Text($0.rawValue).tag($0) }
                    }.pickerStyle(.segmented)

                    if let error = session.errorMessage {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote).foregroundStyle(.orange).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    if visibleCourses.isEmpty && !session.isLoading {
                        ContentUnavailableView("コースが見つかりません", systemImage: "book.closed", description: Text(query.isEmpty ? "WebClassを再読み込みしてください。" : "検索条件を変更してください。"))
                    } else {
                        ForEach(visibleCourses) { course in
                            NavigationLink { CourseContentsView(course: course) } label: { CourseCard(course: course) }.buttonStyle(.plain)
                        }
                    }
                }
            }.padding()
        }
        .refreshable { session.refresh() }
    }
}

private struct CourseCard: View {
    let course: WebClassCourse
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: course.isDueSoon ? "exclamationmark.clock.fill" : "book.closed.fill")
                .font(.title3).foregroundStyle(course.isDueSoon ? .orange : .indigo)
                .frame(width: 46, height: 46)
                .background((course.isDueSoon ? Color.orange : Color.indigo).opacity(0.12), in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 5) {
                Text(course.title).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                if !course.code.isEmpty { Text(course.code).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
            if course.unreadCount > 0 {
                Text("\(course.unreadCount)").font(.caption.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 3).background(.red, in: Capsule())
            }
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
        }
        .padding(15).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
    }
}

struct BookmarksView: View {
    var body: some View {
        ContentUnavailableView("ブックマークはありません", systemImage: "bookmark", description: Text("WebClassの教材画面から追加できます"))
            .navigationTitle("ブックマーク")
    }
}
