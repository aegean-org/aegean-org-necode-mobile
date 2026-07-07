import XCTest
@testable import Litter

final class PairPayloadInputTests: XCTestCase {

    /// Test that normal JSON input passes through unchanged
    func testNormalJSONInput() {
        let input = #"{"v":1,"node_id":"abc123","token":"def456","host_name":"Test","relay":"https://relay.example.com"}"#
        let result = PairPayloadInput.normalized(input)
        XCTAssertEqual(result, input)
    }

    /// Test that UTF-16 LE bytes misinterpreted as UTF-16 BE are correctly fixed
    func testFixMisinterpretedUTF16() {
        // Simulate the garbled text that appears when UTF-16 LE bytes
        // are incorrectly read as UTF-16 BE characters
        let garbled = "笀∀瘀∀㨀㄀Ⰰ∀渀漀搀攀开椀搀∀㨀∀愀戀挀㄀㈀㌀∀Ⰰ∀琀漀欀攀渀∀㨀∀搀攀昀㐀㔀㘀∀紀"

        let result = PairPayloadInput.normalized(garbled)

        // Should be decoded back to proper JSON
        XCTAssertTrue(result.starts(with: "{"))
        XCTAssertTrue(result.contains("\"v\":1"))
        XCTAssertTrue(result.contains("\"node_id\""))
        XCTAssertTrue(result.contains("abc123"))
        XCTAssertTrue(result.contains("\"token\""))
        XCTAssertTrue(result.contains("def456"))
    }

    /// Test the actual garbled text from the user report
    func testUserReportedGarbledText() {
        let garbled = "笀∀瘀∀㨀㄀Ⰰ∀渀漀搀攀开椀搀∀㨀∀攀㄀挀愀攀㌀㄀搀攀㈀㐀㠀挀戀㔀㌀㜀㌀　㤀　搀搀搀㜀㠀　攀㔀㌀攀㤀挀戀㐀㔀昀㄀㄀㌀㈀戀戀㤀　㐀愀昀攀㘀㔀搀昀㈀昀㐀㈀攀㤀愀　㘀㠀㈀∀Ⰰ∀琀漀欀攀渀∀㨀∀㘀挀　挀戀㤀㜀挀㌀搀戀　㔀㄀挀㤀㠀挀㐀㠀㔀㌀愀㔀㐀㘀戀搀㔀㐀㠀昀㐀㠀㠀攀搀搀挀㠀　㘀㠀㜀攀搀㘀㔀㜀㠀㜀㜀愀搀㌀㜀㄀戀戀攀攀㤀攀昀∀Ⰰ∀栀漀猀琀开渀愀洀攀∀㨀∀齞楲孲∀Ⰰ∀爀攀氀愀礀∀㨀∀栀琀琀瀀猀㨀⼀⼀爀攀氀愀礀⸀椀渀漀琀攀攀砀瀀爀攀猀猀⸀挀漀洀∀紀"

        let result = PairPayloadInput.normalized(garbled)

        // Should decode to the expected JSON structure
        XCTAssertTrue(result.starts(with: "{"))
        XCTAssertTrue(result.contains("\"v\":1"))
        XCTAssertTrue(result.contains("\"node_id\":\"e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682\""))
        XCTAssertTrue(result.contains("\"token\":\"6c0cb97c3db051c98c4853a546bd548f488eddc80687ed657877ad371bbee9ef\""))
        XCTAssertTrue(result.contains("\"host_name\":\"废物牛\""))
        XCTAssertTrue(result.contains("\"relay\":\"https://relay.inoteexpress.com\""))
    }

    /// Test that BOM is removed
    func testBOMRemoval() {
        let inputWithBOM = "\u{FEFF}" + #"{"v":1,"node_id":"test"}"#
        let result = PairPayloadInput.normalized(inputWithBOM)
        XCTAssertTrue(result.starts(with: "{"))
        XCTAssertFalse(result.starts(with: "\u{FEFF}"))
    }

    /// Test that markdown code fences are stripped
    func testMarkdownFenceStripping() {
        let input = """
        ```json
        {"v":1,"node_id":"test"}
        ```
        """
        let result = PairPayloadInput.normalized(input)
        XCTAssertEqual(result, #"{"v":1,"node_id":"test"}"#)
    }

    /// Test JSON extraction from surrounding text
    func testJSONExtraction() {
        let input = "Here is your pairing JSON: {\"v\":1,\"node_id\":\"test\"} Please use it."
        let result = PairPayloadInput.normalized(input)
        XCTAssertEqual(result, #"{"v":1,"node_id":"test"}"#)
    }

    /// Test that whitespace is trimmed
    func testWhitespaceTrimming() {
        let input = "  \n  {\"v\":1,\"node_id\":\"test\"}  \n  "
        let result = PairPayloadInput.normalized(input)
        XCTAssertEqual(result, #"{"v":1,"node_id":"test"}"#)
    }

    /// Test combined normalization: markdown fence + whitespace + garbled UTF-16
    func testCombinedNormalization() {
        // Markdown fence around garbled UTF-16
        let input = """
        ```json
        笀∀瘀∀㨀㄀Ⰰ∀渀漀搀攀开椀搀∀㨀∀琀攀猀琀∀紀
        ```
        """
        let result = PairPayloadInput.normalized(input)
        XCTAssertTrue(result.starts(with: "{"))
        XCTAssertTrue(result.contains("\"v\":1"))
        XCTAssertTrue(result.contains("\"node_id\""))
        XCTAssertTrue(result.contains("test"))
    }
}
