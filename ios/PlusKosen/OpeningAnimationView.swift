import SwiftUI

struct OpeningAnimationView: View {
    let reduceMotion: Bool
    @State private var appeared = false
    @State private var glow = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            Circle()
                .fill(Color.indigo.opacity(glow ? 0.24 : 0.08))
                .frame(width: 270, height: 270)
                .blur(radius: 36)
                .scaleEffect(glow ? 1.15 : 0.8)

            VStack(spacing: 20) {
                KosenPlusMark()
                    .frame(width: 138, height: 138)
                    .scaleEffect(appeared ? 1 : 0.72)
                    .opacity(appeared ? 1 : 0)
                VStack(spacing: 5) {
                    Text("高専プラス").font(.title.bold()).foregroundStyle(.white)
                    Text("KOSEN+").font(.caption.weight(.semibold)).tracking(3).foregroundStyle(.white.opacity(0.58))
                }
                .offset(y: appeared ? 0 : 12)
                .opacity(appeared ? 1 : 0)
            }
        }
        .onAppear {
            guard !reduceMotion else { appeared = true; glow = true; return }
            withAnimation(.spring(response: 0.62, dampingFraction: 0.72)) { appeared = true }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { glow = true }
        }
    }
}

struct KosenPlusMark: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 31, style: .continuous).fill(.black)
                .overlay { RoundedRectangle(cornerRadius: 31, style: .continuous).stroke(.white.opacity(0.12), lineWidth: 1) }
                .shadow(color: .indigo.opacity(0.36), radius: 24, y: 10)
            Text("K").font(.system(size: 83, weight: .black, design: .rounded)).foregroundStyle(.white).offset(x: -5)
            Image(systemName: "plus").font(.system(size: 31, weight: .black)).foregroundStyle(Color(red: 1, green: 0.09, blue: 0.27)).offset(x: 43, y: -39)
        }
    }
}
