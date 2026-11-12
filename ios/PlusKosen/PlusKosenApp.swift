import SwiftUI

@main
struct PlusKosenApp: App {
    @StateObject private var settings = AppSettings()
    @StateObject private var webClass = WebClassSession()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(webClass)
                .preferredColorScheme(settings.colorScheme)
        }
    }
}

@MainActor
final class AppSettings: ObservableObject {
    @AppStorage("appearance") var appearance = "system" { willSet { objectWillChange.send() } }
    @AppStorage("webViewDarkMode") var webViewDarkMode = true { willSet { objectWillChange.send() } }
    @AppStorage("trainFavorites") private var trainFavoritesValue = "" { willSet { objectWillChange.send() } }

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
}

