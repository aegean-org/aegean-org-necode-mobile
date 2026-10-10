import SwiftUI

/// Presentation-only pagination; the caller owns cursor, requests, and errors.
struct PaginatedScrollView<Content: View>: View {
    let hasMore: Bool
    let loading: Bool
    var enabled = true
    var error: String?
    var loadingText = "正在加载更多…"
    let onLoadMore: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var userScrolling = false
    @State private var consumedGesture = false

    private struct Position: Equatable {
        let offset: CGFloat
        let nearTail: Bool
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                content()
                HStack(spacing: 8) {
                    if loading {
                        ProgressView().tint(LitterTheme.accentStrong)
                        Text(loadingText)
                    } else if let error {
                        VStack(spacing: 4) {
                            Text(error).lineLimit(2)
                            Button("重试加载", action: onLoadMore)
                                .foregroundColor(LitterTheme.accentStrong).disabled(!enabled)
                        }
                    } else {
                        Text(hasMore ? "上拉加载更多" : "已全部加载")
                    }
                }
                .litterFont(.footnote)
                .foregroundColor(LitterTheme.textSecondary)
                .frame(maxWidth: .infinity, minHeight: 56)
                .padding(.vertical, 8)
            }
            .padding(.horizontal, 16).padding(.bottom, 24)
        }
        .onScrollPhaseChange { _, phase in
            if phase == .tracking || phase == .interacting { userScrolling = true }
            if phase == .idle {
                userScrolling = false
                consumedGesture = false
            }
        }
        .onScrollGeometryChange(for: Position.self) { geometry in
            Position(offset: geometry.contentOffset.y,
                     nearTail: geometry.contentSize.height - geometry.visibleRect.maxY <= 56)
        } action: { previous, current in
            // Never load from appearance/layout changes, nor chain pages in one gesture.
            guard userScrolling, current.offset > previous.offset, current.nearTail,
                  !consumedGesture, enabled, hasMore, !loading, error == nil else { return }
            consumedGesture = true
            onLoadMore()
        }
    }
}
