import SwiftUI

struct TimetableHubView: View {
    @State private var mode = 0
    var body: some View {
        VStack(spacing: 0) {
            Picker("交通機関", selection: $mode) {
                Label("小湊バス", systemImage: "bus.fill").tag(0)
                Label("電車", systemImage: "tram.fill").tag(1)
            }
            .pickerStyle(.segmented).padding([.horizontal, .top])
            if mode == 0 { BusTimetableView() } else { TrainStationListView() }
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct BusTimetableView: View {
    @State private var fromStation = true
    @State private var holiday = false

    private var departures: [BusDeparture] { BusData.departures(fromStation: fromStation, holiday: holiday) }

    var body: some View {
        VStack(spacing: 10) {
            Picker("方向", selection: $fromStation) {
                Text("木更津発").tag(true)
                Text("高専発").tag(false)
            }.pickerStyle(.segmented)
            Picker("曜日", selection: $holiday) {
                Text("平日").tag(false)
                Text("土休日").tag(true)
            }.pickerStyle(.segmented)
            ScrollViewReader { proxy in
                List(departures) { item in
                    HStack {
                        Text(item.label).font(.title3.monospacedDigit().weight(.semibold))
                        Spacer()
                        Text(item.stop).foregroundStyle(.secondary)
                    }.id(item.id)
                }
                .listStyle(.plain)
                .onAppear { scrollToNow(proxy) }
                .onChange(of: fromStation) { scrollToNow(proxy) }
                .onChange(of: holiday) { scrollToNow(proxy) }
            }
        }.padding(.horizontal).padding(.top, 8)
    }

    private func scrollToNow(_ proxy: ScrollViewProxy) {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: .now)
        let now = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        let target = departures.min(by: { abs($0.minutes - now) < abs($1.minutes - now) })
        guard let target else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { withAnimation(.easeInOut) { proxy.scrollTo(target.id, anchor: .center) } }
    }
}

@MainActor
final class TrainListModel: ObservableObject {
    @Published var stations: [TrainStation] = []
    @Published var isLoading = true
    @Published var error: String?
    private let service = TrainService()
    func load() async {
        isLoading = true
        do { stations = try await service.stations(); error = nil } catch { self.error = error.localizedDescription }
        isLoading = false
    }
}

struct TrainStationListView: View {
    @EnvironmentObject private var settings: AppSettings
    @StateObject private var model = TrainListModel()
    @State private var query = ""
    private var filtered: [TrainStation] {
        let sorted = model.stations.sorted { settings.trainFavorites.contains($0.id) && !settings.trainFavorites.contains($1.id) }
        return query.isEmpty ? sorted : sorted.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.kana.localizedCaseInsensitiveContains(query) }
    }
    var body: some View {
        Group {
            if model.isLoading { List(0..<8, id: \.self) { _ in RoundedRectangle(cornerRadius: 14).fill(.quaternary).frame(height: 62) }.redacted(reason: .placeholder) }
            else if let error = model.error { ContentUnavailableView("電車情報を取得できません", systemImage: "wifi.exclamationmark", description: Text(error)) }
            else {
                List(filtered) { station in
                    NavigationLink { TrainDetailView(station: station) } label: {
                        HStack {
                            VStack(alignment: .leading) { Text(station.name).font(.headline); Text(station.dokoLineName).font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            if settings.trainFavorites.contains(station.id) { Image(systemName: "star.fill").foregroundStyle(.yellow) }
                        }.padding(.vertical, 5)
                    }
                    .swipeActions { Button { settings.toggleFavorite(station.id) } label: { Label("お気に入り", systemImage: "star") }.tint(.yellow) }
                }.listStyle(.plain).searchable(text: $query, prompt: "駅名を検索")
            }
        }.task { await model.load() }
    }
}

struct TrainDetailView: View {
    let station: TrainStation
    @EnvironmentObject private var settings: AppSettings
    @State private var direction = "up"
    @State private var departures: [TrainDeparture] = []
    @State private var loading = true
    @State private var error: String?
    private let service = TrainService()

    var body: some View {
        VStack(spacing: 0) {
            Picker("方向", selection: $direction) { Text("上り").tag("up"); Text("下り").tag("down") }
                .pickerStyle(.segmented).padding()
            Group {
                if loading { List(0..<7, id: \.self) { _ in RoundedRectangle(cornerRadius: 12).fill(.quaternary).frame(height: 58) }.redacted(reason: .placeholder) }
                else if let error { ContentUnavailableView("時刻表を取得できません", systemImage: "wifi.exclamationmark", description: Text(error)) }
                else { departureList }
            }
        }
        .navigationTitle(station.name).navigationBarTitleDisplayMode(.inline)
        .toolbar { Button { settings.toggleFavorite(station.id) } label: { Image(systemName: settings.trainFavorites.contains(station.id) ? "star.fill" : "star") } }
        .task(id: direction) { await load() }
    }

    private var departureList: some View {
        ScrollViewReader { proxy in
            List(departures) { item in
                HStack {
                    Text(item.departureTime ?? "--:--").font(.title3.monospacedDigit().weight(.semibold))
                    VStack(alignment: .leading) { Text(item.destination ?? "行先不明"); if let name = item.trainName, !name.isEmpty { Text(name).font(.caption).foregroundStyle(.secondary) } }
                    Spacer()
                    if item.isCancelled { Text("運休").font(.caption.bold()).foregroundStyle(.red) }
                    else if let delay = item.liveDelay, !delay.isEmpty, delay != "0" { Text("+\(delay)分").font(.caption.bold()).foregroundStyle(.orange) }
                }.id(item.id)
            }
            .listStyle(.plain)
            .onAppear {
                let now = Date.now.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
                let target = departures.min { abs(minutes($0.departureTime) - minutes(now)) < abs(minutes($1.departureTime) - minutes(now)) }
                if let target { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { withAnimation(.easeInOut(duration: 0.45)) { proxy.scrollTo(target.id, anchor: .center) } } }
            }
        }
    }

    private func load() async {
        loading = true
        do { departures = try await service.departures(station: station, direction: direction); error = nil } catch { self.error = error.localizedDescription }
        loading = false
    }
    private func minutes(_ value: String?) -> Int {
        let p = (value ?? "").split(separator: ":").compactMap { Int($0) }; return p.count >= 2 ? p[0] * 60 + p[1] : 0
    }
}

