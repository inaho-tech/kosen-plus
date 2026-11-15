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
            if let symbol { Image(systemName: symbol).font(.subheadline).foregroundStyle(.secondary) }
            Text(title).font(.headline.weight(.semibold))
            Spacer()
            if let subtitle { Text(subtitle).font(.caption).foregroundStyle(.tertiary) }
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
            .font(.system(size: size * 0.4, weight: .medium))
            .foregroundStyle(AppPalette.accent)
            .frame(width: size, height: size)
            .background(.primary.opacity(0.045), in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
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

    func body(content: Content) -> some View {
        fallback(content)
    }

    private func fallback(_ content: Content) -> some View {
        content
            .background(AppPalette.card, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(.primary.opacity(0.055), lineWidth: 0.75)
            }
            .shadow(color: .black.opacity(0.025), radius: 5, y: 2)
    }
}

