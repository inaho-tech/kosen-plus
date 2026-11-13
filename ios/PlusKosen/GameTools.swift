import SwiftUI

struct OthelloGameView: View {
    @State private var board = OthelloGameView.initialBoard
    @State private var message = "黒の番です"
    @State private var thinking = false

    var body: some View {
        VStack(spacing: 16) {
            GameScoreBar(left: "黒 \(board.filter { $0 == 1 }.count)", center: message, right: "白 \(board.filter { $0 == -1 }.count)")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 8), spacing: 2) {
                ForEach(0..<64, id: \.self) { index in
                    ZStack {
                        Rectangle().fill(Color.green.opacity(0.82)).aspectRatio(1, contentMode: .fit)
                        if board[index] != 0 { Circle().fill(board[index] == 1 ? Color.black : Color.white).padding(4).shadow(radius: 1) }
                        else if !thinking && !flips(for: index, player: 1).isEmpty { Circle().fill(.black.opacity(0.14)).padding(12) }
                    }.onTapGesture { play(index) }
                }
            }.padding(5).background(.green.opacity(0.45), in: RoundedRectangle(cornerRadius: 14)).frame(maxWidth: 520)
            Button("最初から", action: reset).buttonStyle(.bordered)
        }.padding().appScreenBackground()
    }

    private static var initialBoard: [Int] { var value = Array(repeating: 0, count: 64); value[27] = -1; value[28] = 1; value[35] = 1; value[36] = -1; return value }
    private func reset() { board = Self.initialBoard; message = "黒の番です"; thinking = false }
    private func flips(for index: Int, player: Int) -> [Int] {
        guard board[index] == 0 else { return [] }
        let row = index / 8, column = index % 8
        var result: [Int] = []
        for (dr, dc) in [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)] {
            var r = row + dr, c = column + dc, line: [Int] = []
            while (0..<8).contains(r), (0..<8).contains(c), board[r * 8 + c] == -player { line.append(r * 8 + c); r += dr; c += dc }
            if !line.isEmpty, (0..<8).contains(r), (0..<8).contains(c), board[r * 8 + c] == player { result += line }
        }
        return result
    }
    private func moves(for player: Int) -> [Int] { board.indices.filter { !flips(for: $0, player: player).isEmpty } }
    private func place(_ index: Int, player: Int) { let values = flips(for: index, player: player); guard !values.isEmpty else { return }; board[index] = player; values.forEach { board[$0] = player } }
    private func play(_ index: Int) {
        guard !thinking, !flips(for: index, player: 1).isEmpty else { return }
        place(index, player: 1); thinking = true; message = "白が考えています…"
        Task { try? await Task.sleep(for: .milliseconds(420)); await MainActor.run { cpuTurn() } }
    }
    private func cpuTurn() {
        let options = moves(for: -1)
        if let move = options.max(by: { flips(for: $0, player: -1).count < flips(for: $1, player: -1).count }) { place(move, player: -1) }
        thinking = false
        if moves(for: 1).isEmpty && moves(for: -1).isEmpty {
            let black = board.filter { $0 == 1 }.count, white = board.filter { $0 == -1 }.count
            message = black == white ? "引き分け" : black > white ? "黒の勝ち" : "白の勝ち"
        } else if moves(for: 1).isEmpty { message = "黒はパス"; thinking = true; Task { try? await Task.sleep(for: .milliseconds(450)); await MainActor.run { cpuTurn() } } }
        else { message = "黒の番です" }
    }
}

struct GomokuGameView: View {
    @State private var board = Array(repeating: 0, count: 121)
    @State private var message = "黒の番です"
    @State private var finished = false
    var body: some View {
        VStack(spacing: 14) {
            Text(message).font(.headline)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 1), count: 11), spacing: 1) {
                ForEach(0..<121, id: \.self) { index in
                    ZStack { Rectangle().fill(Color.brown.opacity(0.35)).aspectRatio(1, contentMode: .fit); if board[index] != 0 { Circle().fill(board[index] == 1 ? .black : .white).padding(2) } }.onTapGesture { play(index) }
                }
            }.padding(4).background(.brown.opacity(0.2), in: RoundedRectangle(cornerRadius: 12)).frame(maxWidth: 580)
            Button("最初から") { board = Array(repeating: 0, count: 121); message = "黒の番です"; finished = false }.buttonStyle(.bordered)
        }.padding().appScreenBackground()
    }
    private func play(_ index: Int) { guard !finished, board[index] == 0 else { return }; board[index] = 1; if won(index, 1) { message = "黒の勝ち"; finished = true; return }; message = "白が考えています…"; Task { try? await Task.sleep(for: .milliseconds(300)); await MainActor.run { cpu() } } }
    private func cpu() {
        let empty = board.indices.filter { board[$0] == 0 }
        guard let move = empty.max(by: { score($0) < score($1) }) else { message = "引き分け"; finished = true; return }
        board[move] = -1; if won(move, -1) { message = "白の勝ち"; finished = true } else { message = "黒の番です" }
    }
    private func score(_ index: Int) -> Int { let r=index/11,c=index%11; return board.indices.reduce(0) { value, i in let ir=i/11,ic=i%11; guard board[i] != 0 else{return value}; let distance=abs(r-ir)+abs(c-ic); return value + (distance <= 2 ? (board[i] == -1 ? 4 : 3) : 0) } }
    private func won(_ index: Int, _ player: Int) -> Bool { let row=index/11,col=index%11; return [(1,0),(0,1),(1,1),(1,-1)].contains { dr,dc in var count=1; for sign in [-1,1] { var r=row+dr*sign,c=col+dc*sign; while (0..<11).contains(r),(0..<11).contains(c),board[r*11+c] == player { count += 1; r += dr*sign; c += dc*sign } }; return count >= 5 } }
}

struct NumberMergeGameView: View {
    @State private var cells = NumberMergeGameView.newBoard()
    @State private var score = 0
    var body: some View {
        VStack(spacing: 16) {
            GameScoreBar(left: "スコア", center: "\(score)", right: "最高 \(cells.max() ?? 0)")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 7), count: 4), spacing: 7) {
                ForEach(0..<16, id: \.self) { index in Text(cells[index] == 0 ? "" : "\(cells[index])").font(.title2.bold().monospacedDigit()).foregroundStyle(cells[index] >= 8 ? .white : .primary).frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit).background(tileColor(cells[index]), in: RoundedRectangle(cornerRadius: 12)) }
            }.padding(9).background(.brown.opacity(0.16), in: RoundedRectangle(cornerRadius: 17)).frame(maxWidth: 520)
                .gesture(DragGesture(minimumDistance: 20).onEnded { value in move(dx: value.translation.width, dy: value.translation.height) })
            Text("盤面を上下左右にスワイプ").font(.caption).foregroundStyle(.secondary)
            Button("最初から") { score = 0; cells = Self.newBoard() }.buttonStyle(.bordered)
        }.padding().appScreenBackground()
    }
    private static func newBoard() -> [Int] { var values=Array(repeating:0,count:16); for _ in 0..<2 { if let index=values.indices.filter({values[$0]==0}).randomElement(){values[index]=2} }; return values }
    private func move(dx: CGFloat, dy: CGFloat) { let horizontal=abs(dx)>abs(dy), forward=horizontal ? dx<0 : dy<0; var next=cells; var gained=0; for line in 0..<4 { var indexes=(0..<4).map { horizontal ? line*4+$0 : $0*4+line }; if !forward { indexes.reverse() }; var values=indexes.map{cells[$0]}.filter{$0 != 0}; var merged:[Int]=[]; var i=0; while i<values.count { if i+1<values.count && values[i]==values[i+1] { let value=values[i]*2; merged.append(value); gained += value; i += 2 } else { merged.append(values[i]); i += 1 } }; merged += Array(repeating:0,count:4-merged.count); for (position,index) in indexes.enumerated(){next[index]=merged[position]} }; guard next != cells else{return}; cells=next; score += gained; if let index=cells.indices.filter({cells[$0]==0}).randomElement(){cells[index]=Int.random(in:0..<10)==0 ? 4 : 2} }
    private func tileColor(_ value: Int) -> Color { if value == 0 { return .secondary.opacity(0.12) }; let colors:[Color]=[.orange.opacity(0.18),.orange.opacity(0.32),.orange,.pink,.purple,.indigo,.blue,.teal]; return colors[min(Int(log2(Double(value)))-1,colors.count-1)] }
}

private struct MinesBoard {
    var mines: Set<Int> = []
    var opened: Set<Int> = []
    var flags: Set<Int> = []
    init() { mines = Set((0..<81).shuffled().prefix(10)) }
    func nearby(_ index: Int) -> [Int] { let r=index/9,c=index%9; return (-1...1).flatMap { dr in (-1...1).compactMap { dc in let nr=r+dr,nc=c+dc; return (dr != 0 || dc != 0) && (0..<9).contains(nr) && (0..<9).contains(nc) ? nr*9+nc : nil } } }
}

struct MinesweeperGameView: View {
    @State private var game = MinesBoard()
    @State private var message = "安全なマスを開いてください"
    @State private var finished = false
    var body: some View {
        VStack(spacing: 14) {
            GameScoreBar(left: "地雷 10", center: message, right: "旗 \(game.flags.count)")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 9), spacing: 2) {
                ForEach(0..<81, id: \.self) { index in
                    Button { open(index) } label: { ZStack { RoundedRectangle(cornerRadius: 4).fill(game.opened.contains(index) ? .secondary.opacity(0.1) : .indigo.opacity(0.28)); if game.opened.contains(index) { if game.mines.contains(index) { Image(systemName:"burst.fill").foregroundStyle(.red) } else { let count=game.nearby(index).filter(game.mines.contains).count; if count>0 { Text("\(count)").font(.caption.bold()) } } } else if game.flags.contains(index) { Image(systemName:"flag.fill").foregroundStyle(.orange) } }.aspectRatio(1,contentMode:.fit) }.buttonStyle(.plain).contextMenu { Button(game.flags.contains(index) ? "旗を外す" : "旗を立てる", systemImage:"flag") { if game.flags.contains(index){game.flags.remove(index)}else{game.flags.insert(index)} } }
                }
            }.frame(maxWidth: 520)
            Button("新しいゲーム") { game=MinesBoard(); message="安全なマスを開いてください"; finished=false }.buttonStyle(.bordered)
        }.padding().appScreenBackground()
    }
    private func open(_ index:Int){ guard !finished,!game.flags.contains(index) else{return}; var queue=[index]; while let value=queue.popLast(){guard !game.opened.contains(value) else{continue};game.opened.insert(value);if game.mines.contains(value){message="ゲームオーバー";finished=true;game.opened.formUnion(game.mines);return};if game.nearby(value).filter(game.mines.contains).isEmpty{queue += game.nearby(value).filter{!game.opened.contains($0)}}};if game.opened.count==71{message="クリア！";finished=true} }
}

struct AirHockeyGameView: View {
    @State private var playerX: CGFloat = 160
    @State private var cpuX: CGFloat = 160
    @State private var puck = CGPoint(x:160,y:240)
    @State private var velocity = CGVector(dx:2.8,dy:3.6)
    @State private var scores = [0,0]
    private let timer = Timer.publish(every: 1/60, on: .main, in: .common).autoconnect()
    var body: some View {
        VStack(spacing:12){ GameScoreBar(left:"CPU \(scores[0])",center:"先に5点",right:"YOU \(scores[1])"); ZStack { RoundedRectangle(cornerRadius:24).fill(.cyan.opacity(0.12)); Rectangle().fill(.white.opacity(0.6)).frame(height:2); Capsule().stroke(.secondary,lineWidth:2).frame(width:100,height:12).offset(y:-234); Capsule().stroke(.secondary,lineWidth:2).frame(width:100,height:12).offset(y:234); Circle().fill(.red.gradient).frame(width:28,height:28).position(puck); Circle().fill(.orange.gradient).frame(width:72,height:72).position(x:cpuX,y:70); Circle().fill(.indigo.gradient).frame(width:72,height:72).position(x:playerX,y:410) }.frame(width:320,height:480).clipShape(RoundedRectangle(cornerRadius:24)).gesture(DragGesture().onChanged{playerX=min(max($0.location.x,36),284)}).onReceive(timer){_ in tick()}; Button("リセット"){scores=[0,0];resetPuck(direction:Bool.random() ? 1 : -1)}.buttonStyle(.bordered) }.padding().appScreenBackground()
    }
    private func resetPuck(direction:Int){puck=CGPoint(x:160,y:240);velocity=CGVector(dx:CGFloat.random(in:-3...3),dy:CGFloat(direction)*3.8)}
    private func tick(){cpuX += (puck.x-cpuX)*0.035;puck.x += velocity.dx;puck.y += velocity.dy;if puck.x<14 || puck.x>306{velocity.dx *= -1};for (x,y) in [(playerX,CGFloat(410)),(cpuX,CGFloat(70))]{let dx=puck.x-x,dy=puck.y-y,dist=sqrt(dx*dx+dy*dy);if dist<50{let speed=max(4,sqrt(velocity.dx*velocity.dx+velocity.dy*velocity.dy));velocity=CGVector(dx:dx/max(dist,1)*speed,dy:dy/max(dist,1)*speed)}};if puck.y < -10{scores[1]+=1;resetPuck(direction:-1)}else if puck.y>490{scores[0]+=1;resetPuck(direction:1)};if scores.max() ?? 0 >= 5{scores=[0,0]} }
}

struct FlappySkyGameView: View {
    @State private var birdY: CGFloat = 240
    @State private var speed: CGFloat = 0
    @State private var obstacleX: CGFloat = 340
    @State private var gapY: CGFloat = 240
    @State private var score = 0
    @State private var running = false
    @AppStorage("flappyBest") private var best=0
    private let timer=Timer.publish(every:1/60,on:.main,in:.common).autoconnect()
    var body: some View { VStack(spacing:12){GameScoreBar(left:"スコア \(score)",center:running ? "タップで上昇" : "タップして開始",right:"ベスト \(best)");ZStack{LinearGradient(colors:[.cyan.opacity(0.35),.blue.opacity(0.12)],startPoint:.top,endPoint:.bottom);Rectangle().fill(.green.opacity(0.55)).frame(width:54,height:max(0,gapY-75)).position(x:obstacleX,y:max(0,gapY-75)/2);Rectangle().fill(.green.opacity(0.55)).frame(width:54,height:max(0,480-gapY-75)).position(x:obstacleX,y:gapY+75+max(0,480-gapY-75)/2);Image(systemName:"bird.fill").font(.system(size:32)).foregroundStyle(.orange).position(x:80,y:birdY)}.frame(width:320,height:480).clipShape(RoundedRectangle(cornerRadius:24)).contentShape(Rectangle()).onTapGesture{if !running{running=true;birdY=240;obstacleX=340;score=0;speed=0};speed = -6.5}.onReceive(timer){_ in tick()};Button("リセット"){running=false;score=0;birdY=240}.buttonStyle(.bordered)}.padding().appScreenBackground() }
    private func tick(){guard running else{return};speed += 0.36;birdY += speed;obstacleX -= 2.4;if obstacleX < -30{obstacleX=350;gapY=CGFloat.random(in:135...345);score += 1;best=max(best,score)};let hitsPipe=abs(obstacleX-80)<42 && abs(birdY-gapY)>61;if birdY<0 || birdY>480 || hitsPipe{running=false;best=max(best,score)} }
}

private struct GameScoreBar: View { let left,center,right:String; var body:some View{HStack{Text(left);Spacer();Text(center).foregroundStyle(.secondary);Spacer();Text(right)}.font(.subheadline.bold().monospacedDigit()).padding(12).frame(maxWidth:620).appCard(cornerRadius:14)} }
