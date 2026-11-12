import Foundation

struct TrainStation: Codable, Identifiable, Hashable {
    let lineID: String
    let lineName: String
    let dokoLineID: String
    let dokoLineName: String
    let stationID: String
    let name: String
    let kana: String
    let serviceFlag: Int
    let upFlag: Int
    let downFlag: Int

    var id: String { "\(dokoLineID):\(stationID)" }

    enum CodingKeys: String, CodingKey {
        case lineID = "LINE_ID", lineName = "LINE_NAME"
        case dokoLineID = "LINE_dokotrain_ID", dokoLineName = "LINE_dokotrain_NAME"
        case stationID = "STATION_ID", name = "STATION_NAME_KANJI", kana = "STATION_NAME_KANA"
        case serviceFlag = "service_flag", upFlag = "STAD_FLAG_UP", downFlag = "STAD_FLAG_DOWN"
    }
}

struct TrainStationEnvelope: Codable { let data: [TrainStation] }

struct TrainDeparture: Codable, Identifiable, Hashable {
    let departureTime: String?
    let departureEpoch: Int64?
    let trainID: String?
    let destination: String?
    let trainName: String?
    let cars: String?
    let runType: Int?
    let cancelFlag: String?
    let suspensionFlag: String?
    let liveDelay: String?
    let liveCanceled: String?

    var id: String { "\(trainID ?? UUID().uuidString)-\(departureTime ?? "")" }
    var isCancelled: Bool { cancelFlag == "T" || suspensionFlag == "1" || liveCanceled == "1" }

    enum CodingKeys: String, CodingKey {
        case departureTime = "STD", departureEpoch = "STD_TM", trainID = "TRAIN_ID"
        case destination = "END_STATION", trainName = "TRAIN_NNAME", cars = "UNIT_INFO"
        case runType = "RUN_TYPE", cancelFlag = "CANCEL_FLAG", suspensionFlag = "SSPNSN_FLAG"
        case liveDelay = "ad_latency", liveCanceled = "ad_canceled"
    }
}

struct BusDeparture: Identifiable, Hashable {
    let hour: Int
    let minute: Int
    let stop: String
    var id: String { "\(stop)-\(hour)-\(minute)" }
    var label: String { String(format: "%02d:%02d", hour, minute) }
    var minutes: Int { hour * 60 + minute }
}

struct ToolDestination: Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let symbol: String
}

