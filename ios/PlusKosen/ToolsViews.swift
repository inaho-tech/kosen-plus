import SwiftUI

struct ToolsView: View {
    private let schoolTools = [
        ToolDestination(id: "camera", title: "無音カメラ", subtitle: "撮影とアプリ内ギャラリー", symbol: "camera.fill"),
        ToolDestination(id: "attendance", title: "電子出席簿", subtitle: "出席登録ページを開く", symbol: "person.badge.clock.fill"),
        ToolDestination(id: "library", title: "図書館蔵書検索", subtitle: "蔵書を検索", symbol: "books.vertical.fill")
    ]
    private let studyTools = [
        ToolDestination(id: "pomodoro", title: "ポモドーロタイマー", subtitle: "集中と休憩を管理", symbol: "timer"),
        ToolDestination(id: "stopwatch", title: "ストップウォッチ", subtitle: "ラップ対応", symbol: "stopwatch.fill"),
        ToolDestination(id: "todo", title: "ToDoリスト", subtitle: "タスクを端末に保存", symbol: "checklist"),
        ToolDestination(id: "unit", title: "単位変換", subtitle: "長さ・質量などを換算", symbol: "arrow.left.arrow.right"),
        ToolDestination(id: "kmap", title: "カルノー図", subtitle: "4変数までの論理式を整理", symbol: "square.grid.3x3.fill")
    ]
    private let gameTools = [
        ToolDestination(id: "airhockey", title: "エアホッケー", subtitle: "CPUと対戦するアクションゲーム", symbol: "circle.circle.fill"),
        ToolDestination(id: "othello", title: "オセロ", subtitle: "CPU対戦対応の定番ボードゲーム", symbol: "circle.grid.3x3.fill"),
        ToolDestination(id: "gomoku", title: "五目並べ", subtitle: "11×11盤でCPUと対戦", symbol: "grid"),
        ToolDestination(id: "reaction", title: "反射神経テスト", subtitle: "反応時間を測定して記録", symbol: "bolt.fill"),
        ToolDestination(id: "merge", title: "Number Merge", subtitle: "数字を合体させるスライドパズル", symbol: "square.grid.2x2.fill"),
        ToolDestination(id: "mines", title: "マインスイーパ", subtitle: "地雷を避けて安全なマスを開く", symbol: "burst.fill"),
        ToolDestination(id: "flappy", title: "Flappy Sky", subtitle: "障害物を避けて飛び続ける", symbol: "cloud.fill")
    ]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                AppSectionHeader(title: "学校サービス", symbol: "building.columns")
                toolGrid(schoolTools)
                AppSectionHeader(title: "学習ツール", symbol: "hammer").padding(.top, 6)
                toolGrid(studyTools)
                AppSectionHeader(title: "ゲーム", symbol: "gamecontroller").padding(.top, 6)
                toolGrid(gameTools)
            }.padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 16).appReadableContentWidth()
        }
        .appScreenBackground()
        .navigationTitle("ツール")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: ToolDestination.self) { ToolRouter(id: $0.id, title: $0.title) }
    }

    private func toolGrid(_ values: [ToolDestination]) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
            ForEach(values) { tool in
                NavigationLink(value: tool) {
                    HStack(spacing: 11) {
                        AppIconTile(symbol: tool.symbol, color: .indigo, size: 38)
                        Text(tool.title).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(2)
                        Spacer(minLength: 0)
                    }.frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                        .padding(11).appCard(cornerRadius: 14)
                }.buttonStyle(.plain)
            }
        }
    }
}

struct ToolRouter: View {
    let id: String
    let title: String
    var body: some View {
        Group {
            switch id {
            case "camera": SilentCameraView()
            case "attendance": WebClassView(startURL: URL(string: "https://std.kisarazu.ac.jp/")!)
            case "library": WebClassView(startURL: URL(string: "https://libopac-c.kosen-k.go.jp/webopac15/cattab.do")!)
            case "reaction": ReactionView()
            case "othello": OthelloGameView()
            case "gomoku": GomokuGameView()
            case "merge": NumberMergeGameView()
            case "mines": MinesweeperGameView()
            case "airhockey": AirHockeyGameView()
            case "flappy": FlappySkyGameView()
            case "pomodoro": TimerToolView(minutes: 25, title: "ポモドーロ")
            case "stopwatch": StopwatchView()
            case "todo": TodoView()
            case "unit": UnitConverterView()
            case "kmap": KarnaughView()
            default: ContentUnavailableView("利用できないツールです", systemImage: "exclamationmark.triangle")
            }
        }.navigationTitle(title).navigationBarTitleDisplayMode(.inline)
    }
}

struct StopwatchView: View {
    @State private var elapsed = 0.0
    @State private var running = false
    @State private var laps: [Double] = []
    let timer = Timer.publish(every: 0.01, on: .main, in: .common).autoconnect()
    var body: some View {
        VStack(spacing: 22) {
            Text(format(elapsed)).font(.system(size: 52, weight: .light, design: .monospaced)).padding(.top, 30)
            HStack {
                Button(running ? "ラップ" : "リセット") { if running { laps.insert(elapsed, at: 0) } else { elapsed = 0; laps = [] } }.buttonStyle(.bordered)
                Button(running ? "停止" : "開始") { running.toggle() }.buttonStyle(.borderedProminent).tint(running ? .red : .indigo)
            }
            List(Array(laps.enumerated()), id: \.offset) { i, lap in HStack { Text("ラップ \(laps.count - i)"); Spacer(); Text(format(lap)).monospacedDigit() } }
        }.onReceive(timer) { _ in if running { elapsed += 0.01 } }
    }
    private func format(_ value: Double) -> String { String(format: "%02d:%02d.%02d", Int(value)/60, Int(value)%60, Int(value*100)%100) }
}

struct TimerToolView: View {
    let minutes: Int
    let title: String
    @State private var remaining: Int
    @State private var running = false
    let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    init(minutes: Int, title: String) { self.minutes = minutes; self.title = title; _remaining = State(initialValue: minutes * 60) }
    var body: some View {
        VStack(spacing: 24) {
            Spacer(); Text(String(format: "%02d:%02d", remaining/60, remaining%60)).font(.system(size: 64, weight: .light, design: .rounded)).monospacedDigit()
            Button(running ? "一時停止" : "開始") { running.toggle() }.buttonStyle(.borderedProminent).controlSize(.large)
            Button("リセット") { running = false; remaining = minutes * 60 }.buttonStyle(.bordered)
            Spacer()
        }.onReceive(timer) { _ in if running && remaining > 0 { remaining -= 1 } }
    }
}

struct TodoView: View {
    @AppStorage("todoItems") private var encoded = ""
    @State private var text = ""
    private var items: [String] { encoded.split(separator: "\n").map(String.init) }
    var body: some View {
        VStack {
            HStack { TextField("タスクを追加", text: $text).textFieldStyle(.roundedBorder); Button("追加") { add() }.disabled(text.trimmingCharacters(in: .whitespaces).isEmpty) }.padding()
            List { ForEach(items, id: \.self) { item in Text(item) }.onDelete { set in encoded = items.enumerated().filter { !set.contains($0.offset) }.map(\.element).joined(separator: "\n") } }
        }
    }
    private func add() { var values = items; values.append(text.trimmingCharacters(in: .whitespaces)); encoded = values.joined(separator: "\n"); text = "" }
}

struct ReactionView: View {
    enum Phase { case idle, waiting, ready, result, tooEarly }
    @State private var phase = Phase.idle
    @State private var readyAt = Date.now
    @State private var token = UUID()
    @AppStorage("reactionBest") private var best = 0.0
    @AppStorage("reactionLast") private var last = 0.0
    @AppStorage("reactionTotal") private var total = 0.0
    @AppStorage("reactionAttempts") private var attempts = 0
    var body: some View {
        VStack(spacing: 16) {
            HStack(spacing: 10) {
                stat("自己ベスト", best > 0 ? "\(Int(best)) ms" : "—")
                stat("直近", last > 0 ? "\(Int(last)) ms" : "—")
                stat("平均", attempts > 0 ? "\(Int(total / Double(attempts))) ms" : "—")
            }.padding(.horizontal)
            Button { tapped() } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 28).fill(buttonColor)
                    VStack(spacing: 8) {
                        Image(systemName: phase == .ready ? "bolt.fill" : phase == .tooEarly ? "exclamationmark.triangle.fill" : "hand.tap.fill").font(.largeTitle)
                        Text(buttonText).font(.title2.bold())
                        if phase == .result { Text("もう一度タップして再挑戦").font(.caption) }
                    }.foregroundStyle(.white)
                }
            }.buttonStyle(.plain).padding().frame(maxHeight: 420)
        }.appScreenBackground()
    }
    private var buttonText: String { switch phase { case .idle: "タップして開始"; case .waiting: "色が変わるまで待つ"; case .ready: "今すぐタップ"; case .result: "\(Int(last)) ms"; case .tooEarly: "早すぎます" } }
    private var buttonColor: Color { switch phase { case .ready: .green; case .waiting: .orange; case .tooEarly: .red; default: .indigo } }
    private func stat(_ title: String, _ value: String) -> some View { VStack(spacing: 4) { Text(value).font(.headline.monospacedDigit()); Text(title).font(.caption2).foregroundStyle(.secondary) }.frame(maxWidth: .infinity).padding(.vertical, 12).appCard(cornerRadius: 14) }
    private func tapped() {
        switch phase {
        case .idle, .result, .tooEarly:
            phase = .waiting
            let current = UUID(); token = current
            Task { try? await Task.sleep(for: .seconds(Double.random(in: 1.5...4))); if phase == .waiting && token == current { readyAt = .now; phase = .ready } }
        case .waiting:
            token = UUID(); phase = .tooEarly
        case .ready:
            let ms = Date.now.timeIntervalSince(readyAt) * 1000
            last = ms; total += ms; attempts += 1
            if best == 0 || ms < best { best = ms }
            phase = .result
        }
    }
}

struct UnitConverterView: View {
    private struct UnitOption: Identifiable { let id: String; let name: String; let toBase: (Double) -> Double; let fromBase: (Double) -> Double }
    private struct Category: Identifiable { let id: String; let name: String; let units: [UnitOption] }
    private let categories: [Category] = [
        .init(id: "length", name: "長さ", units: [
            .init(id:"mm",name:"ミリメートル",toBase:{$0/1000},fromBase:{$0*1000}), .init(id:"cm",name:"センチメートル",toBase:{$0/100},fromBase:{$0*100}), .init(id:"m",name:"メートル",toBase:{$0},fromBase:{$0}), .init(id:"km",name:"キロメートル",toBase:{$0*1000},fromBase:{$0/1000})]),
        .init(id: "mass", name: "質量", units: [
            .init(id:"mg",name:"ミリグラム",toBase:{$0/1000},fromBase:{$0*1000}), .init(id:"g",name:"グラム",toBase:{$0},fromBase:{$0}), .init(id:"kg",name:"キログラム",toBase:{$0*1000},fromBase:{$0/1000}), .init(id:"t",name:"トン",toBase:{$0*1_000_000},fromBase:{$0/1_000_000})]),
        .init(id: "temperature", name: "温度", units: [
            .init(id:"c",name:"摂氏 ℃",toBase:{$0},fromBase:{$0}), .init(id:"f",name:"華氏 ℉",toBase:{($0-32)*5/9},fromBase:{$0*9/5+32}), .init(id:"k",name:"ケルビン K",toBase:{$0-273.15},fromBase:{$0+273.15})]),
        .init(id: "speed", name: "速度", units: [
            .init(id:"ms",name:"m/s",toBase:{$0},fromBase:{$0}), .init(id:"kmh",name:"km/h",toBase:{$0/3.6},fromBase:{$0*3.6}), .init(id:"mph",name:"mph",toBase:{$0*0.44704},fromBase:{$0/0.44704})])
    ]
    @State private var value = 1.0
    @State private var categoryID = "length"
    @State private var fromID = "m"
    @State private var toID = "km"
    private var category: Category { categories.first(where: { $0.id == categoryID }) ?? categories[0] }
    private var result: Double { let from=category.units.first(where:{$0.id==fromID}) ?? category.units[0], to=category.units.first(where:{$0.id==toID}) ?? category.units[0]; return to.fromBase(from.toBase(value)) }
    var body: some View {
        Form {
            Section("値") { TextField("値", value: $value, format: .number).keyboardType(.decimalPad) }
            Section("種類") { Picker("カテゴリ", selection: $categoryID) { ForEach(categories) { Text($0.name).tag($0.id) } }.onChange(of: categoryID) { let units=category.units; fromID=units[0].id; toID=units[min(1,units.count-1)].id } }
            Section("単位") {
                Picker("変換元", selection: $fromID) { ForEach(category.units) { Text($0.name).tag($0.id) } }
                Picker("変換先", selection: $toID) { ForEach(category.units) { Text($0.name).tag($0.id) } }
            }
            Section("変換結果") { Text(result.formatted(.number.precision(.fractionLength(0...8)))).font(.title2.monospacedDigit()).textSelection(.enabled) }
        }
    }
}

struct KarnaughView: View {
    @State private var cells = Array(repeating: false, count: 16)
    private let gray = [0, 1, 3, 2]
    var body: some View {
        VStack(spacing: 18) {
            Text("ABを行、CDを列とし、グレイコード順に配置します").font(.subheadline).foregroundStyle(.secondary)
            Grid(horizontalSpacing: 4, verticalSpacing: 4) {
                GridRow { Text("AB＼CD"); ForEach(gray, id: \.self) { Text(String($0, radix: 2).leftPadded(to: 2)) } }
                ForEach(Array(gray.enumerated()), id: \.offset) { r, rv in
                    GridRow {
                        Text(String(rv, radix: 2).leftPadded(to: 2))
                        ForEach(Array(gray.enumerated()), id: \.offset) { c, cv in
                            let index = rv * 4 + cv
                            Button(cells[index] ? "1" : "0") { cells[index].toggle() }
                                .buttonStyle(.borderedProminent).tint(cells[index] ? .indigo : .gray)
                        }
                    }
                }
            }
            Text("Σm(\(cells.indices.filter { cells[$0] }.map(String.init).joined(separator: ", ")))").font(.headline.monospaced())
            Spacer()
        }.padding()
    }
}

private extension String { func leftPadded(to count: Int) -> String { String(repeating: "0", count: Swift.max(0, count-self.count)) + self } }

