import SwiftUI
import Textual
import UIKit

// MARK: - Reusable bubble components

enum LitterMarkdownStyleVariant {
    case content
    case system
}

private extension VerticalAlignment {
    private enum LitterFirstTextCenterAlignment: AlignmentID {
        static func defaultValue(in context: ViewDimensions) -> CGFloat {
            let firstLineHeight =
                context.height - (context[.lastTextBaseline] - context[.firstTextBaseline])
            return firstLineHeight / 2
        }
    }

    static let litterFirstTextCenter = Self(LitterFirstTextCenterAlignment.self)
}

struct LitterMarkdownView: View {
    let markdown: String
    var style: LitterMarkdownStyleVariant = .content
    var bodySize: CGFloat = LitterFont.conversationBodyPointSize
    var codeSize: CGFloat = LitterFont.conversationBodyPointSize
    var selectionEnabled = true

    var body: some View {
        renderedMarkdown(selectionEnabled: selectionEnabled)
    }

    @ViewBuilder
    private func renderedMarkdown(selectionEnabled: Bool) -> some View {
        switch style {
        case .content:
            StructuredText(markdown: markdown, syntaxExtensions: [.math])
                .litterContentMarkdown(
                    bodySize: bodySize,
                    codeSize: codeSize,
                    selectionEnabled: selectionEnabled
                )
        case .system:
            StructuredText(markdown: markdown, syntaxExtensions: [.math])
                .litterSystemMarkdown(
                    bodySize: bodySize,
                    codeSize: codeSize,
                    selectionEnabled: selectionEnabled
                )
        }
    }
}

struct InlineSelectableMarkdownMessage<Content: View>: View {
    let markdown: String
    var style: LitterMarkdownStyleVariant = .content
    var bodySize: CGFloat = LitterFont.conversationBodyPointSize
    var codeSize: CGFloat = LitterFont.conversationBodyPointSize
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
    }
}

private extension LitterMarkdownStyleVariant {
    var cacheKey: String {
        switch self {
        case .content:
            return "content"
        case .system:
            return "system"
        }
    }
}

struct UserBubble: View {
    let text: String
    var images: [ChatImage] = []
    var compact: Bool = false
    private let contentFontSize = LitterFont.conversationBodyPointSize

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Spacer(minLength: compact ? 30 : 60)
            VStack(alignment: .trailing, spacing: compact ? 4 : 8) {
                ForEach(images) { img in
                    if let uiImage = UserBubble.decodeImage(img) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFit()
                            .frame(maxWidth: 200, maxHeight: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
                if !text.isEmpty {
                    Text(text)
                        .litterFont(size: contentFontSize)
                        .foregroundColor(LitterTheme.textPrimary)
                        .textSelection(.enabled)
                }
            }
            .padding(.horizontal, compact ? 10 : 14)
            .padding(.vertical, compact ? 6 : 10)
            .modifier(GlassRectModifier(cornerRadius: compact ? 10 : 14, tint: LitterTheme.accent.opacity(0.3)))
        }
    }

    private static let imageCache = NSCache<NSString, UIImage>()

    private static func decodeImage(_ image: ChatImage) -> UIImage? {
        let key = image.cacheKey as NSString
        if let cached = imageCache.object(forKey: key) { return cached }
        guard let data = imageData(for: image) else { return nil }
        guard let image = UIImage(data: data) else { return nil }
        imageCache.setObject(image, forKey: key)
        return image
    }

    private static func imageData(for image: ChatImage) -> Data? {
        let source = image.source
        guard source.hasPrefix("data:") || source.hasPrefix("file://") else {
            return nil
        }

        if source.hasPrefix("file://") {
            let path = String(source.dropFirst("file://".count))
            return FileManager.default.contents(atPath: path)
        }

        guard let commaIndex = source.firstIndex(of: ",") else { return nil }
        let base64 = String(source[source.index(after: commaIndex)...])
        return Data(base64Encoded: base64, options: .ignoreUnknownCharacters)
    }
}

struct AssistantBubble: View, Equatable {
    let markdownString: String
    let markdownIdentity: Int
    var label: String? = nil
    var compact: Bool = false
    var themeVersion: Int = 0
    var allowsInlineSelection: Bool = true
    private let contentFontSize = LitterFont.conversationBodyPointSize

    init(
        text: String,
        label: String? = nil,
        compact: Bool = false,
        themeVersion: Int = 0,
        allowsInlineSelection: Bool = true
    ) {
        self.markdownString = text
        self.markdownIdentity = text.hashValue
        self.label = label
        self.compact = compact
        self.themeVersion = themeVersion
        self.allowsInlineSelection = allowsInlineSelection
    }

    init(
        markdownString: String,
        markdownIdentity: Int,
        label: String? = nil,
        compact: Bool = false,
        themeVersion: Int = 0,
        allowsInlineSelection: Bool = true
    ) {
        self.markdownString = markdownString
        self.markdownIdentity = markdownIdentity
        self.label = label
        self.compact = compact
        self.themeVersion = themeVersion
        self.allowsInlineSelection = allowsInlineSelection
    }

    static func == (lhs: AssistantBubble, rhs: AssistantBubble) -> Bool {
        lhs.markdownIdentity == rhs.markdownIdentity &&
        lhs.label == rhs.label &&
        lhs.compact == rhs.compact &&
        lhs.themeVersion == rhs.themeVersion &&
        lhs.allowsInlineSelection == rhs.allowsInlineSelection
    }

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            if allowsInlineSelection {
                InlineSelectableMarkdownMessage(
                    markdown: markdownString,
                    style: .content,
                    bodySize: contentFontSize,
                    codeSize: contentFontSize
                ) {
                    bubbleContent
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                bubbleContent
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Spacer(minLength: compact ? 8 : 20)
        }
    }

    private var bubbleContent: some View {
        VStack(alignment: .leading, spacing: compact ? 4 : 8) {
            if let label {
                Text(label)
                    .litterFont(.caption2, weight: .semibold)
                    .foregroundColor(LitterTheme.textSecondary)
            }
            LitterMarkdownView(
                markdown: markdownString,
                style: .content,
                bodySize: contentFontSize,
                codeSize: contentFontSize
            )
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

struct AssistantBlocksBubble: View {
    let segments: [MessageRenderCache.AssistantSegment]
    var label: String? = nil
    var compact: Bool = false
    private let contentFontSize = LitterFont.conversationBodyPointSize

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: compact ? 4 : 8) {
                if let label {
                    Text(label)
                        .litterFont(.caption2, weight: .semibold)
                        .foregroundColor(LitterTheme.textSecondary)
                }

                ForEach(segments) { segment in
                    segmentView(segment)
                        .transition(.asymmetric(
                            insertion: .push(from: .top),
                            removal: .identity
                        ))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer(minLength: compact ? 8 : 20)
        }
    }

    @ViewBuilder
    private func segmentView(_ segment: MessageRenderCache.AssistantSegment) -> some View {
        switch segment.kind {
        case .markdown(let content, let identity):
            LitterMarkdownView(
                markdown: content,
                style: .content,
                bodySize: contentFontSize,
                codeSize: contentFontSize
            )
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(identity)
        case .codeBlock(let language, let code, let identity):
            if isMathCodeBlock(language) {
                LitterMarkdownView(
                    markdown: mathBlockMarkdown(code),
                    style: .content,
                    bodySize: contentFontSize,
                    codeSize: contentFontSize
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .id(identity)
            } else {
                CodeBlockView(
                    language: language ?? "",
                    code: code,
                    fontSize: contentFontSize
                )
                .id(identity)
            }
        case .image(let uiImage):
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFit()
                .frame(maxHeight: 300)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }

    private func isMathCodeBlock(_ language: String?) -> Bool {
        guard let language else { return false }
        return language.trimmingCharacters(in: .whitespacesAndNewlines)
            .caseInsensitiveCompare("math") == .orderedSame
    }

    private func mathBlockMarkdown(_ code: String) -> String {
        "```math\n\(code)\n```"
    }
}

struct StreamingAssistantBubble: View {
    private let renderCache = StreamingAssistantRenderCache.shared
    let itemId: String
    let text: String
    var label: String? = nil
    var themeVersion: Int = 0
    var onSnapshotRendered: (() -> Void)? = nil
    @State private var renderedSegments: [MessageRenderCache.AssistantSegment] = []
    @State private var pendingText: String?
    @State private var flushWorkItem: DispatchWorkItem?
    @State private var animating = false

    private let flushInterval: TimeInterval = 0.1

    var body: some View {
        AssistantBlocksBubble(
            segments: renderedSegments,
            label: label,
            compact: false
        )
            .onAppear {
                renderedSegments = renderCache.segments(itemId: itemId, text: text)
                onSnapshotRendered?()
            }
            .onChange(of: text) {
                pendingText = text
                guard flushWorkItem == nil, !animating else { return }
                scheduleFlush()
            }
            .onDisappear {
                flushWorkItem?.cancel()
                flushWorkItem = nil
                pendingText = nil
                animating = false
            }
    }

    private func scheduleFlush() {
        let work = DispatchWorkItem {
            flushWorkItem = nil
            flush()
        }
        flushWorkItem = work
        DispatchQueue.main.asyncAfter(deadline: .now() + flushInterval, execute: work)
    }

    private func flush() {
        guard let next = pendingText else { return }
        pendingText = nil

        let nextSegments = renderCache.segments(itemId: itemId, text: next)

        if nextSegments.count > renderedSegments.count {
            animating = true
            withAnimation(.easeOut(duration: 0.25)) {
                renderedSegments = nextSegments
            } completion: {
                animating = false
                onSnapshotRendered?()
                if pendingText != nil {
                    flush()
                }
            }
        } else {
            renderedSegments = nextSegments
            onSnapshotRendered?()
            if pendingText != nil {
                flush()
            }
        }
    }
}

// MARK: - Full message bubble (used in conversation)

struct MessageBubbleView: View {
    private let renderCache = MessageRenderCache.shared
    let message: ChatMessage
    let serverId: String?
    let agentDirectoryVersion: UInt64
    let isStreamingMessage: Bool
    let actionsDisabled: Bool
    let onStreamingSnapshotRendered: (() -> Void)?
    let resolveTargetLabel: ((String) -> String?)?
    let onWidgetPrompt: ((String) -> Void)?
    let onEditUserMessage: ((ChatMessage) -> Void)?
    let onForkFromUserMessage: ((ChatMessage) -> Void)?
    private let contentFontSize = LitterFont.conversationBodyPointSize

    init(
        message: ChatMessage,
        serverId: String? = nil,
        agentDirectoryVersion: UInt64 = 0,
        isStreamingMessage: Bool = false,
        actionsDisabled: Bool = false,
        onStreamingSnapshotRendered: (() -> Void)? = nil,
        resolveTargetLabel: ((String) -> String?)? = nil,
        onWidgetPrompt: ((String) -> Void)? = nil,
        onEditUserMessage: ((ChatMessage) -> Void)? = nil,
        onForkFromUserMessage: ((ChatMessage) -> Void)? = nil
    ) {
        self.message = message
        self.serverId = serverId
        self.agentDirectoryVersion = agentDirectoryVersion
        self.isStreamingMessage = isStreamingMessage
        self.actionsDisabled = actionsDisabled
        self.onStreamingSnapshotRendered = onStreamingSnapshotRendered
        self.resolveTargetLabel = resolveTargetLabel
        self.onWidgetPrompt = onWidgetPrompt
        self.onEditUserMessage = onEditUserMessage
        self.onForkFromUserMessage = onForkFromUserMessage
    }

    var body: some View {
        Group {
            if message.role == .user {
                userBubbleWithActions
            } else if message.role == .assistant {
                assistantContent
            } else if isReasoning {
                HStack(alignment: .top, spacing: 0) {
                    reasoningContent
                    Spacer(minLength: 20)
                }
            } else {
                HStack(alignment: .top, spacing: 0) {
                    systemBubble
                    Spacer(minLength: 20)
                }
            }
        }
    }

    private var renderRevisionKey: MessageRenderCache.RevisionKey {
        MessageRenderCache.makeRevisionKey(
            for: message,
            serverId: serverId,
            agentDirectoryVersion: agentDirectoryVersion,
            isStreaming: isStreamingMessage
        )
    }

    private var isReasoning: Bool {
        let trimmed = message.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("### ") else { return false }
        let firstLine = trimmed.prefix(while: { $0 != "\n" })
        return firstLine.lowercased().contains("reason")
    }

    private var supportsUserActions: Bool {
        message.role == .user &&
            message.isFromUserTurnBoundary &&
            message.sourceTurnIndex != nil
    }

    private var userBubbleWithActions: some View {
        UserBubble(text: message.text, images: message.images)
            .contextMenu {
                if supportsUserActions {
                    Button("Edit Message") {
                        onEditUserMessage?(message)
                    }
                    .disabled(actionsDisabled || onEditUserMessage == nil)

                    Button("Fork From Here") {
                        onForkFromUserMessage?(message)
                    }
                    .disabled(actionsDisabled || onForkFromUserMessage == nil)
                }
            }
    }

    @ViewBuilder
    private var assistantContent: some View {
        if isStreamingMessage {
            StreamingAssistantBubble(
                itemId: message.id.uuidString,
                text: message.text,
                label: assistantAgentLabel,
                onSnapshotRendered: onStreamingSnapshotRendered
            )
        } else {
            AssistantBlocksBubble(
                segments: assistantSegmentsForRendering,
                label: assistantAgentLabel
            )
        }
    }

    private var assistantAgentLabel: String? {
        AgentLabelFormatter.format(
            nickname: message.agentNickname,
            role: message.agentRole
        )
    }

    private var reasoningContent: some View {
        let (_, body) = extractSystemTitleAndBody(message.text)
        return Text(normalizedReasoningText(body))
            .litterFont(size: contentFontSize)
            .italic()
            .foregroundColor(LitterTheme.textSecondary)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var systemBubble: some View {
        if let widget = message.widgetState {
            WidgetContainerView(
                widget: widget,
                onMessage: handleWidgetMessage
            )
        } else {
            let parsed = systemParseResultForRendering
            switch parsed {
            case .recognized(let model):
                ToolCallCardView(model: model)
            case .unrecognized:
                genericSystemBubble
            }
        }
    }

    private func handleWidgetMessage(_ body: Any) {
        guard let dict = body as? [String: Any],
              let type = dict["_type"] as? String else { return }
        switch type {
        case "sendPrompt":
            if let text = dict["text"] as? String, !text.isEmpty {
                onWidgetPrompt?(text)
            }
        case "openLink":
            if let urlStr = dict["url"] as? String, let url = URL(string: urlStr) {
                UIApplication.shared.open(url)
            }
        default:
            break
        }
    }

    private var genericSystemBubble: some View {
        let (title, body) = extractSystemTitleAndBody(message.text)
        let markdown = title == nil ? message.text : body
        let displayTitle = title ?? "System"

        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "info.circle.fill")
                    .litterFont(size: 11, weight: .semibold)
                    .foregroundColor(LitterTheme.accent)
                Text(displayTitle.uppercased())
                    .litterFont(.caption2, weight: .bold)
                    .foregroundColor(LitterTheme.accent)
                Spacer()
            }

            if !markdown.isEmpty {
                LitterMarkdownView(
                    markdown: markdown,
                    style: .system,
                    bodySize: contentFontSize,
                    codeSize: contentFontSize
                )
                    .padding(.top, 8)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .modifier(GlassRectModifier(cornerRadius: 12))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 1)
                .fill(LitterTheme.accent.opacity(0.9))
                .frame(width: 3)
                .padding(.vertical, 6)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func extractSystemTitleAndBody(_ text: String) -> (String?, String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("### ") else { return (nil, trimmed) }
        let lines = trimmed.split(separator: "\n", omittingEmptySubsequences: false)
        guard let first = lines.first else { return (nil, trimmed) }
        let title = first.dropFirst(4).trimmingCharacters(in: .whitespacesAndNewlines)
        let body = lines.dropFirst().joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        return (title.isEmpty ? nil : title, body)
    }

    private func normalizedReasoningText(_ body: String) -> String {
        body
            .components(separatedBy: .newlines)
            .map { line in
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                if trimmed.hasPrefix("**"), trimmed.hasSuffix("**"), trimmed.count > 4 {
                    return String(trimmed.dropFirst(2).dropLast(2))
                }
                return line
            }
            .joined(separator: "\n")
    }

    private var assistantSegmentsForRendering: [MessageRenderCache.AssistantSegment] {
        renderCache.assistantSegments(
            for: message,
            key: renderRevisionKey
        )
    }

    private var systemParseResultForRendering: ToolCallParseResult {
        renderCache.systemParseResult(
            for: message,
            key: renderRevisionKey,
            resolveTargetLabel: resolveTargetLabel
        )
    }
}

// MARK: - Litter Textual Styles

struct LitterHeadingStyle: StructuredText.HeadingStyle {
    let fontScales: [CGFloat]
    let topMargins: [CGFloat]
    let bottomMargins: [CGFloat]

    func makeBody(configuration: Configuration) -> some View {
        let level = min(configuration.headingLevel, 3) - 1
        let scale = level < fontScales.count ? fontScales[level] : 1.0
        let top = level < topMargins.count ? topMargins[level] : 8
        let bottom = level < bottomMargins.count ? bottomMargins[level] : 4

        configuration.label
            .textual.fontScale(scale)
            .fontWeight(level == 0 ? .bold : .semibold)
            .foregroundStyle(LitterTheme.textPrimary)
            .textual.blockSpacing(.init(top: top, bottom: bottom))
    }
}

struct LitterBlockQuoteStyle: StructuredText.BlockQuoteStyle {
    let topBottom: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(LitterTheme.textSecondary)
            .italic()
            .padding(.leading, 12)
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(LitterTheme.border)
                    .frame(width: 3)
            }
            .textual.blockSpacing(.init(top: topBottom, bottom: topBottom))
    }
}

struct LitterCodeBlockStyle: StructuredText.CodeBlockStyle {
    func makeBody(configuration: Configuration) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            configuration.label
                .monospaced()
                .textual.textSelection(.enabled)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(LitterTheme.codeBackground.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .modifier(GlassRectModifier(cornerRadius: 8))
        .textual.blockSpacing(.init(top: 8, bottom: 8))
    }
}

struct LitterSystemCodeBlockStyle: StructuredText.CodeBlockStyle {
    func makeBody(configuration: Configuration) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            configuration.label
                .monospaced()
                .textual.textSelection(.enabled)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(LitterTheme.codeBackground.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .modifier(GlassRectModifier(cornerRadius: 8))
        .textual.blockSpacing(.init(top: 6, bottom: 6))
    }
}

struct LitterThematicBreakStyle: StructuredText.ThematicBreakStyle {
    let topBottom: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        Divider()
            .overlay(LitterTheme.border)
            .textual.blockSpacing(.init(top: topBottom, bottom: topBottom))
    }
}

struct LitterListItemStyle: StructuredText.ListItemStyle {
    let topBottom: CGFloat
    @ScaledMetric(relativeTo: .body) private var markerColumnWidth: CGFloat = 16

    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .litterFirstTextCenter, spacing: 8) {
            configuration.marker
                .frame(width: markerColumnWidth, alignment: .center)
            configuration.block
        }
        .textual.blockSpacing(.init(top: topBottom, bottom: topBottom))
    }
}

struct LitterStructuredStyle: StructuredText.Style {
    let bodySize: CGFloat
    let codeSize: CGFloat

    var inlineStyle: InlineStyle {
        InlineStyle()
            .code(
                .monospaced,
                .fontScale(codeSize / bodySize),
                .foregroundColor(LitterTheme.textPrimary),
                .backgroundColor(LitterTheme.surfaceLight)
            )
            .strong(.fontWeight(.semibold), .foregroundColor(LitterTheme.textPrimary))
            .emphasis(.italic)
            .link(.foregroundColor(LitterTheme.accent))
    }

    var headingStyle: LitterHeadingStyle {
        LitterHeadingStyle(
            fontScales: [1.43, 1.21, 1.07],
            topMargins: [16, 12, 10],
            bottomMargins: [8, 6, 4]
        )
    }

    var paragraphStyle: StructuredText.DefaultParagraphStyle { .default }

    var blockQuoteStyle: LitterBlockQuoteStyle {
        LitterBlockQuoteStyle(topBottom: 8)
    }

    var codeBlockStyle: LitterCodeBlockStyle {
        LitterCodeBlockStyle()
    }

    var listItemStyle: LitterListItemStyle {
        LitterListItemStyle(topBottom: 4)
    }

    var unorderedListMarker: StructuredText.SymbolListMarker { .disc }
    var orderedListMarker: StructuredText.DecimalListMarker { .decimal }
    var tableStyle: StructuredText.DefaultTableStyle { .default }
    var tableCellStyle: StructuredText.DefaultTableCellStyle { .default }

    var thematicBreakStyle: LitterThematicBreakStyle {
        LitterThematicBreakStyle(topBottom: 12)
    }
}

struct LitterSystemStructuredStyle: StructuredText.Style {
    let bodySize: CGFloat
    let codeSize: CGFloat

    var inlineStyle: InlineStyle {
        InlineStyle()
            .code(
                .monospaced,
                .fontScale(codeSize / bodySize),
                .foregroundColor(LitterTheme.textPrimary),
                .backgroundColor(LitterTheme.surfaceLight)
            )
            .strong(.fontWeight(.semibold), .foregroundColor(LitterTheme.textPrimary))
            .emphasis(.italic)
            .link(.foregroundColor(LitterTheme.accent))
    }

    var headingStyle: LitterHeadingStyle {
        LitterHeadingStyle(
            fontScales: [1.31, 1.15, 1.08],
            topMargins: [12, 10, 8],
            bottomMargins: [6, 4, 4]
        )
    }

    var paragraphStyle: StructuredText.DefaultParagraphStyle { .default }

    var blockQuoteStyle: LitterBlockQuoteStyle {
        LitterBlockQuoteStyle(topBottom: 6)
    }

    var codeBlockStyle: LitterSystemCodeBlockStyle {
        LitterSystemCodeBlockStyle()
    }

    var listItemStyle: LitterListItemStyle {
        LitterListItemStyle(topBottom: 3)
    }

    var unorderedListMarker: StructuredText.SymbolListMarker { .disc }
    var orderedListMarker: StructuredText.DecimalListMarker { .decimal }
    var tableStyle: StructuredText.DefaultTableStyle { .default }
    var tableCellStyle: StructuredText.DefaultTableCellStyle { .default }

    var thematicBreakStyle: LitterThematicBreakStyle {
        LitterThematicBreakStyle(topBottom: 8)
    }
}

// MARK: - Auto-Scaling Markdown Modifiers

private struct ScaledContentMarkdownModifier: ViewModifier {
    @Environment(\.textScale) private var textScale
    let baseBodySize: CGFloat
    let baseCodeSize: CGFloat
    let selectionEnabled: Bool

    func body(content: Content) -> some View {
        let scaledBody = baseBodySize * textScale
        let scaledCode = baseCodeSize * textScale
        let styledContent = content
            .font(.custom(LitterFont.markdownFontName, size: scaledBody))
            .foregroundStyle(LitterTheme.textBody)
            .textual.mathProperties(
                MathProperties(
                    fontName: .latinModern,
                    fontScale: 1.0,
                    textAlignment: .leading
                )
            )
            .textual.structuredTextStyle(LitterStructuredStyle(bodySize: scaledBody, codeSize: scaledCode))

        if selectionEnabled {
            styledContent.textual.textSelection(.enabled)
        } else {
            styledContent
        }
    }
}

private struct ScaledSystemMarkdownModifier: ViewModifier {
    @Environment(\.textScale) private var textScale
    let baseBodySize: CGFloat
    let baseCodeSize: CGFloat
    let selectionEnabled: Bool

    func body(content: Content) -> some View {
        let scaledBody = baseBodySize * textScale
        let scaledCode = baseCodeSize * textScale
        let styledContent = content
            .font(.custom(LitterFont.markdownFontName, size: scaledBody))
            .foregroundStyle(LitterTheme.textSystem)
            .textual.mathProperties(
                MathProperties(
                    fontName: .latinModern,
                    fontScale: 1.0,
                    textAlignment: .leading
                )
            )
            .textual.structuredTextStyle(LitterSystemStructuredStyle(bodySize: scaledBody, codeSize: scaledCode))

        if selectionEnabled {
            styledContent.textual.textSelection(.enabled)
        } else {
            styledContent
        }
    }
}

extension View {
    func litterContentMarkdown(
        bodySize: CGFloat = LitterFont.conversationBodyPointSize,
        codeSize: CGFloat = LitterFont.conversationBodyPointSize,
        selectionEnabled: Bool = true
    ) -> some View {
        modifier(
            ScaledContentMarkdownModifier(
                baseBodySize: bodySize,
                baseCodeSize: codeSize,
                selectionEnabled: selectionEnabled
            )
        )
    }

    func litterSystemMarkdown(
        bodySize: CGFloat = LitterFont.conversationBodyPointSize,
        codeSize: CGFloat = LitterFont.conversationBodyPointSize,
        selectionEnabled: Bool = true
    ) -> some View {
        modifier(
            ScaledSystemMarkdownModifier(
                baseBodySize: bodySize,
                baseCodeSize: codeSize,
                selectionEnabled: selectionEnabled
            )
        )
    }
}

#if DEBUG
#Preview("Message Bubbles") {
    LitterPreviewScene {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                ForEach(LitterPreviewData.sampleMessages) { message in
                    MessageBubbleView(
                        message: message,
                        serverId: LitterPreviewData.sampleServer.id
                    )
                }
            }
            .padding(16)
        }
    }
}
#endif
