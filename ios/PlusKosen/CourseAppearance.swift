import SwiftUI

struct CourseAppearance {
    let symbol: String
    let color: Color

    static func course(_ title: String) -> CourseAppearance {
        let value = title.lowercased()
        switch true {
        case contains(value, ["国語", "日本", "中国", "英語", "ドイツ", "英会話", "国文学"]):
            return .init(symbol: "character.book.closed.fill", color: .blue)
        case contains(value, ["数学", "線形代数", "微分", "積分"]):
            return .init(symbol: "function", color: .purple)
        case value.contains("統計"):
            return .init(symbol: "chart.bar.xaxis", color: .cyan)
        case contains(value, ["保険", "保健"]):
            return .init(symbol: "cross.case.fill", color: .red)
        case value.contains("体育"):
            return .init(symbol: "figure.run", color: .green)
        case contains(value, ["地理", "社会", "経済学", "法学"]):
            return .init(symbol: "globe.asia.australia.fill", color: .teal)
        case value.contains("歴史"):
            return .init(symbol: "building.columns.fill", color: .brown)
        case contains(value, ["物理", "化学", "科学", "サイエンス"]):
            return .init(symbol: "atom", color: .mint)
        case contains(value, ["哲学", "心理学"]):
            return .init(symbol: "brain.head.profile.fill", color: .pink)
        case contains(value, ["課題研究", "研究"]):
            return .init(symbol: "lightbulb.max.fill", color: .yellow)
        case contains(value, ["技術", "実習"]):
            return .init(symbol: "wrench.and.screwdriver.fill", color: .orange)
        case value.contains("機械"):
            return .init(symbol: "gearshape.2.fill", color: .gray)
        case value.contains("設計"):
            return .init(symbol: "ruler.fill", color: .indigo)
        case contains(value, ["情報", "計算機", "コンピュータ", "プログラミング"]):
            return .init(symbol: "laptopcomputer", color: .indigo)
        case contains(value, ["電子工学", "システム"]):
            return .init(symbol: "memorychip.fill", color: .blue)
        case value.contains("力学"):
            return .init(symbol: "hammer.fill", color: .orange)
        case value.contains("ロボット"):
            return .init(symbol: "cpu.fill", color: .cyan)
        case value.contains("電磁気"):
            return .init(symbol: "bolt.fill", color: .yellow)
        case contains(value, ["電気回路", "電子回路"]):
            return .init(symbol: "waveform.path.ecg", color: .orange)
        case contains(value, ["計測工学", "製図"]):
            return .init(symbol: "compass.drawing", color: .brown)
        case value.contains("制御"):
            return .init(symbol: "slider.horizontal.3", color: .teal)
        case value.contains("セミナー"):
            return .init(symbol: "person.3.fill", color: .green)
        case contains(value, ["芸術", "美術", "音楽"]):
            return .init(symbol: "paintpalette.fill", color: .pink)
        case value.contains("hr") || value.contains("ホームルーム"):
            return .init(symbol: "person.2.fill", color: .blue)
        default:
            return .init(symbol: "graduationcap.fill", color: .indigo)
        }
    }

    static func assignment(_ category: String) -> CourseAppearance {
        let value = category.lowercased()
        if contains(value, ["テスト", "試験"]) { return .init(symbol: "checkmark.seal.fill", color: .red) }
        if value.contains("アンケート") { return .init(symbol: "chart.bar.doc.horizontal.fill", color: .cyan) }
        if contains(value, ["レポート", "課題"]) { return .init(symbol: "doc.text.fill", color: .orange) }
        if contains(value, ["教材", "資料"]) { return .init(symbol: "book.pages.fill", color: .blue) }
        return .init(symbol: "doc.fill", color: .indigo)
    }

    private static func contains(_ value: String, _ needles: [String]) -> Bool {
        needles.contains(where: value.contains)
    }
}
