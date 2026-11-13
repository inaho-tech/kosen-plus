import SwiftUI

private struct CourseDayGroup: Identifiable {
    let day: String
    let courses: [WebClassCourse]
    var id: String { day }
}

struct RootView: View {
    fileprivate enum AppTab: Int, CaseIterable, Identifiable {
        case home, syllabus, timetable, tools, profile
        var id: Self { self }
        var title: String { switch self { case .home: "ホーム"; case .syllabus: "シラバス"; case .timetable: "時刻表"; case .tools: "ツール"; case .profile: "マイページ" } }
        var symbol: String { switch self { case .home: "graduationcap.fill"; case .syllabus: "doc.text.fill"; case .timetable: "clock.fill"; case .tools: "hammer.fill"; case .profile: "person.crop.circle.fill" } }
    }

    @State private var selection = AppTab.home

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selection) {
                ForEach(AppTab.allCases) { tab in
                    NavigationStack { tabContent(tab) }
                        .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 72) }
                        .toolbar(.hidden, for: .tabBar)
                        .tag(tab)
                }
            }
            PillNavigation(selection: $selection)
                .padding(.horizontal, 14)
                .padding(.bottom, 7)
        }
        .tint(.indigo)
    }

    @ViewBuilder private func tabContent(_ tab: AppTab) -> some View {
        switch tab {
        case .home: HomeView()
        case .syllabus: SyllabusView()
        case .timetable: TimetableHubView()
        case .tools: ToolsView()
        case .profile: SettingsView()
        }
    }
}

private struct PillNavigation: View {
    @Binding var selection: RootView.AppTab
    @Namespace private var selectionAnimation

    var body: some View {
        HStack(spacing: 4) {
            ForEach(RootView.AppTab.allCases) { tab in
                Button {
                    withAnimation(.snappy(duration: 0.32)) { selection = tab }
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: tab.symbol).font(.system(size: 18, weight: .semibold))
                        Text(tab.title).font(.system(size: 10, weight: .semibold)).lineLimit(1)
                    }
                    .foregroundStyle(selection == tab ? Color.indigo : Color.secondary)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .background {
                        if selection == tab {
                            Capsule().fill(.indigo.opacity(0.14)).matchedGeometryEffect(id: "selection", in: selectionAnimation)
                        }
                    }
                    .contentShape(Rectangle())
                }.buttonStyle(.plain)
            }
        }
        .padding(6)
        .frame(maxWidth: 620)
        .liquidGlassPill()
        .shadow(color: .black.opacity(0.13), radius: 18, y: 8)
    }
}

struct HomeView: View {
    @EnvironmentObject private var session: WebClassSession
    @EnvironmentObject private var settings: AppSettings
    @State private var showLogin = false
    @State private var query = ""
    @State private var filter = CourseFilter.all
    @AppStorage("hiddenCourseIDs") private var hiddenCourseIDsValue = ""

    private enum CourseFilter: String, CaseIterable, Identifiable {
        case all = "すべて"
        case tasks = "期限間近"
        case bookmarks = "お気に入り"
        case hidden = "非表示"
        var id: Self { self }
    }

    private var hiddenCourseIDs: Set<String> { Set(hiddenCourseIDsValue.split(separator: "|").map(String.init)) }

    private var visibleCourses: [WebClassCourse] {
        session.courses.filter { course in
            let matchesQuery = query.isEmpty || course.title.localizedCaseInsensitiveContains(query) || course.code.localizedCaseInsensitiveContains(query)
            let hidden = hiddenCourseIDs.contains(course.id)
            let matchesFilter = switch filter {
            case .all: !hidden
            case .tasks: !hidden && course.isDueSoon
            case .bookmarks: !hidden && settings.courseBookmarks.contains(course.id)
            case .hidden: hidden
            }
            return matchesQuery && matchesFilter
        }
    }

    private var groupedCourses: [CourseDayGroup] {
        let order = ["月", "火", "水", "木", "金", "土", "日"]
        var result = order.compactMap { day -> CourseDayGroup? in
            let values = visibleCourses.filter { $0.day == day }.sorted { ($0.period ?? 99) < ($1.period ?? 99) }
            return values.isEmpty ? nil : CourseDayGroup(day: "\(day)曜日", courses: values)
        }
        let other = visibleCourses.filter { course in !order.contains(course.day ?? "") }
        if !other.isEmpty { result.append(CourseDayGroup(day: "その他", courses: other)) }
        return result
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
                    if !session.years.isEmpty || !session.semesters.isEmpty {
                        HStack(spacing: 10) {
                            if !session.years.isEmpty {
                                Menu {
                                    ForEach(session.years) { option in Button(option.label) { session.selectSchedule(year: option.value) } }
                                } label: {
                                    ScheduleMenuLabel(text: session.years.first(where: { $0.value == session.currentYear })?.label ?? session.currentYear, symbol: "calendar")
                                }.frame(maxWidth: .infinity)
                            }
                            if !session.semesters.isEmpty {
                                Menu {
                                    ForEach(session.semesters) { option in Button(option.label) { session.selectSchedule(semester: option.value) } }
                                } label: {
                                    ScheduleMenuLabel(text: session.semesters.first(where: { $0.value == session.currentSemester })?.label ?? session.currentSemester, symbol: "rectangle.split.2x1")
                                }.frame(maxWidth: .infinity)
                            }
                        }
                    }
                    HStack {
                        Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                        TextField("コース名やIDで検索", text: $query)
                        if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) } }
                    }
                    .padding(.horizontal, 13).frame(height: 48).appCard(cornerRadius: 14)
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
                        ForEach(groupedCourses) { group in
                            AppSectionHeader(title: group.day, subtitle: "\(group.courses.count)件", symbol: group.day == "その他" ? "square.grid.2x2" : "calendar")
                                .padding(.top, 5)
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 320), spacing: 12)], spacing: 12) {
                                ForEach(group.courses) { course in
                                    NavigationLink { CourseContentsView(course: course) } label: { CourseCard(course: course, bookmarked: settings.courseBookmarks.contains(course.id)) }.buttonStyle(.plain)
                                        .contextMenu {
                                            Button(settings.courseBookmarks.contains(course.id) ? "お気に入りから削除" : "お気に入りに追加", systemImage: settings.courseBookmarks.contains(course.id) ? "bookmark.slash" : "bookmark") { settings.toggleCourseBookmark(course.id) }
                                            Button(filter == .hidden ? "一覧に戻す" : "非表示にする", systemImage: filter == .hidden ? "eye" : "eye.slash") { toggleHidden(course.id) }
                                        }
                                }
                            }
                        }
                    }
                }
            }.padding(16).appReadableContentWidth()
        }
        .refreshable { session.refresh() }
        .appScreenBackground()
    }

    private func toggleHidden(_ id: String) {
        var values = hiddenCourseIDs
        if values.contains(id) { values.remove(id) } else { values.insert(id) }
        hiddenCourseIDsValue = values.sorted().joined(separator: "|")
    }
}

private struct ScheduleMenuLabel: View {
    let text: String
    let symbol: String
    var body: some View {
        HStack(spacing: 7) {
            Image(systemName: symbol)
            Text(text.isEmpty ? "選択" : text).lineLimit(1)
            Image(systemName: "chevron.down").font(.caption2)
        }
        .font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
        .padding(.horizontal, 14).frame(maxWidth: .infinity).frame(height: 44).appCard(cornerRadius: 12).contentShape(Rectangle())
    }
}

private struct CourseCard: View {
    let course: WebClassCourse
    let bookmarked: Bool
    private var appearance: CourseAppearance { .course(course.title) }
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: appearance.symbol)
                .font(.title3).foregroundStyle(appearance.color)
                .frame(width: 46, height: 46)
                .background(appearance.color.opacity(0.12), in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 5) {
                Text(course.title).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                if !course.code.isEmpty { Text(course.code).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
            if course.isDueSoon {
                Image(systemName: "exclamationmark.clock.fill").foregroundStyle(.orange)
            }
            if bookmarked { Image(systemName: "bookmark.fill").foregroundStyle(.indigo) }
            if course.unreadCount > 0 {
                Text("\(course.unreadCount)").font(.caption.bold()).foregroundStyle(.white)
                    .padding(.horizontal, 7).padding(.vertical, 3).background(.red, in: Capsule())
            }
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
        }
        .padding(15).appCard()
    }
}

struct BookmarksView: View {
    var body: some View {
        ContentUnavailableView("ブックマークはありません", systemImage: "bookmark", description: Text("WebClassの教材画面から追加できます"))
            .navigationTitle("ブックマーク")
    }
}
