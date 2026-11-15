import SwiftUI

private enum VersusMode: String, CaseIterable, Identifiable {
    case cpu = "CPU対戦"
    case human = "2人プレイ"
    var id: Self { self }
}

private enum GameLevel: Int, CaseIterable, Identifiable {
    case easy, medium, hard
    var id: Self { self }
    var title: String { switch self { case .easy: "初級"; case .medium: "中級"; case .hard: "上級" } }
}

private struct BoardGameConfiguration: View {
    @Binding var mode: VersusMode
    @Binding var level: GameLevel
    @Binding var playerColor: Int
    let reset: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Picker("対戦モード", selection: $mode) {
                ForEach(VersusMode.allCases) { Text($0.rawValue).tag($0) }
            }.pickerStyle(.segmented)
            if mode == .cpu {
                HStack(spacing: 10) {
                    Picker("難易度", selection: $level) {
                        ForEach(GameLevel.allCases) { Text($0.title).tag($0) }
                    }.pickerStyle(.segmented)
                    Picker("石色", selection: $playerColor) {
                        Text("先手").tag(1)
                        Text("後手").tag(-1)
                    }.pickerStyle(.menu).fixedSize()
                }
            }
        }
        .padding(12)
        .appCard(cornerRadius: 15)
        .onChange(of: mode) { reset() }
        .onChange(of: level) { reset() }
        .onChange(of: playerColor) { reset() }
    }
}

struct OthelloGameView: View {
    @State private var board = Self.initialBoard
    @State private var mode = VersusMode.cpu
    @State private var level = GameLevel.medium
    @State private var playerColor = 1
    @State private var currentTurn = 1
    @State private var thinking = false
    @State private var gameOver = false
    @State private var message = "黒の番です"
    @State private var lastMove: Int?

    private static let weights = [
        120,-25,20,5,5,20,-25,120, -25,-45,-5,-5,-5,-5,-45,-25,
        20,-5,15,3,3,15,-5,20, 5,-5,3,3,3,3,-5,5,
        5,-5,3,3,3,3,-5,5, 20,-5,15,3,3,15,-5,20,
        -25,-45,-5,-5,-5,-5,-45,-25, 120,-25,20,5,5,20,-25,120
    ]
    private static var initialBoard: [Int] {
        var value = Array(repeating: 0, count: 64)
        value[27] = -1; value[28] = 1; value[35] = 1; value[36] = -1
        return value
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 13) {
                BoardGameConfiguration(mode: $mode, level: $level, playerColor: $playerColor, reset: reset)
                GameScoreBar(left: "黒 \(count(1))", center: message, right: "白 \(count(-1))")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 8), spacing: 2) {
                    ForEach(0..<64, id: \.self) { index in
                        ZStack {
                            Rectangle().fill(Color.green.opacity(0.82)).aspectRatio(1, contentMode: .fit)
                            if board[index] != 0 {
                                Circle().fill(board[index] == 1 ? Color.black : Color.white)
                                    .padding(4).shadow(radius: 1)
                                    .overlay { if lastMove == index { Circle().fill(.red).frame(width: 5, height: 5) } }
                                    .transition(.scale.combined(with: .opacity))
                            } else if canHumanPlay && !flips(in: board, at: index, player: currentTurn).isEmpty {
                                Circle().fill(.black.opacity(0.13)).padding(12)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { play(index) }
                    }
                }
                .padding(5).background(.green.opacity(0.42), in: RoundedRectangle(cornerRadius: 15))
                .frame(maxWidth: 540)
                Button("最初から", action: reset).buttonStyle(.bordered)
            }.padding(16).appReadableContentWidth(680)
        }.appScreenBackground().onAppear { if mode == .cpu && playerColor == -1 { scheduleCPU() } }
    }

    private var canHumanPlay: Bool { !thinking && !gameOver && (mode == .human || currentTurn == playerColor) }
    private func count(_ color: Int) -> Int { board.filter { $0 == color }.count }
    private func name(_ color: Int) -> String { color == 1 ? "黒" : "白" }

    private func reset() {
        board = Self.initialBoard; currentTurn = 1; thinking = false; gameOver = false; lastMove = nil
        message = "黒の番です"
        if mode == .cpu && playerColor == -1 { scheduleCPU() }
    }

    private func flips(in values: [Int], at index: Int, player: Int) -> [Int] {
        guard values[index] == 0 else { return [] }
        let row = index / 8, column = index % 8
        var result: [Int] = []
        for (dr, dc) in [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)] {
            var r = row + dr, c = column + dc, line: [Int] = []
            while (0..<8).contains(r), (0..<8).contains(c), values[r * 8 + c] == -player {
                line.append(r * 8 + c); r += dr; c += dc
            }
            if !line.isEmpty, (0..<8).contains(r), (0..<8).contains(c), values[r * 8 + c] == player { result += line }
        }
        return result
    }

    private func moves(in values: [Int], player: Int) -> [Int] {
        values.indices.filter { !flips(in: values, at: $0, player: player).isEmpty }
    }

    private func placed(_ values: [Int], at index: Int, player: Int) -> [Int] {
        let changed = flips(in: values, at: index, player: player)
        guard !changed.isEmpty else { return values }
        var next = values; next[index] = player; changed.forEach { next[$0] = player }; return next
    }

    private func play(_ index: Int) {
        guard canHumanPlay, !flips(in: board, at: index, player: currentTurn).isEmpty else { return }
        withAnimation(.easeInOut(duration: 0.22)) { board = placed(board, at: index, player: currentTurn); lastMove = index }
        finishTurn()
    }

    private func finishTurn() {
        let next = -currentTurn
        if !moves(in: board, player: next).isEmpty { currentTurn = next }
        else if moves(in: board, player: currentTurn).isEmpty { finishGame(); return }
        else { message = "\(name(next))はパス" }
        message = "\(name(currentTurn))の番です"
        if mode == .cpu && currentTurn != playerColor { scheduleCPU() }
    }

    private func scheduleCPU() {
        thinking = true; message = "CPUが考えています…"
        Task {
            try? await Task.sleep(for: .milliseconds(level == .hard ? 520 : 300))
            await MainActor.run {
                guard !gameOver, currentTurn != playerColor else { thinking = false; return }
                if let move = cpuMove() {
                    withAnimation(.easeInOut(duration: 0.22)) { board = placed(board, at: move, player: currentTurn); lastMove = move }
                }
                thinking = false; finishTurn()
            }
        }
    }

    private func cpuMove() -> Int? {
        let options = moves(in: board, player: currentTurn)
        guard level != .easy else { return options.randomElement() }
        return options.max { lhs, rhs in cpuScore(lhs) < cpuScore(rhs) }
    }

    private func cpuScore(_ move: Int) -> Int {
        let immediate = flips(in: board, at: move, player: currentTurn).count * 7 + Self.weights[move]
        guard level == .hard else { return immediate }
        let next = placed(board, at: move, player: currentTurn)
        let replyPenalty = moves(in: next, player: -currentTurn).map { Self.weights[$0] + flips(in: next, at: $0, player: -currentTurn).count * 5 }.max() ?? 0
        return immediate * 2 - replyPenalty
    }

    private func finishGame() {
        gameOver = true; thinking = false
        let black = count(1), white = count(-1)
        message = black == white ? "引き分け" : black > white ? "黒の勝ち" : "白の勝ち"
    }
}

struct GomokuGameView: View {
    @State private var board = Array(repeating: 0, count: 121)
    @State private var mode = VersusMode.cpu
    @State private var level = GameLevel.medium
    @State private var playerColor = 1
    @State private var currentTurn = 1
    @State private var thinking = false
    @State private var gameOver = false
    @State private var message = "黒の番です"
    @State private var lastMove: Int?
    @State private var winning = Set<Int>()

    var body: some View {
        ScrollView {
            VStack(spacing: 13) {
                BoardGameConfiguration(mode: $mode, level: $level, playerColor: $playerColor, reset: reset)
                GameScoreBar(left: "黒 \(board.filter { $0 == 1 }.count)", center: message, right: "白 \(board.filter { $0 == -1 }.count)")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 1), count: 11), spacing: 1) {
                    ForEach(0..<121, id: \.self) { index in
                        ZStack {
                            Rectangle().fill(Color.brown.opacity(0.32)).aspectRatio(1, contentMode: .fit)
                            if board[index] != 0 {
                                Circle().fill(board[index] == 1 ? Color.black : Color.white).padding(2)
                                    .overlay { if lastMove == index && !winning.contains(index) { Circle().fill(.red).frame(width: 4, height: 4) } }
                                    .overlay { if winning.contains(index) { Circle().stroke(.red, lineWidth: 2) } }
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }.contentShape(Rectangle()).onTapGesture { play(index) }
                    }
                }.padding(5).background(.brown.opacity(0.17), in: RoundedRectangle(cornerRadius: 14)).frame(maxWidth: 580)
                Button("最初から", action: reset).buttonStyle(.bordered)
            }.padding(16).appReadableContentWidth(700)
        }.appScreenBackground().onAppear { if mode == .cpu && playerColor == -1 { scheduleCPU() } }
    }

    private func reset() {
        board = Array(repeating: 0, count: 121); currentTurn = 1; thinking = false; gameOver = false
        lastMove = nil; winning = []; message = "黒の番です"
        if mode == .cpu && playerColor == -1 { scheduleCPU() }
    }

    private func play(_ index: Int) {
        guard !gameOver, !thinking, board[index] == 0, mode == .human || currentTurn == playerColor else { return }
        place(index)
    }

    private func place(_ index: Int) {
        withAnimation(.spring(response: 0.25, dampingFraction: 0.72)) { board[index] = currentTurn; lastMove = index }
        let line = winningLine(at: index, player: currentTurn)
        if !line.isEmpty { winning = Set(line); gameOver = true; message = "\(currentTurn == 1 ? "黒" : "白")の勝ち"; return }
        if !board.contains(0) { gameOver = true; message = "引き分け"; return }
        currentTurn *= -1; message = "\(currentTurn == 1 ? "黒" : "白")の番です"
        if mode == .cpu && currentTurn != playerColor { scheduleCPU() }
    }

    private func scheduleCPU() {
        thinking = true; message = "CPUが考えています…"
        Task {
            try? await Task.sleep(for: .milliseconds(level == .hard ? 450 : 260))
            await MainActor.run {
                guard !gameOver, let move = cpuMove() else { thinking = false; return }
                thinking = false; place(move)
            }
        }
    }

    private func cpuMove() -> Int? {
        let empty = board.indices.filter { board[$0] == 0 }
        if board.allSatisfy({ $0 == 0 }) { return 60 }
        if let win = empty.first(where: { wouldWin($0, currentTurn) }) { return win }
        if let block = empty.first(where: { wouldWin($0, -currentTurn) }) { return block }
        let nearby = empty.filter { index in
            let r = index / 11, c = index % 11
            return board.indices.contains { board[$0] != 0 && abs($0 / 11 - r) <= 2 && abs($0 % 11 - c) <= 2 }
        }
        let candidates = nearby.isEmpty ? empty : nearby
        if level == .easy { return candidates.randomElement() }
        return candidates.max { heuristic($0) < heuristic($1) }
    }

    private func heuristic(_ index: Int) -> Int {
        var total = 0
        for (dr, dc) in [(1,0),(0,1),(1,1),(1,-1)] {
            total += linePotential(index, currentTurn, dr, dc) * (level == .hard ? 3 : 2)
            total += linePotential(index, -currentTurn, dr, dc) * 2
        }
        let center = 10 - (abs(index / 11 - 5) + abs(index % 11 - 5))
        return total + center
    }

    private func linePotential(_ index: Int, _ player: Int, _ dr: Int, _ dc: Int) -> Int {
        let row = index / 11, col = index % 11
        var count = 1
        for sign in [-1, 1] {
            var r = row + dr * sign, c = col + dc * sign
            while (0..<11).contains(r), (0..<11).contains(c), board[r * 11 + c] == player { count += 1; r += dr * sign; c += dc * sign }
        }
        return count * count
    }

    private func wouldWin(_ index: Int, _ player: Int) -> Bool {
        var copy = board; copy[index] = player
        return winningLine(in: copy, at: index, player: player).count >= 5
    }

    private func winningLine(at index: Int, player: Int) -> [Int] { winningLine(in: board, at: index, player: player) }
    private func winningLine(in values: [Int], at index: Int, player: Int) -> [Int] {
        let row = index / 11, col = index % 11
        for (dr, dc) in [(1,0),(0,1),(1,1),(1,-1)] {
            var line = [index]
            for sign in [-1, 1] {
                var r = row + dr * sign, c = col + dc * sign
                while (0..<11).contains(r), (0..<11).contains(c), values[r * 11 + c] == player { line.append(r * 11 + c); r += dr * sign; c += dc * sign }
            }
            if line.count >= 5 { return line }
        }
        return []
    }
}

struct NumberMergeGameView: View {
    @State private var cells = Self.newBoard()
    @State private var score = 0
    @State private var loaded = false
    @State private var gameOver = false
    @AppStorage("numberMergeBoard") private var savedBoard = ""
    @AppStorage("numberMergeScore") private var savedScore = 0
    @AppStorage("numberMergeHighScore") private var highScore = 0

    var body: some View {
        VStack(spacing: 16) {
            GameScoreBar(left: "スコア \(score)", center: gameOver ? "ゲームオーバー" : "スワイプで移動", right: "ベスト \(highScore)")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 7), count: 4), spacing: 7) {
                ForEach(0..<16, id: \.self) { index in
                    Text(cells[index] == 0 ? "" : "\(cells[index])")
                        .font(.title2.bold().monospacedDigit()).foregroundStyle(cells[index] >= 8 ? .white : .primary)
                        .frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit)
                        .background(tileColor(cells[index]), in: RoundedRectangle(cornerRadius: 12))
                        .contentTransition(.numericText())
                }
            }.padding(9).background(.brown.opacity(0.14), in: RoundedRectangle(cornerRadius: 17)).frame(maxWidth: 520)
                .gesture(DragGesture(minimumDistance: 20).onEnded { value in move(dx: value.translation.width, dy: value.translation.height) })
            Button("最初から", action: newGame).buttonStyle(.bordered)
        }.padding().appScreenBackground()
            .onAppear(perform: restore)
            .onChange(of: cells) { save() }
            .onChange(of: score) { save() }
    }

    private static func newBoard() -> [Int] {
        var values = Array(repeating: 0, count: 16)
        for _ in 0..<2 { if let index = values.indices.filter({ values[$0] == 0 }).randomElement() { values[index] = 2 } }
        return values
    }
    private func restore() {
        guard !loaded else { return }; loaded = true
        let values = savedBoard.split(separator: ",").compactMap { Int($0) }
        if values.count == 16 { cells = values; score = savedScore; gameOver = !hasMoves(values) }
    }
    private func save() {
        guard loaded else { return }
        savedBoard = cells.map(String.init).joined(separator: ","); savedScore = score; highScore = max(highScore, score)
    }
    private func newGame() { cells = Self.newBoard(); score = 0; gameOver = false; save() }
    private func move(dx: CGFloat, dy: CGFloat) {
        guard !gameOver else { return }
        let horizontal = abs(dx) > abs(dy), forward = horizontal ? dx < 0 : dy < 0
        var next = cells, gained = 0
        for line in 0..<4 {
            var indexes = (0..<4).map { horizontal ? line * 4 + $0 : $0 * 4 + line }
            if !forward { indexes.reverse() }
            let values = indexes.map { cells[$0] }.filter { $0 != 0 }
            var merged: [Int] = [], i = 0
            while i < values.count {
                if i + 1 < values.count, values[i] == values[i + 1] { let value = values[i] * 2; merged.append(value); gained += value; i += 2 }
                else { merged.append(values[i]); i += 1 }
            }
            merged += Array(repeating: 0, count: 4 - merged.count)
            for (position, index) in indexes.enumerated() { next[index] = merged[position] }
        }
        guard next != cells else { return }
        if let index = next.indices.filter({ next[$0] == 0 }).randomElement() { next[index] = Int.random(in: 0..<10) == 0 ? 4 : 2 }
        withAnimation(.easeInOut(duration: 0.15)) { cells = next; score += gained }
        gameOver = !hasMoves(next); save()
    }
    private func hasMoves(_ values: [Int]) -> Bool {
        if values.contains(0) { return true }
        for r in 0..<4 { for c in 0..<4 { let i = r * 4 + c; if c < 3 && values[i] == values[i + 1] { return true }; if r < 3 && values[i] == values[i + 4] { return true } } }
        return false
    }
    private func tileColor(_ value: Int) -> Color {
        if value == 0 { return .secondary.opacity(0.1) }
        let colors: [Color] = [.orange.opacity(0.18),.orange.opacity(0.32),.orange,.pink,.purple,.indigo,.blue,.teal]
        return colors[min(max(Int(log2(Double(value))) - 1, 0), colors.count - 1)]
    }
}

private enum MineDifficulty: Int, CaseIterable, Identifiable {
    case easy, medium, hard
    var id: Self { self }
    var rows: Int { switch self { case .easy: 9; case .medium: 12; case .hard: 15 } }
    var columns: Int { switch self { case .easy: 9; case .medium: 10; case .hard: 12 } }
    var mines: Int { switch self { case .easy: 10; case .medium: 20; case .hard: 35 } }
    var title: String { switch self { case .easy: "初級"; case .medium: "中級"; case .hard: "上級" } }
}

private struct MinesBoard {
    let rows: Int, columns: Int, mineCount: Int
    var mines = Set<Int>(), opened = Set<Int>(), flags = Set<Int>()
    var generated = false
    init(difficulty: MineDifficulty) { rows = difficulty.rows; columns = difficulty.columns; mineCount = difficulty.mines }
    mutating func generate(safe index: Int) {
        let excluded = Set([index] + nearby(index))
        mines = Set((0..<(rows * columns)).filter { !excluded.contains($0) }.shuffled().prefix(mineCount)); generated = true
    }
    func nearby(_ index: Int) -> [Int] {
        let r = index / columns, c = index % columns
        return (-1...1).flatMap { dr in (-1...1).compactMap { dc in
            let nr = r + dr, nc = c + dc
            return (dr != 0 || dc != 0) && (0..<rows).contains(nr) && (0..<columns).contains(nc) ? nr * columns + nc : nil
        } }
    }
}

struct MinesweeperGameView: View {
    @State private var difficulty = MineDifficulty.easy
    @State private var game = MinesBoard(difficulty: .easy)
    @State private var message = "最初のマスは必ず安全です"
    @State private var finished = false
    @State private var flagMode = false
    @State private var seconds = 0
    @AppStorage("minesBestEasy") private var bestEasy = 0
    @AppStorage("minesBestMedium") private var bestMedium = 0
    @AppStorage("minesBestHard") private var bestHard = 0
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 13) {
                Picker("難易度", selection: $difficulty) { ForEach(MineDifficulty.allCases) { Text($0.title).tag($0) } }.pickerStyle(.segmented)
                    .onChange(of: difficulty) { reset() }
                GameScoreBar(left: "地雷 \(max(0, difficulty.mines - game.flags.count))", center: message, right: String(format: "%03d秒", seconds))
                if currentBest > 0 { Text("ベスト \(currentBest)秒").font(.caption).foregroundStyle(.secondary) }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: difficulty.columns), spacing: 2) {
                    ForEach(0..<(difficulty.rows * difficulty.columns), id: \.self) { index in
                        Button { flagMode ? toggleFlag(index) : open(index) } label: {
                            ZStack {
                                RoundedRectangle(cornerRadius: 3).fill(game.opened.contains(index) ? Color.secondary.opacity(0.09) : Color.indigo.opacity(0.24))
                                if game.opened.contains(index) {
                                    if game.mines.contains(index) { Image(systemName: "burst.fill").foregroundStyle(.red) }
                                    else { let count = game.nearby(index).filter(game.mines.contains).count; if count > 0 { Text("\(count)").font(.caption2.bold()) } }
                                } else if game.flags.contains(index) { Image(systemName: "flag.fill").font(.caption).foregroundStyle(.orange) }
                            }.aspectRatio(1, contentMode: .fit)
                        }.buttonStyle(.plain).contextMenu { Button(game.flags.contains(index) ? "旗を外す" : "旗を立てる", systemImage: "flag") { toggleFlag(index) } }
                    }
                }.frame(maxWidth: 600)
                Picker("操作", selection: $flagMode) { Label("開く", systemImage: "hand.tap").tag(false); Label("旗", systemImage: "flag.fill").tag(true) }.pickerStyle(.segmented).frame(maxWidth: 320)
                Button("新しいゲーム", action: reset).buttonStyle(.bordered)
            }.padding(16).appReadableContentWidth(700)
        }.appScreenBackground().onReceive(timer) { _ in if game.generated && !finished { seconds = min(seconds + 1, 9999) } }
    }

    private var currentBest: Int { switch difficulty { case .easy: bestEasy; case .medium: bestMedium; case .hard: bestHard } }
    private func reset() { game = MinesBoard(difficulty: difficulty); message = "最初のマスは必ず安全です"; finished = false; seconds = 0; flagMode = false }
    private func toggleFlag(_ index: Int) { guard !finished, !game.opened.contains(index) else { return }; if game.flags.contains(index) { game.flags.remove(index) } else if game.flags.count < difficulty.mines { game.flags.insert(index) } }
    private func open(_ index: Int) {
        guard !finished, !game.flags.contains(index) else { return }
        if !game.generated { game.generate(safe: index); message = "プレイ中" }
        var queue = [index]
        while let value = queue.popLast() {
            guard !game.opened.contains(value) else { continue }
            game.opened.insert(value)
            if game.mines.contains(value) { message = "ゲームオーバー"; finished = true; game.opened.formUnion(game.mines); return }
            if game.nearby(value).filter(game.mines.contains).isEmpty { queue += game.nearby(value).filter { !game.opened.contains($0) } }
        }
        if game.opened.count == difficulty.rows * difficulty.columns - difficulty.mines { message = "クリア！"; finished = true; saveBest() }
    }
    private func saveBest() {
        switch difficulty {
        case .easy: if bestEasy == 0 || seconds < bestEasy { bestEasy = seconds }
        case .medium: if bestMedium == 0 || seconds < bestMedium { bestMedium = seconds }
        case .hard: if bestHard == 0 || seconds < bestHard { bestHard = seconds }
        }
    }
}

struct AirHockeyGameView: View {
    @State private var mode = VersusMode.cpu
    @State private var level = GameLevel.medium
    @State private var playerX: CGFloat = 160
    @State private var opponentX: CGFloat = 160
    @State private var puck = CGPoint(x: 160, y: 240)
    @State private var velocity = CGVector(dx: 2.8, dy: 3.6)
    @State private var scores = [0, 0]
    @State private var winner: String?
    private let timer = Timer.publish(every: 1 / 60, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Picker("対戦モード", selection: $mode) { ForEach(VersusMode.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented).onChange(of: mode) { reset() }
                if mode == .cpu { Picker("難易度", selection: $level) { ForEach(GameLevel.allCases) { Text($0.title).tag($0) } }.pickerStyle(.segmented).onChange(of: level) { reset() } }
                GameScoreBar(left: "\(mode == .cpu ? "CPU" : "P2") \(scores[0])", center: winner ?? "7点先取", right: "P1 \(scores[1])")
                ZStack {
                    RoundedRectangle(cornerRadius: 24).fill(.cyan.opacity(0.11))
                    Rectangle().fill(.white.opacity(0.55)).frame(height: 2)
                    Capsule().stroke(.secondary, lineWidth: 2).frame(width: 100, height: 12).offset(y: -234)
                    Capsule().stroke(.secondary, lineWidth: 2).frame(width: 100, height: 12).offset(y: 234)
                    Circle().fill(.red.gradient).frame(width: 28, height: 28).position(puck)
                    Circle().fill(.orange.gradient).frame(width: 72, height: 72).position(x: opponentX, y: 70)
                    Circle().fill(.indigo.gradient).frame(width: 72, height: 72).position(x: playerX, y: 410)
                }.frame(width: 320, height: 480).clipShape(RoundedRectangle(cornerRadius: 24))
                    .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                        if value.location.y > 240 { playerX = min(max(value.location.x, 36), 284) }
                        else if mode == .human { opponentX = min(max(value.location.x, 36), 284) }
                    })
                    .onReceive(timer) { _ in tick() }
                Button("リセット", action: reset).buttonStyle(.bordered)
            }.padding(16).appReadableContentWidth(620)
        }.appScreenBackground()
    }

    private func reset() { scores = [0, 0]; winner = nil; playerX = 160; opponentX = 160; resetPuck(direction: Bool.random() ? 1 : -1) }
    private func resetPuck(direction: Int) { puck = CGPoint(x: 160, y: 240); velocity = CGVector(dx: CGFloat.random(in: -3...3), dy: CGFloat(direction) * 3.8) }
    private func tick() {
        guard winner == nil else { return }
        if mode == .cpu {
            let response: CGFloat = switch level { case .easy: 0.022; case .medium: 0.04; case .hard: 0.065 }
            opponentX += (puck.x - opponentX) * response
        }
        puck.x += velocity.dx; puck.y += velocity.dy
        if puck.x < 14 || puck.x > 306 { velocity.dx *= -1 }
        for (x, y) in [(playerX, CGFloat(410)), (opponentX, CGFloat(70))] {
            let dx = puck.x - x, dy = puck.y - y, distance = sqrt(dx * dx + dy * dy)
            if distance < 50 { let speed = max(4, sqrt(velocity.dx * velocity.dx + velocity.dy * velocity.dy)); velocity = CGVector(dx: dx / max(distance, 1) * speed, dy: dy / max(distance, 1) * speed) }
        }
        if puck.y < -10 { scores[1] += 1; scoreChanged(direction: -1) }
        else if puck.y > 490 { scores[0] += 1; scoreChanged(direction: 1) }
    }
    private func scoreChanged(direction: Int) { if let index = scores.firstIndex(where: { $0 >= 7 }) { winner = index == 0 ? (mode == .cpu ? "CPUの勝ち" : "P2の勝ち") : "P1の勝ち" } else { resetPuck(direction: direction) } }
}

private enum FlappyLevel: Int, CaseIterable, Identifiable {
    case easy, normal, hard
    var id: Self { self }
    var title: String { switch self { case .easy: "イージー"; case .normal: "ノーマル"; case .hard: "ハード" } }
    var speed: CGFloat { switch self { case .easy: 1.9; case .normal: 2.4; case .hard: 2.9 } }
    var halfGap: CGFloat { switch self { case .easy: 92; case .normal: 75; case .hard: 62 } }
}

struct FlappySkyGameView: View {
    @State private var level = FlappyLevel.normal
    @State private var birdY: CGFloat = 240
    @State private var velocity: CGFloat = 0
    @State private var obstacleX: CGFloat = 340
    @State private var gapY: CGFloat = 240
    @State private var score = 0
    @State private var running = false
    @State private var gameOver = false
    @AppStorage("flappyBestEasy") private var bestEasy = 0
    @AppStorage("flappyBestNormal") private var bestNormal = 0
    @AppStorage("flappyBestHard") private var bestHard = 0
    private let timer = Timer.publish(every: 1 / 60, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Picker("難易度", selection: $level) { ForEach(FlappyLevel.allCases) { Text($0.title).tag($0) } }.pickerStyle(.segmented).onChange(of: level) { reset() }
                GameScoreBar(left: "スコア \(score)", center: gameOver ? "ゲームオーバー" : running ? "タップで上昇" : "タップして開始", right: "ベスト \(currentBest)")
                ZStack {
                    LinearGradient(colors: [.cyan.opacity(0.35), .blue.opacity(0.1)], startPoint: .top, endPoint: .bottom)
                    ForEach(0..<3, id: \.self) { i in Image(systemName: "cloud.fill").foregroundStyle(.white.opacity(0.4)).font(.system(size: CGFloat(34 + i * 10))).position(x: CGFloat(65 + i * 95), y: CGFloat(70 + i * 55)) }
                    Rectangle().fill(.green.opacity(0.52)).frame(width: 54, height: max(0, gapY - level.halfGap)).position(x: obstacleX, y: max(0, gapY - level.halfGap) / 2)
                    Rectangle().fill(.green.opacity(0.52)).frame(width: 54, height: max(0, 480 - gapY - level.halfGap)).position(x: obstacleX, y: gapY + level.halfGap + max(0, 480 - gapY - level.halfGap) / 2)
                    Image(systemName: "bird.fill").font(.system(size: 32)).foregroundStyle(.orange).rotationEffect(.degrees(Double(min(max(velocity * 4, -25), 70)))).position(x: 80, y: birdY)
                }.frame(width: 320, height: 480).clipShape(RoundedRectangle(cornerRadius: 24)).contentShape(Rectangle())
                    .onTapGesture { flap() }.onReceive(timer) { _ in tick() }
                Button("リセット", action: reset).buttonStyle(.bordered)
            }.padding(16).appReadableContentWidth(620)
        }.appScreenBackground()
    }
    private var currentBest: Int { switch level { case .easy: bestEasy; case .normal: bestNormal; case .hard: bestHard } }
    private func flap() { if !running { running = true; gameOver = false; birdY = 240; obstacleX = 340; score = 0; velocity = 0 }; velocity = -6.5 }
    private func reset() { running = false; gameOver = false; score = 0; birdY = 240; velocity = 0; obstacleX = 340 }
    private func tick() {
        guard running else { return }
        velocity += 0.36; birdY += velocity; obstacleX -= level.speed
        if obstacleX < -30 { obstacleX = 350; gapY = CGFloat.random(in: 125...355); score += 1; saveBest() }
        let hitsPipe = abs(obstacleX - 80) < 42 && abs(birdY - gapY) > level.halfGap - 14
        if birdY < 0 || birdY > 480 || hitsPipe { running = false; gameOver = true; saveBest() }
    }
    private func saveBest() { switch level { case .easy: bestEasy = max(bestEasy, score); case .normal: bestNormal = max(bestNormal, score); case .hard: bestHard = max(bestHard, score) } }
}

private struct GameScoreBar: View {
    let left: String, center: String, right: String
    var body: some View {
        HStack { Text(left); Spacer(); Text(center).foregroundStyle(.secondary).lineLimit(1); Spacer(); Text(right) }
            .font(.subheadline.bold().monospacedDigit()).padding(12).frame(maxWidth: 620).appCard(cornerRadius: 14)
    }
}
