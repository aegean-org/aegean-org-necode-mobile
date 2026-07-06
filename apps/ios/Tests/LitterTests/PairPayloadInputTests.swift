import XCTest
@testable import Litter

final class PairPayloadInputTests: XCTestCase {
    func testNormalizedPairPayloadRemovesBomAndMarkdownFence() {
        let raw = "\u{feff}```json\n{\"v\":1,\"node_id\":\"abc\",\"token\":\"secret\"}\n```"

        XCTAssertEqual(
            PairPayloadInput.normalized(raw),
            "{\"v\":1,\"node_id\":\"abc\",\"token\":\"secret\"}"
        )
    }

    func testNormalizedPairPayloadLeavesJsonTextUnchangedExceptOuterWhitespace() {
        let raw = "  {\"v\":1,\"node_id\":\"abc\",\"token\":\"secret\"}\n"

        XCTAssertEqual(
            PairPayloadInput.normalized(raw),
            "{\"v\":1,\"node_id\":\"abc\",\"token\":\"secret\"}"
        )
    }
}
