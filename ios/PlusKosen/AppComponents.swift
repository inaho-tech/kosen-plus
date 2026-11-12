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
        self
            .background(AppPalette.card, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).stroke(.primary.opacity(0.055), lineWidth: 1))
    }

    func appScreenBackground() -> some View {
        self.background(AppPalette.background.ignoresSafeArea())
    }
}
