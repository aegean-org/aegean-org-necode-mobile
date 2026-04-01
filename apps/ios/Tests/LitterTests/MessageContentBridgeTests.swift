import XCTest
@testable import Litter

final class MessageContentBridgeTests: XCTestCase {
    func testNormalizedAssistantMarkdownConvertsBackslashMathDelimiters() {
        let text = """
        Hello, LaTeX.

        \\[
        e^{i\\pi} + 1 = 0
        \\]
        """

        let normalized = MessageContentBridge.normalizedAssistantMarkdown(text)

        XCTAssertEqual(
            normalized,
            """
            Hello, LaTeX.

            ```math
            e^{i\\pi} + 1 = 0
            ```
            """
        )
    }

    func testNormalizedAssistantMarkdownPreservesFencedCode() {
        let text = """
        ```tex
        \\[
        e^{i\\pi} + 1 = 0
        \\]
        ```
        """

        XCTAssertEqual(MessageContentBridge.normalizedAssistantMarkdown(text), text)
    }
}
