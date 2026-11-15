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
        var title: String { switch self { case .home: "WebClass"; case .syllabus: "シラバス"; case .timetable: "時刻表"; case .tools: "ツール"; case .profile: "マイページ" } }
        var symbol: String { switch self { case .home: "graduationcap.fill"; case .syllabus: "doc.text.fill"; case .timetable: "clock.fill"; case .tools: "hammer.fill"; case .profile: "person.crop.circle.fill" } }
    }

    @State private var selection = AppTab.home

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack { tabContent(tab) }
                    .toolbar(.hidden, for: .tabBar)
                    .tag(tab)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(.clear)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            PillNavigation(selection: $selection)
                .padding(.horizontal, 14)
                .padding(.top, 4)
                .padding(.bottom, 5)
                .background(Color.clear)
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
    @State private var dragLocation: CGFloat?

    var body: some View {
        GeometryReader { geometry in
            let tabs = RootView.AppTab.allCases
            let slotWidth = geometry.size.width / CGFloat(tabs.count)
            let selectedIndex = CGFloat(tabs.firstIndex(of: selection) ?? 0)
            let indicatorCenter = dragLocation ?? ((selectedIndex + 0.5) * slotWidth)
            let activeIndex = dragLocation.map { min(max(Int($0 / slotWidth), 0), tabs.count - 1) } ?? Int(selectedIndex)

            ZStack(alignment: .leading) {
                Capsule().fill(.clear).liquidGlassPill()
                Capsule()
                    .fill(.primary.opacity(0.045))
                    .frame(width: max(44, slotWidth - 8), height: 52)
                    .offset(x: min(max(4, indicatorCenter - slotWidth / 2 + 4), geometry.size.width - slotWidth + 4))
                    .liquidGlassSelection()
                    .allowsHitTesting(false)
                HStack(spacing: 0) {
                    ForEach(Array(tabs.enumerated()), id: \.element) { index, tab in
                        VStack(spacing: 3) {
                            Image(systemName: tab.symbol).font(.system(size: 17, weight: .medium))
                            Text(tab.title).font(.system(size: 10, weight: .medium)).lineLimit(1)
                        }
                        .foregroundStyle(activeIndex == index ? Color.indigo : Color.secondary)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .contentShape(Rectangle())
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel(tab.title)
                        .accessibilityAddTraits(activeIndex == index ? [.isButton, .isSelected] : .isButton)
                        .accessibilityAction { withAnimation(.snappy(duration: 0.3)) { selection = tab } }
                    }
                }.padding(6)
            }
            .contentShape(Capsule())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in
                        let x = min(max(value.location.x, slotWidth / 2), geometry.size.width - slotWidth / 2)
                        dragLocation = x
                    }
                    .onEnded { value in
                        let x = min(max(value.location.x, slotWidth / 2), geometry.size.width - slotWidth / 2)
                        let index = min(max(Int(x / slotWidth), 0), tabs.count - 1)
                        withAnimation(.snappy(duration: 0.3)) {
                            selection = tabs[index]
                            dragLocation = nil
                        }
                    }
            )
        }
        .frame(maxWidth: 620)
        .frame(height: 64)
        .shadow(color: .black.opacity(0.08), radius: 12, y: 5)
        .onChange(of: selection) { dragLocation = nil }
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
        .navigationTitle("WebClass")
        .navigationBarTitleDisplayMode(.inline)
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
        HStack(spacing: 12) {
            Image(systemName: appearance.symbol)
                .font(.system(size: 18, weight: .medium)).foregroundStyle(.indigo)
                .frame(width: 38, height: 38)
                .background(.primary.opacity(0.045), in: RoundedRectangle(cornerRadius: 11))
            VStack(alignment: .leading, spacing: 3) {
                Text(course.title).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                if !course.code.isEmpty { Text(course.code).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
            if course.unreadCount > 0 {
                Text("\(course.unreadCount)").font(.caption.weight(.semibold)).foregroundStyle(.indigo)
                    .padding(.horizontal, 7).padding(.vertical, 3).background(.indigo.opacity(0.1), in: Capsule())
            } else if course.isDueSoon {
                Image(systemName: "clock.badge.exclamationmark").foregroundStyle(.secondary)
            } else if bookmarked {
                Image(systemName: "bookmark.fill").foregroundStyle(.secondary)
            }
        }
        .padding(13).appCard(cornerRadius: 15)
    }
}

struct BookmarksView: View {
    var body: some View {
        ContentUnavailableView("ブックマークはありません", systemImage: "bookmark", description: Text("WebClassの教材画面から追加できます"))
            .navigationTitle("ブックマーク")
    }
}
