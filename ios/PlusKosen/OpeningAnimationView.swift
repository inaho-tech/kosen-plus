import SwiftUI

struct OpeningAnimationView: View {
    let reduceMotion: Bool
    @State private var appeared = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            Image("AppLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 220, height: 220)
                .scaleEffect(appeared ? 1 : 0.9)
                .opacity(appeared ? 1 : 0)
        }
        .onAppear {
            guard !reduceMotion else {
                appeared = true
                return
            }
            withAnimation(.easeOut(duration: 0.45)) {
                appeared = true
            }
        }
    }
}
