import SwiftUI
import WatchKit
import UserNotifications

/// Root @main for the Litter Watch app. Vertically paginated TabView
/// makes the three hero surfaces reachable via crown/swipe.
@main
struct LitterWatchApp: App {
    @StateObject private var store = WatchAppStore.shared

    init() {
        WatchSessionBridge.shared.start()
        Self.scheduleNextBackgroundRefresh()
    }

    var body: some Scene {
        WindowGroup {
            WatchRootView()
                .environmentObject(store)
                .preferredColorScheme(.dark)
                .tint(WatchTheme.ginger)
        }
        .backgroundTask(.appRefresh("litter.watch.refresh")) {
            await Self.handleBackgroundRefresh()
        }

        WKNotificationScene(
            controller: LitterNotificationController.self,
            category: "litter.task.complete"
        )
    }

    /// Re-arm the periodic background refresh and request a fresh snapshot
    /// from the phone. Called on app launch and from the background-task
    /// handler.
    fileprivate static func scheduleNextBackgroundRefresh() {
        let next = Date().addingTimeInterval(15 * 60)
        WKApplication.shared().scheduleBackgroundRefresh(
            withPreferredDate: next,
            userInfo: nil
        ) { _ in }
    }

    @MainActor
    fileprivate static func handleBackgroundRefresh() async {
        scheduleNextBackgroundRefresh()
        WatchSessionBridge.shared.requestSnapshot()
        // Give WCSession a short window to deliver the reply before the
        // system suspends us; the inbound handler will update the store.
        try? await Task.sleep(nanoseconds: 2_000_000_000)
    }
}

/// The three-page hero loop: glance → dictate → approve.
///
/// A single root `NavigationStack` wraps the `TabView` so pushed
/// destinations (task detail, transcript, approval) replace the whole
/// pager and the native horizontal edge-swipe-back gesture works.
/// Nesting `NavigationStack` per tab page fought with the vertical
/// page tab view and broke back navigation.
struct WatchRootView: View {
    @EnvironmentObject var store: WatchAppStore
    @State private var tab: RootTab = .home
    @State private var path: [WatchTask] = []

    var body: some View {
        NavigationStack(path: $path) {
            TabView(selection: $tab) {
                HomeScreen().tag(RootTab.home)
                RealtimeVoiceScreen().tag(RootTab.voice)
                ApprovalScreen().tag(RootTab.approval)
            }
            .tabViewStyle(.verticalPage)
            .navigationDestination(for: WatchTask.self) { task in
                TaskDetailScreen(task: task)
            }
        }
        .onOpenURL { url in
            route(url)
        }
    }

    /// Parse `litter-watch://task/{taskId}` and push `TaskDetailScreen` for
    /// the matched task. Falls back to home when the task isn't in the
    /// store (e.g., complication tapped before first snapshot arrived).
    private func route(_ url: URL) {
        guard url.scheme == "litter-watch", url.host == "task" else { return }
        let taskId = url.pathComponents.dropFirst().first ?? ""
        guard !taskId.isEmpty,
              let task = store.tasks.first(where: { $0.id == taskId })
        else {
            path.removeAll()
            tab = .home
            return
        }
        tab = .home
        path = [task]
    }
}

enum RootTab: Hashable {
    case home, voice, approval
}

final class LitterNotificationController: WKUserNotificationHostingController<NotificationScreen> {
    private var currentNotification: UNNotification?

    override var body: NotificationScreen {
        NotificationScreen(notification: currentNotification)
    }

    override func didReceive(_ notification: UNNotification) {
        currentNotification = notification
    }
}
