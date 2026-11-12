import SwiftUI

struct ToolsView: View {
    private let tools = [
        ToolDestination(id: "camera", title: "無音カメラ", subtitle: "撮影とアプリ内ギャラリー", symbol: "camera.fill"),
        ToolDestination(id: "attendance", title: "電子出席簿", subtitle: "出席登録ページを開く", symbol: "person.badge.clock.fill"),
        ToolDestination(id: "library", title: "図書館蔵書検索", subtitle: "蔵書を検索", symbol: "books.vertical.fill"),
        ToolDestination(id: "reaction", title: "反射神経テスト", subtitle: "反応時間を測定して記録", symbol: "bolt.fill"),
        ToolDestination(id: "pomodoro", title: "ポモドーロタイマー", subtitle: "集中と休憩を管理", symbol: "timer"),
        ToolDestination(id: "stopwatch", title: "ストップウォッチ", subtitle: "ラップ対応", symbol: "stopwatch.fill"),
        ToolDestination(id: "todo", title: "ToDoリスト", subtitle: "タスクを端末に保存", symbol: "checklist"),
        ToolDestination(id: "unit", title: "単位変換", subtitle: "長さ・質量などを換算", symbol: "arrow.left.arrow.right"),
        ToolDestination(id: "kmap", title: "カルノー図", subtitle: "4変数までの論理式を整理", symbol: "square.grid.3x3.fill"),
        ToolDestination(id: "othello", title: "オセロ", subtitle: "ボードゲーム", symbol: "circle.lefthalf.filled"),
        ToolDestination(id: "gomoku", title: "五目並べ", subtitle: "ボードゲーム", symbol: "circle.grid.cross.fill"),
        ToolDestination(id: "merge", title: "Number Merge", subtitle: "数字パズル", symbol: "square.grid.2x2.fill"),
        ToolDestination(id: "hockey", title: "エアホッケー", subtitle: "対戦ゲーム", symbol: "circle.circle.fill"),
        ToolDestination(id: "mine", title: "マインスイーパー", subtitle: "地雷を避けるパズル", symbol: "burst.fill"),
        ToolDestination(id: "flappy", title: "Flappy Sky", subtitle: "アクションゲーム", symbol: "bird.fill")
    ]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 14) {
                    AppIconTile(symbol: "hammer.fill", color: .indigo, size: 54)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("ツール").font(.title2.bold())
                        Text("学習・撮影・計算・ゲーム").font(.subheadline).foregroundStyle(.secondary)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading).padding(15).appCard()

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 155), spacing: 12)], spacing: 12) {
                ForEach(tools) { tool in
                    NavigationLink(value: tool) {
                        VStack(alignment: .leading, spacing: 10) {
                            AppIconTile(symbol: tool.symbol, color: toolColor(tool.id), size: 44)
                            Text(tool.title).font(.headline).foregroundStyle(.primary)
                            Text(tool.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }.frame(maxWidth: .infinity, minHeight: 122, alignment: .topLeading)
                            .padding(14).appCard()
                    }
                }
                }
            }.padding()
        }
        .appScreenBackground()
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: ToolDestination.self) { ToolRouter(id: $0.id, title: $0.title) }
    }

    private func toolColor(_ id: String) -> Color {
        switch id {
        case "camera": .blue
        case "attendance": .green
        case "library": .brown
        case "reaction": .orange
        case "kmap", "unit": .purple
        case "pomodoro", "stopwatch": .red
        case "todo": .teal
        default: .indigo
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
            case "attendance": WebClassView(startURL: URL(string: "https://webclass.kosen-k.go.jp/webclass/")!)
            case "library": WebClassView(startURL: URL(string: "https://libopac3-c.nagaokaut.ac.jp/opac/opac_search/")!)
            case "reaction": ReactionView()
            case "pomodoro": TimerToolView(minutes: 25, title: "ポモドーロ")
            case "stopwatch": StopwatchView()
            case "todo": TodoView()
            case "unit": UnitConverterView()
            case "kmap": KarnaughView()
            default: GamePlaceholderView(title: title)
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
    enum Phase { case ready, waiting, tap }
    @State private var phase = Phase.ready
    @State private var start = Date.now
    @AppStorage("reactionBest") private var best = 0.0
    var body: some View {
        VStack(spacing: 20) {
            if best > 0 { Text("ベスト \(Int(best)) ms").font(.headline) }
            Button { tapped() } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 28).fill(phase == .tap ? Color.green : phase == .waiting ? Color.orange : Color.indigo)
                    Text(phase == .ready ? "タップして開始" : phase == .waiting ? "緑になるまで待つ" : "今すぐタップ").font(.title2.bold()).foregroundStyle(.white)
                }
            }.buttonStyle(.plain).padding().frame(maxHeight: 420)
        }
    }
    private func tapped() {
        switch phase {
        case .ready:
            phase = .waiting
            Task { try? await Task.sleep(for: .seconds(Double.random(in: 1.5...4))); if phase == .waiting { start = .now; phase = .tap } }
        case .waiting: phase = .ready
        case .tap:
            let ms = Date.now.timeIntervalSince(start) * 1000
            if best == 0 || ms < best { best = ms }
            phase = .ready
        }
    }
}

struct UnitConverterView: View {
    @State private var value = 1.0
    @State private var from = 1.0
    @State private var to = 1000.0
    var body: some View {
        Form {
            Section("値") { TextField("値", value: $value, format: .number).keyboardType(.decimalPad) }
            Section("長さ") {
                Picker("変換元", selection: $from) { Text("m").tag(1.0); Text("km").tag(1000.0); Text("cm").tag(0.01); Text("mm").tag(0.001) }
                Picker("変換先", selection: $to) { Text("m").tag(1.0); Text("km").tag(1000.0); Text("cm").tag(0.01); Text("mm").tag(0.001) }
            }
            Section("結果") { Text((value * from / to).formatted()).font(.title2.monospacedDigit()) }
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

struct GamePlaceholderView: View {
    let title: String
    var body: some View { ContentUnavailableView("\(title)を移植中", systemImage: "gamecontroller.fill", description: Text("ゲームエンジン部分をSwiftUIへ置き換えています")) }
}

