import Foundation
import SwiftUI

struct DesktopSessionAttention: Decodable {
    let status: String
    let label: String
    let permissions: [DesktopPermissionRequest]
    let questions: [DesktopQuestionRequest]
    static let idle = DesktopSessionAttention(status: "idle", label: "", permissions: [], questions: [])
    var count: Int { permissions.count + questions.count }
}

struct DesktopPermissionRequest: Decodable, Identifiable {
    let id: String
    let permission: String
    let patterns: [String]
    let requestSource: String
    var identity: String { "\(requestSource)|\(id)" }
}
struct DesktopQuestionRequest: Decodable, Identifiable {
    let id: String
    let requestSource: String
    let questions: [DesktopQuestionInfo]
    var identity: String { "\(requestSource)|\(id)" }
}
struct DesktopQuestionInfo: Decodable {
    let question: String
    let options: [DesktopQuestionOption]
    let multiple: Bool?
    let custom: Bool?
}
struct DesktopQuestionOption: Decodable {
    let label: String
    let description: String
}

struct DesktopAttentionSheet: View {
    let state: DesktopSessionAttention
    let busy: Bool
    let onReply: (String, [String: Any]) -> Void
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("需要你处理").litterFont(.title2, weight: .semibold)
                ForEach(state.permissions, id: \.identity) { request in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(request.permission).litterFont(.headline)
                        Text(request.patterns.joined(separator: "\n")).litterFont(.footnote)
                            .foregroundColor(LitterTheme.textSecondary).textSelection(.enabled)
                        HStack {
                            ForEach(["once", "always", "reject"], id: \.self) { reply in
                                Button(reply == "once" ? "允许一次" : reply == "always" ? "始终允许" : "拒绝") {
                                    onReply("/permission/\(request.id)/reply", ["reply": reply, "requestSource": request.requestSource])
                                }.frame(minHeight: 44)
                            }
                        }.disabled(busy)
                    }
                    Divider()
                }
                ForEach(state.questions, id: \.identity) { request in
                    DesktopQuestionForm(request: request, busy: busy, onReply: onReply).id(request.identity)
                    Divider()
                }
                if state.count == 0 { Text("当前没有待处理事项").foregroundColor(LitterTheme.textSecondary) }
            }.padding(20)
        }.foregroundColor(LitterTheme.textPrimary).tint(LitterTheme.accentStrong)
            .presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
            .presentationBackground(LitterTheme.background)
    }
}

private struct DesktopQuestionForm: View {
    let request: DesktopQuestionRequest
    let busy: Bool
    let onReply: (String, [String: Any]) -> Void
    @State private var selected: [Int: [String]] = [:]
    @State private var custom: [Int: String] = [:]
    private var answers: [[String]] {
        request.questions.indices.map { index in
            let text = (custom[index] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return (selected[index] ?? []) + (text.isEmpty ? [] : [text])
        }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(request.questions.indices, id: \.self) { index in
                let question = request.questions[index]
                Text(question.question).litterFont(.headline)
                ForEach(question.options, id: \.label) { option in
                    Button {
                        let existing = selected[index] ?? []
                        if question.multiple == true {
                            selected[index] = existing.contains(option.label) ? existing.filter { $0 != option.label } : existing + [option.label]
                        } else {
                            selected[index] = [option.label]
                            custom[index] = ""
                        }
                    } label: {
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: (selected[index] ?? []).contains(option.label) ? "checkmark.circle.fill" : "circle")
                            VStack(alignment: .leading, spacing: 4) {
                                Text(option.label)
                                Text(option.description).litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                        }.padding(.vertical, 8)
                    }.disabled(busy)
                }
                if question.custom != false {
                    TextField("其他回答", text: Binding(get: { custom[index] ?? "" }, set: {
                        custom[index] = $0
                        if question.multiple != true { selected[index] = [] }
                    })).textFieldStyle(.roundedBorder).disabled(busy)
                }
            }
            HStack {
                Button("提交回答") {
                    onReply("/question/\(request.id)/reply", ["answers": answers, "requestSource": request.requestSource])
                }.disabled(busy || answers.contains(where: { $0.isEmpty }))
                Button("暂不回答", role: .destructive) {
                    onReply("/question/\(request.id)/reject", ["requestSource": request.requestSource])
                }.disabled(busy)
            }.frame(minHeight: 44)
        }
    }
}
