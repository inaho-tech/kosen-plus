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

    var id: String { "\(trainID ?? destination ?? "train")-\(departureTime ?? "")" }
    var isCancelled: Bool { cancelFlag == "T" || suspensionFlag == "1" || liveCanceled == "1" }

    enum CodingKeys: String, CodingKey {
        case departureTime = "STD", departureEpoch = "STD_TM", trainID = "TRAIN_ID"
        case destination = "END_STATION", trainName = "TRAIN_NNAME", cars = "UNIT_INFO"
        case runType = "RUN_TYPE", cancelFlag = "CANCEL_FLAG", suspensionFlag = "SSPNSN_FLAG"
        case liveDelay = "ad_latency", liveCanceled = "ad_canceled"
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        departureTime = try values.decodeFlexibleStringIfPresent(forKey: .departureTime)
        departureEpoch = try values.decodeFlexibleInt64IfPresent(forKey: .departureEpoch)
        trainID = try values.decodeFlexibleStringIfPresent(forKey: .trainID)
        destination = try values.decodeFlexibleStringIfPresent(forKey: .destination)
        trainName = try values.decodeFlexibleStringIfPresent(forKey: .trainName)
        cars = try values.decodeFlexibleStringIfPresent(forKey: .cars)
        runType = try values.decodeFlexibleIntIfPresent(forKey: .runType)
        cancelFlag = try values.decodeFlexibleStringIfPresent(forKey: .cancelFlag)
        suspensionFlag = try values.decodeFlexibleStringIfPresent(forKey: .suspensionFlag)
        liveDelay = try values.decodeFlexibleStringIfPresent(forKey: .liveDelay)
        liveCanceled = try values.decodeFlexibleStringIfPresent(forKey: .liveCanceled)
    }

    func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encodeIfPresent(departureTime, forKey: .departureTime)
        try values.encodeIfPresent(departureEpoch, forKey: .departureEpoch)
        try values.encodeIfPresent(trainID, forKey: .trainID)
        try values.encodeIfPresent(destination, forKey: .destination)
        try values.encodeIfPresent(trainName, forKey: .trainName)
        try values.encodeIfPresent(cars, forKey: .cars)
        try values.encodeIfPresent(runType, forKey: .runType)
        try values.encodeIfPresent(cancelFlag, forKey: .cancelFlag)
        try values.encodeIfPresent(suspensionFlag, forKey: .suspensionFlag)
        try values.encodeIfPresent(liveDelay, forKey: .liveDelay)
        try values.encodeIfPresent(liveCanceled, forKey: .liveCanceled)
    }
}

private extension KeyedDecodingContainer {
    func decodeFlexibleStringIfPresent(forKey key: Key) throws -> String? {
        guard contains(key) else { return nil }
        if try decodeNil(forKey: key) { return nil }
        if let value = try? decode(String.self, forKey: key) { return value }
        if let value = try? decode(Int64.self, forKey: key) { return String(value) }
        if let value = try? decode(Double.self, forKey: key) { return String(value) }
        if let value = try? decode(Bool.self, forKey: key) { return value ? "1" : "0" }
        return nil
    }

    func decodeFlexibleIntIfPresent(forKey key: Key) throws -> Int? {
        guard contains(key) else { return nil }
        if try decodeNil(forKey: key) { return nil }
        if let value = try? decode(Int.self, forKey: key) { return value }
        if let value = try? decode(String.self, forKey: key) { return Int(value) }
        return nil
    }

    func decodeFlexibleInt64IfPresent(forKey key: Key) throws -> Int64? {
        guard contains(key) else { return nil }
        if try decodeNil(forKey: key) { return nil }
        if let value = try? decode(Int64.self, forKey: key) { return value }
        if let value = try? decode(String.self, forKey: key) { return Int64(value) }
        return nil
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

