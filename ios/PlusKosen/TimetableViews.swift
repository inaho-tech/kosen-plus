import SwiftUI

struct TimetableHubView: View {
    @State private var mode = 0
    var body: some View {
        VStack(spacing: 0) {
            Picker("交通機関", selection: $mode) {
                Label("小湊バス", systemImage: "bus.fill").tag(0)
                Label("電車", systemImage: "tram.fill").tag(1)
            }
            .pickerStyle(.segmented).padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 8)
            if mode == 0 { BusTimetableView() } else { TrainStationListView() }
        }
        .appScreenBackground()
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct BusTimetableView: View {
    @State private var fromStation = true
    @State private var holiday = false

    private var departures: [BusDeparture] { BusData.departures(fromStation: fromStation, holiday: holiday) }

    var body: some View {
        VStack(spacing: 12) {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    AppIconTile(symbol: "bus.fill", color: .green, size: 48)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(fromStation ? "木更津駅から高専" : "高専から木更津駅").font(.headline)
                        Text(holiday ? "土休日ダイヤ" : "平日ダイヤ").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                }
                Picker("方向", selection: $fromStation) {
                    Text("木更津発").tag(true)
                    Text("高専発").tag(false)
                }.pickerStyle(.segmented)
                Picker("曜日", selection: $holiday) {
                    Text("平日").tag(false)
                    Text("土休日").tag(true)
                }.pickerStyle(.segmented)
            }.padding(14).appCard().padding(.horizontal, 16)

            ScrollViewReader { proxy in
                List(departures) { item in
                    HStack(spacing: 14) {
                        Text(item.label).font(.title3.monospacedDigit().weight(.bold)).foregroundStyle(.green).frame(width: 58, alignment: .leading)
                        AppIconTile(symbol: "bus.fill", color: .green, size: 36)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.stop).font(.headline)
                            Text(fromStation ? "高専方面" : "木更津駅方面").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                    }.id(item.id)
                }
                .listStyle(.plain).scrollContentBackground(.hidden)
                .onAppear { scrollToNow(proxy) }
                .onChange(of: fromStation) { scrollToNow(proxy) }
                .onChange(of: holiday) { scrollToNow(proxy) }
            }
        }.padding(.top, 4).appScreenBackground()
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
    @State private var pendingRemoval: TrainStation?

    private var filtered: [TrainStation] {
        let values = query.isEmpty ? model.stations : model.stations.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.kana.localizedCaseInsensitiveContains(query) }
        return values.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    private var favorites: [TrainStation] { filtered.filter { settings.trainFavorites.contains($0.id) } }

    var body: some View {
        Group {
            if model.isLoading { List(0..<8, id: \.self) { _ in RoundedRectangle(cornerRadius: 14).fill(.quaternary).frame(height: 62) }.redacted(reason: .placeholder).scrollDisabled(true) }
            else if let error = model.error { ContentUnavailableView("電車情報を取得できません", systemImage: "wifi.exclamationmark", description: Text(error)) }
            else {
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if !favorites.isEmpty {
                            AppSectionHeader(title: "お気に入り", subtitle: "よく使う駅", symbol: "star.fill")
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 145), spacing: 10)], spacing: 10) {
                                ForEach(favorites) { station in
                                    NavigationLink { TrainDetailView(station: station) } label: {
                                        FavoriteStationCard(station: station) { pendingRemoval = station }
                                    }.buttonStyle(.plain)
                                }
                            }
                        }

                        AppSectionHeader(title: "すべての駅", subtitle: "\(filtered.count)駅", symbol: "tram.fill")
                            .padding(.top, favorites.isEmpty ? 0 : 8)
                        ForEach(filtered) { station in
                            NavigationLink { TrainDetailView(station: station) } label: {
                                StationCard(station: station, favorite: settings.trainFavorites.contains(station.id)) {
                                    if settings.trainFavorites.contains(station.id) { pendingRemoval = station }
                                    else { settings.toggleFavorite(station.id) }
                                }
                            }.buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
                .searchable(text: $query, prompt: "駅名・よみがなで検索")
                .appScreenBackground()
            }
        }
        .task { await model.load() }
        .alert("お気に入りから削除しますか？", isPresented: Binding(get: { pendingRemoval != nil }, set: { if !$0 { pendingRemoval = nil } })) {
            Button("削除", role: .destructive) { if let station = pendingRemoval { settings.toggleFavorite(station.id) }; pendingRemoval = nil }
            Button("キャンセル", role: .cancel) { pendingRemoval = nil }
        } message: { Text(pendingRemoval?.name ?? "") }
    }
}

private struct FavoriteStationCard: View {
    let station: TrainStation
    let remove: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                AppIconTile(symbol: "tram.fill", color: .indigo, size: 38)
                Spacer()
                Button(action: remove) { Image(systemName: "star.fill").foregroundStyle(.yellow) }.buttonStyle(.plain)
            }
            Text(station.name).font(.headline).foregroundStyle(.primary).lineLimit(1)
            Text(station.dokoLineName).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }.padding(13).frame(maxWidth: .infinity, alignment: .leading).appCard()
    }
}

private struct StationCard: View {
    let station: TrainStation
    let favorite: Bool
    let toggleFavorite: () -> Void
    var body: some View {
        HStack(spacing: 13) {
            AppIconTile(symbol: "tram.fill", color: .indigo)
            VStack(alignment: .leading, spacing: 4) {
                Text(station.name).font(.headline).foregroundStyle(.primary)
                Text(station.dokoLineName).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Button(action: toggleFavorite) { Image(systemName: favorite ? "star.fill" : "star").foregroundStyle(favorite ? .yellow : .secondary).frame(width: 38, height: 38) }.buttonStyle(.plain)
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
        }.padding(13).appCard()
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
            HStack(spacing: 13) {
                AppIconTile(symbol: "tram.fill", color: .indigo, size: 52)
                VStack(alignment: .leading, spacing: 4) {
                    Text(station.name).font(.title3.bold())
                    Text(station.dokoLineName).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Button { settings.toggleFavorite(station.id) } label: {
                    Image(systemName: settings.trainFavorites.contains(station.id) ? "star.fill" : "star")
                        .foregroundStyle(settings.trainFavorites.contains(station.id) ? .yellow : .secondary).font(.title3)
                }
            }.padding(14).appCard().padding(.horizontal, 16).padding(.top, 12)

            Picker("方向", selection: $direction) {
                Label("上り", systemImage: "arrow.up").tag("up")
                Label("下り", systemImage: "arrow.down").tag("down")
            }
            .pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 12)
            Group {
                if loading { List(0..<7, id: \.self) { _ in RoundedRectangle(cornerRadius: 12).fill(.quaternary).frame(height: 58) }.redacted(reason: .placeholder) }
                else if let error { ContentUnavailableView("時刻表を取得できません", systemImage: "wifi.exclamationmark", description: Text(error)) }
                else { departureList }
            }
        }
        .appScreenBackground()
        .simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { value in
            guard abs(value.translation.width) > abs(value.translation.height), abs(value.translation.width) > 65 else { return }
            withAnimation(.snappy) { direction = value.translation.width < 0 ? "down" : "up" }
        })
        .navigationTitle(station.name).navigationBarTitleDisplayMode(.inline)
        .task(id: direction) { await load() }
    }

    private var departureList: some View {
        ScrollViewReader { proxy in
            List(departures) { item in
                HStack(spacing: 14) {
                    Text(item.departureTime ?? "--:--").font(.title3.monospacedDigit().weight(.bold)).foregroundStyle(.indigo).frame(width: 58, alignment: .leading)
                    Rectangle().fill(.indigo.opacity(0.28)).frame(width: 2, height: 44).overlay(Circle().fill(.indigo).frame(width: 8, height: 8))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(item.destination ?? "行先不明") 行").font(.headline)
                        HStack(spacing: 6) {
                            if let name = item.trainName, !name.isEmpty { Text(name) }
                            if let cars = item.cars, !cars.isEmpty { Text("\(cars)両") }
                        }.font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if item.isCancelled { Text("運休").font(.caption.bold()).foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 5).background(.red, in: Capsule()) }
                    else if let delay = item.liveDelay, !delay.isEmpty, delay != "0" { Text("+\(delay)分").font(.caption.bold()).foregroundStyle(.orange) }
                }.id(item.id)
            }
            .listStyle(.plain).scrollContentBackground(.hidden)
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

