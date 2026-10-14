import SwiftUI
import WebKit

struct NeCaptchaSheet: View {
    let config: NeCaptchaConfig
    let baseURL: String
    let completion: (String?, String?) -> Void
    @State private var completed = false

    private func finish(_ proof: String?, _ message: String?) {
        guard !completed else { return }
        completed = true
        completion(proof, message)
    }

    var body: some View {
        NavigationStack {
            NeCaptchaWebView(config: config, baseURL: baseURL, completion: finish)
                .navigationTitle("安全验证")
                .toolbar { ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { finish(nil, "验证已取消，请重试") }
                } }
        }
        .interactiveDismissDisabled()
    }
}

private struct NeCaptchaWebView: UIViewRepresentable {
    let config: NeCaptchaConfig
    let baseURL: String
    let completion: (String?, String?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(completion) }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.userContentController.add(context.coordinator, name: "neCaptcha")
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        do { view.loadHTMLString(try neCaptchaHtml(config: config), baseURL: URL(string: baseURL)) }
        catch { DispatchQueue.main.async { context.coordinator.finish(nil, "无法加载人机验证配置") } }
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) {
        coordinator.closed = true
        view.stopLoading()
        view.configuration.userContentController.removeScriptMessageHandler(forName: "neCaptcha")
        view.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        var closed = false
        private var initialDocument = true
        let completion: (String?, String?) -> Void
        init(_ completion: @escaping (String?, String?) -> Void) { self.completion = completion }
        func finish(_ proof: String?, _ message: String?) {
            guard !closed else { return }
            closed = true
            completion(proof, message)
        }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.frameInfo.isMainFrame, let raw = message.body as? String, raw.utf8.count < 131072,
                  let data = raw.data(using: .utf8),
                  let value = try? JSONSerialization.jsonObject(with: data) as? [String: String] else {
                finish(nil, "验证结果无效，请重试"); return
            }
            if value["type"] == "success", let proof = value["value"],
               !proof.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, proof.utf8.count < 65536 {
                finish(proof, nil)
            } else { finish(nil, value["type"] == "cancel" ? "验证已取消，请重试" : "人机验证未完成，请检查网络后重试") }
        }
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            // Only loadHTMLString's initial document and HTTPS vendor frames are allowed.
            if initialDocument && navigationAction.navigationType == .other {
                initialDocument = false
                decisionHandler(.allow)
                return
            }
            let frameURL = navigationAction.request.url
            let allowedFrame = frameURL?.scheme == "https" || frameURL?.absoluteString == "about:blank"
            decisionHandler(navigationAction.targetFrame?.isMainFrame == false && allowedFrame ? .allow : .cancel)
        }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            finish(nil, "无法加载人机验证，请检查网络后重试")
        }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            finish(nil, "人机验证加载失败，请重试")
        }
    }
}
