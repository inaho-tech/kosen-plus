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

    func appReadableContentWidth(_ width: CGFloat = 980) -> some View {
        self.frame(maxWidth: width).frame(maxWidth: .infinity)
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

