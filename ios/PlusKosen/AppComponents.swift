import SwiftUI

enum AppPalette {
    static let accent = Color.indigo
    static let background = Color(.systemGroupedBackground)
    static let card = Color(.secondarySystemGroupedBackground)
}

struct AppSectionHeader: View {
    let title: String
    var subtitle: String? = nil
    var symbol: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            if let symbol { Image(systemName: symbol).foregroundStyle(AppPalette.accent) }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.title3.bold())
                if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            }
            Spacer()
        }
        .accessibilityAddTraits(.isHeader)
    }
}

struct AppIconTile: View {
    let symbol: String
    let color: Color
    var size: CGFloat = 46

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
    }
}

extension View {
    func appCard(cornerRadius: CGFloat = 18) -> some View {
        modifier(LiquidGlassCardModifier(cornerRadius: cornerRadius))
    }

    func appScreenBackground() -> some View {
        self.background(AppPalette.background.ignoresSafeArea())
    }
}

private struct LiquidGlassCardModifier: ViewModifier {
    let cornerRadius: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
#if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content.glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            fallback(content)
        }
#else
        fallback(content)
#endif
    }

    private func fallback(_ content: Content) -> some View {
        content
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(LinearGradient(colors: [.white.opacity(0.38), .white.opacity(0.06), .primary.opacity(0.08)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 0.8)
            }
            .shadow(color: .black.opacity(0.06), radius: 12, y: 5)
    }
}

struct LiquidPillNavigation: View {
    @Binding var selection: Int
    private let items = [
        ("ホーム", "graduationcap.fill"),
        ("シラバス", "doc.text.fill"),
        ("時刻表", "clock.fill"),
        ("ツール", "hammer.fill"),
        ("設定", "person.crop.circle.fill")
    ]

    var body: some View {
        HStack(spacing: 3) {
            ForEach(items.indices, id: \.self) { index in
                Button { withAnimation(.snappy(duration: 0.28)) { selection = index } } label: {
                    VStack(spacing: 3) {
                        Image(systemName: items[index].1).font(.system(size: 17, weight: .semibold))
                        Text(items[index].0).font(.system(size: 9.5, weight: .semibold)).lineLimit(1)
                    }
                    .foregroundStyle(selection == index ? Color.white : Color.primary.opacity(0.7))
                    .frame(maxWidth: .infinity).frame(height: 49)
                    .background {
                        if selection == index { Capsule().fill(AppPalette.accent.gradient).shadow(color: AppPalette.accent.opacity(0.28), radius: 8, y: 3).matchedGeometryEffectSafe(id: "pill-selection") }
                    }
                }.buttonStyle(.plain).accessibilityLabel(items[index].0)
            }
        }
        .padding(5)
        .frame(maxWidth: 430)
        .appCard(cornerRadius: 28)
        .padding(.horizontal, 12)
        .padding(.top, 5)
        .padding(.bottom, 4)
    }
}

private extension View {
    func matchedGeometryEffectSafe(id: String) -> some View { self }
}
