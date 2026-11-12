import Foundation

actor TrainService {
    private let baseURL = URL(string: "https://doko-train.jp/json")!

    func stations(forceRefresh: Bool = false) async throws -> [TrainStation] {
        let cache = URL.cachesDirectory.appending(path: "dokotrain-stations.json")
        let data: Data
        if !forceRefresh, let cached = try? Data(contentsOf: cache) {
            data = cached
        } else {
            let url = baseURL.appending(path: "station/dokotrain_station_master.json")
            let result = try await URLSession.shared.data(from: url).0
            try? result.write(to: cache, options: .atomic)
            data = result
        }
        return try JSONDecoder().decode(TrainStationEnvelope.self, from: data).data
            .filter { !$0.name.isEmpty && !$0.stationID.isEmpty && $0.serviceFlag == 1 }
            .reduce(into: [String: TrainStation]()) { $0[$1.id] = $1 }.values
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    func departures(station: TrainStation, direction: String) async throws -> [TrainDeparture] {
        let path = "diagram/station/\(station.dokoLineID)/\(station.stationID)_\(direction).json"
        let data = try await URLSession.shared.data(from: baseURL.appending(path: path)).0
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let list = root?["ST_DIAGRAM"] as? [[String: Any]] ?? []
        let encoded = try JSONSerialization.data(withJSONObject: list)
        return try JSONDecoder().decode([TrainDeparture].self, from: encoded)
            .filter { !($0.departureTime ?? "").isEmpty && $0.runType != 6 }
            .sorted { ($0.departureEpoch ?? 0) < ($1.departureEpoch ?? 0) }
    }
}

enum BusData {
    static let weekdayStationEast = parse("07:15,08:00,09:45,10:25,11:20,11:50,13:00,13:45,14:48,15:35,15:45,16:46,17:35,17:45,18:20,18:52,19:40,20:30,21:50", stop: "木更津駅東口")
    static let weekdayStationWest = parse("06:25,06:45,07:05,07:30,07:51,08:08,08:20,08:25,08:37,09:05,09:30,10:30,11:10,12:08,12:25,13:05,13:25,14:05,14:25,15:10,16:01,16:25,17:00,17:27,18:00,18:25,19:15,20:15,21:00", stop: "木更津駅西口")
    static let weekdayKosenFront = parse("06:42,07:02,07:25,07:52,08:17,08:30,08:42,08:47,09:09,09:29,09:51,10:07,10:49,11:29,12:07,12:29,12:47,13:29,13:47,14:07,14:27,14:47,15:07,15:29,16:07,16:29,16:47,17:04,17:22,17:49,18:07,18:23,18:47,19:08,19:34,20:37,21:22", stop: "高専正門")
    static let weekdayKosenBack = parse("06:53,07:39,08:23,10:58,11:41,15:58,18:00,20:01,20:54", stop: "高専裏門")
    static let holidayStationEast = parse("06:30,07:10,08:30,08:50,09:30,11:30,12:10,13:50,14:30,15:10,15:50,16:30,17:10,17:50,18:50,19:40,20:30", stop: "木更津駅東口")
    static let holidayStationWest = parse("07:50,08:10,09:10,09:50,10:30,11:10,11:50,12:30,13:10,14:10,15:30,16:12,16:50,18:30,19:15,20:15", stop: "木更津駅西口")
    static let holidayKosenFront = parse("07:32,08:32,08:52,09:32,09:52,10:12,10:52,11:32,12:12,12:32,12:52,14:12,14:32,15:32,15:52,16:32,16:52,17:12,18:12,18:52,19:42,20:37", stop: "高専正門")
    static let holidayKosenBack = parse("06:53,08:13,09:13,11:53,13:33,14:53,16:13,17:33,19:13,20:03,20:54", stop: "高専裏門")

    static func departures(fromStation: Bool, holiday: Bool) -> [BusDeparture] {
        let values = fromStation
            ? (holiday ? holidayStationEast + holidayStationWest : weekdayStationEast + weekdayStationWest)
            : (holiday ? holidayKosenFront + holidayKosenBack : weekdayKosenFront + weekdayKosenBack)
        return values.sorted { $0.minutes < $1.minutes }
    }

    private static func parse(_ csv: String, stop: String) -> [BusDeparture] {
        csv.split(separator: ",").compactMap {
            let p = $0.split(separator: ":").compactMap { Int($0) }
            return p.count == 2 ? BusDeparture(hour: p[0], minute: p[1], stop: stop) : nil
        }
    }
}

