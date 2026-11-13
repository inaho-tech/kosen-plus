import SwiftUI

@main
struct PlusKosenApp: App {
    @StateObject private var settings = AppSettings()
    @StateObject private var webClass = WebClassSession()

    var body: some Scene {
        WindowGroup {
            AppRootContainer()
                .environmentObject(settings)
                .environmentObject(webClass)
                .preferredColorScheme(settings.colorScheme)
        }
    }
}

private struct AppRootContainer: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showsOpening = true

    var body: some View {
        ZStack {
            RootView()
                .allowsHitTesting(!showsOpening)
            if showsOpening {
                OpeningAnimationView(reduceMotion: reduceMotion)
                    .transition(.opacity.combined(with: .scale(scale: 1.04)))
                    .zIndex(10)
            }
        }
        .task {
            try? await Task.sleep(for: .milliseconds(reduceMotion ? 450 : 1450))
            withAnimation(.easeInOut(duration: reduceMotion ? 0.15 : 0.4)) { showsOpening = false }
        }
    }
}

@MainActor
final class AppSettings: ObservableObject {
    @AppStorage("appearance") var appearance = "system" { willSet { objectWillChange.send() } }
    @AppStorage("webViewDarkMode") var webViewDarkMode = true { willSet { objectWillChange.send() } }
    @AppStorage("trainFavorites") private var trainFavoritesValue = "" { willSet { objectWillChange.send() } }
    @AppStorage("courseBookmarks") private var courseBookmarksValue = "" { willSet { objectWillChange.send() } }

    var colorScheme: ColorScheme? {
        switch appearance {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    var trainFavorites: Set<String> {
        get { Set(trainFavoritesValue.split(separator: "|").map(String.init)) }
        set { trainFavoritesValue = newValue.sorted().joined(separator: "|") }
    }

    func toggleFavorite(_ id: String) {
        var values = trainFavorites
        if values.contains(id) { values.remove(id) } else { values.insert(id) }
        trainFavorites = values
        objectWillChange.send()
    }

    var courseBookmarks: Set<String> {
        get { Set(courseBookmarksValue.split(separator: "|").map(String.init)) }
        set { courseBookmarksValue = newValue.sorted().joined(separator: "|") }
    }

    func toggleCourseBookmark(_ id: String) {
        var values = courseBookmarks
        if values.contains(id) { values.remove(id) } else { values.insert(id) }
        courseBookmarks = values
        objectWillChange.send()
    }
}

