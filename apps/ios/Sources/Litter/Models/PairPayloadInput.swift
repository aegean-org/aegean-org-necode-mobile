import Foundation

/// Normalizes pasted/scanned pairing payload text before it crosses into Rust.
enum PairPayloadInput {
    static func normalized(_ raw: String) -> String {
        let text = trimmedWithoutBom(raw)
        return strippedMarkdownFence(text)
    }

    private static func trimmedWithoutBom(_ raw: String) -> String {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.unicodeScalars.first?.value == 0xfeff {
            text.removeFirst()
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func strippedMarkdownFence(_ text: String) -> String {
        let lines = text.components(separatedBy: .newlines)
        guard lines.count >= 3,
              isOpeningFence(lines[0]),
              isClosingFence(lines[lines.count - 1]) else {
            return text
        }
        return lines
            .dropFirst()
            .dropLast()
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func isOpeningFence(_ line: String) -> Bool {
        let normalized = line.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return normalized == "```" || normalized == "```json"
    }

    private static func isClosingFence(_ line: String) -> Bool {
        line.trimmingCharacters(in: .whitespacesAndNewlines) == "```"
    }
}
