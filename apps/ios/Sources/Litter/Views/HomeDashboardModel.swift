import Foundation
import Observation

@MainActor
@Observable
final class HomeDashboardModel {
    private struct Snapshot {
        let connectedServers: [HomeDashboardServer]
        let recentSessions: [HomeDashboardRecentSession]
        let sessionSummaries: [AppSessionSummary]
    }

    private(set) var connectedServers: [HomeDashboardServer] = []
    /// Home list source: pinned threads first (in pin order), followed by
    /// recent unpinned sessions. Hidden threads are always excluded.
    private(set) var recentSessions: [HomeDashboardRecentSession] = []
    /// Every session we know about across connected servers, newest first —
    /// used by the search view so the user can pick any thread.
    private(set) var allSessions: [HomeDashboardRecentSession] = []
    private(set) var pinnedKeys: [SavedThreadsStore.PinnedKey] = []
    private(set) var hiddenKeys: [SavedThreadsStore.PinnedKey] = []
    private(set) var projects: [AppProject] = []

    var selectedServerId: String? {
        didSet {
            if oldValue != selectedServerId {
                SavedProjectStore.selectedServerId = selectedServerId
                if selectedServerId != nil {
                    userClearedSelection = false
                }
                reconcileSelectedProject()
            }
        }
    }

    /// In-memory selection. May be a project derived from sessions, or a
    /// synthetic `(server, cwd)` pair the user just picked via the directory
    /// picker (which hasn't produced a thread yet, so it's not in `projects`).
    var selectedProject: AppProject? {
        didSet {
            if oldValue?.id != selectedProject?.id {
                SavedProjectStore.selectedProjectId = selectedProject?.id
            }
        }
    }

    @ObservationIgnored private weak var appModel: AppModel?
    @ObservationIgnored private(set) var rebuildCount = 0
    @ObservationIgnored private var isActive = false
    @ObservationIgnored private var observationGeneration = 0
    @ObservationIgnored private var lastSessionSummaries: [AppSessionSummary] = []
    /// Debounces rapid snapshot changes (e.g. the flood of store events
    /// during `listThreads` loads) so we don't rebuild the home list
    /// hundreds of times per second.
    @ObservationIgnored private var debouncedRefreshTask: Task<Void, Never>?
    /// Set by the UI when the user intentionally clears the server filter
    /// so the snapshot reconciler doesn't re-select a default server.
    private var userClearedSelection = false
    @ObservationIgnored private var preferencesObserver: NSObjectProtocol?
    @ObservationIgnored private var savedServersObserver: NSObjectProtocol?

    init() {
        selectedServerId = SavedProjectStore.selectedServerId
        pinnedKeys = SavedThreadsStore.pinnedKeys()
        hiddenKeys = SavedThreadsStore.hiddenKeys()
        preferencesObserver = NotificationCenter.default.addObserver(
            forName: .litterThreadPreferencesDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.reloadThreadPreferences()
                self.refreshState()
            }
        }
        savedServersObserver = NotificationCenter.default.addObserver(
            forName: .litterSavedServersDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.refreshState()
            }
        }
    }

    deinit {
        if let preferencesObserver {
            NotificationCenter.default.removeObserver(preferencesObserver)
        }
        if let savedServersObserver {
            NotificationCenter.default.removeObserver(savedServersObserver)
        }
    }

    /// Add a thread to the home list. No-op if already pinned.
    func pinThread(_ key: ThreadKey) {
        let pin = SavedThreadsStore.PinnedKey(threadKey: key)
        guard !pinnedKeys.contains(pin) else { return }
        SavedThreadsStore.add(pin)
        pinnedKeys = SavedThreadsStore.pinnedKeys()
        // Pinning cancels a prior hide.
        if hiddenKeys.contains(pin) {
            SavedThreadsStore.unhide(pin)
            hiddenKeys = SavedThreadsStore.hiddenKeys()
        }
        refreshState()
    }

    func unpinThread(_ key: ThreadKey) {
        let pin = SavedThreadsStore.PinnedKey(threadKey: key)
        SavedThreadsStore.remove(pin)
        pinnedKeys = SavedThreadsStore.pinnedKeys()
        refreshState()
    }

    func hideThread(_ key: ThreadKey) {
        let pin = SavedThreadsStore.PinnedKey(threadKey: key)
        SavedThreadsStore.hide(pin)
        hiddenKeys = SavedThreadsStore.hiddenKeys()
        // Hide removes from pinned too (Rust enforces this); mirror here.
        pinnedKeys = SavedThreadsStore.pinnedKeys()
        refreshState()
    }

    func unhideThread(_ key: ThreadKey) {
        let pin = SavedThreadsStore.PinnedKey(threadKey: key)
        SavedThreadsStore.unhide(pin)
        hiddenKeys = SavedThreadsStore.hiddenKeys()
        refreshState()
    }

    func isPinned(_ key: ThreadKey) -> Bool {
        pinnedKeys.contains(SavedThreadsStore.PinnedKey(threadKey: key))
    }

    func handleRemovedServer(_ serverId: String) {
        if selectedServerId == serverId {
            clearScope()
        } else if selectedProject?.serverId == serverId {
            selectedProject = nil
        }

        connectedServers.removeAll { $0.id == serverId }
        recentSessions.removeAll { $0.serverId == serverId }
        allSessions.removeAll { $0.serverId == serverId }
        projects.removeAll { $0.serverId == serverId }
    }

    /// Clear the active scope so the tasks list shows sessions from every
    /// connected server.
    func clearScope() {
        userClearedSelection = true
        selectedServerId = nil
        selectedProject = nil
    }

    func bind(appModel: AppModel) {
        self.appModel = appModel
        guard isActive else { return }
        refreshState()
    }

    func activate() {
        guard !isActive else { return }
        isActive = true
        refreshState()
    }

    func deactivate() {
        guard isActive else { return }
        isActive = false
        observationGeneration &+= 1
        debouncedRefreshTask?.cancel()
        debouncedRefreshTask = nil
    }

    /// Coalesce rapid observation-triggered refreshes. Direct callers
    /// (activate, bind, pin/unpin/hide) still go straight to `refreshState`
    /// so user actions feel immediate.
    private func scheduleObservedRefresh() {
        debouncedRefreshTask?.cancel()
        debouncedRefreshTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 120_000_000)
            guard let self, !Task.isCancelled, self.isActive else { return }
            self.refreshState()
        }
    }

    private func refreshState() {
        guard isActive, let appModel else {
            connectedServers = []
            recentSessions = []
            projects = []
            return
        }

        reloadThreadPreferences()
        observationGeneration &+= 1
        let generation = observationGeneration
        let snapshot = withObservationTracking {
            let appSnapshot = appModel.snapshot
            let nextConnectedServers = HomeDashboardSupport.sortedConnectedServers(
                from: appSnapshot?.servers ?? [],
                savedServers: SavedServerStore.rememberedServers(),
                activeServerId: appSnapshot?.activeThread?.serverId
            )
            let nextAllSessions = HomeDashboardSupport.recentConnectedSessions(
                from: appSnapshot?.sessionSummaries ?? [],
                serversById: Dictionary(uniqueKeysWithValues: nextConnectedServers
                    .filter(\.canLaunchSessions)
                    .map { ($0.id, $0) }),
                limit: nil
            )
            return Snapshot(
                connectedServers: nextConnectedServers,
                recentSessions: nextAllSessions,
                sessionSummaries: appSnapshot?.sessionSummaries ?? []
            )
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, self.isActive, self.observationGeneration == generation else { return }
                self.scheduleObservedRefresh()
            }
        }

        rebuildCount += 1
        connectedServers = snapshot.connectedServers
        allSessions = snapshot.recentSessions
        recentSessions = Self.mergedHomeSessions(
            pinned: pinnedKeys,
            hidden: hiddenKeys,
            connectedServers: snapshot.connectedServers,
            allSessions: snapshot.recentSessions
        )
        lastSessionSummaries = snapshot.sessionSummaries
        projects = deriveProjects(sessions: snapshot.sessionSummaries)

        // Keep selectedServerId valid: if the server it points at isn't in
        // the live/launchable list, clear the scope. Default is no filter —
        // we do not auto-select the first connected server.
        if let current = selectedServerId,
           !connectedServers.contains(where: { $0.id == current && $0.canLaunchSessions }) {
            selectedServerId = nil
        }

        reconcileSelectedProject()
    }

    private func reloadThreadPreferences() {
        pinnedKeys = SavedThreadsStore.pinnedKeys()
        hiddenKeys = SavedThreadsStore.hiddenKeys()
    }

    private func reconcileSelectedProject() {
        guard let serverId = selectedServerId else {
            selectedProject = nil
            return
        }

        let serverProjects = projects.filter { $0.serverId == serverId }

        // Preserve user's current pick if it matches this server (even if it
        // isn't in the derived list yet, e.g. a freshly-picked directory).
        if let current = selectedProject, current.serverId == serverId {
            if let refreshed = serverProjects.first(where: { $0.id == current.id }) {
                selectedProject = refreshed
            }
            return
        }

        if let persistedId = SavedProjectStore.selectedProjectId,
           let match = serverProjects.first(where: { $0.id == persistedId }) {
            selectedProject = match
            return
        }

        selectedProject = serverProjects.first
    }

    /// Merge rule:
    /// - Pinned sessions appear first in pin order.
    /// - Recent unpinned sessions fill the remaining home slots.
    /// - Keep at least 10 visible slots, while preserving every pin when the
    ///   user has pinned more than 10 sessions.
    /// - Hidden threads are always excluded.
    private static func mergedHomeSessions(
        pinned: [SavedThreadsStore.PinnedKey],
        hidden: [SavedThreadsStore.PinnedKey],
        connectedServers: [HomeDashboardServer],
        allSessions: [HomeDashboardRecentSession]
    ) -> [HomeDashboardRecentSession] {
        let hiddenSet = Set(hidden)
        let candidates = allSessions.filter {
            !hiddenSet.contains(SavedThreadsStore.PinnedKey(threadKey: $0.key))
        }
        let byKey = Dictionary(uniqueKeysWithValues: candidates.map {
            (SavedThreadsStore.PinnedKey(threadKey: $0.key), $0)
        })
        let serversById = Dictionary(uniqueKeysWithValues: connectedServers.map { ($0.id, $0) })
        let pinnedSessions = pinned.compactMap { pin in
            if let existing = byKey[pin] {
                return existing
            }
            guard !hiddenSet.contains(pin), let server = serversById[pin.serverId] else {
                return nil
            }
            return placeholderPinnedSession(for: pin.threadKey, server: server)
        }
        let pinnedSet = Set(pinnedSessions.map { SavedThreadsStore.PinnedKey(threadKey: $0.key) })
        let recentUnpinned = candidates.filter {
            !pinnedSet.contains(SavedThreadsStore.PinnedKey(threadKey: $0.key))
        }
        let visibleLimit = max(10, pinnedSessions.count)
        return Array((pinnedSessions + recentUnpinned).prefix(visibleLimit))
    }

    private static func placeholderPinnedSession(
        for key: ThreadKey,
        server: HomeDashboardServer
    ) -> HomeDashboardRecentSession {
        HomeDashboardRecentSession(
            key: key,
            serverId: key.serverId,
            serverDisplayName: server.displayName,
            agentRuntimeKind: server.agentRuntimes.first(where: \.available)?.kind
                ?? AgentRuntimeMetadataProvider.all?().first?.name
                ?? "",
            isLocal: server.isLocal,
            sessionTitle: "正在加载会话",
            preview: "",
            cwd: "",
            model: "",
            agentLabel: nil,
            updatedAt: Date(timeIntervalSince1970: 0),
            hasTurnActive: false,
            isResumed: false,
            isSubagent: false,
            isFork: false,
            forkedFromId: nil,
            lineage: nil,
            lastResponsePreview: nil,
            lastResponseTurnId: nil,
            lastUserMessage: nil,
            lastToolLabel: nil,
            stats: nil,
            tokenUsage: nil,
            goal: nil,
            recentToolLog: [],
            lastTurnStart: nil,
            lastTurnEnd: nil
        )
    }

    /// Called when the user picks a fresh directory via the "new project"
    /// flow. The (server, cwd) may have no threads yet, so we synthesize the
    /// project locally and select it. It will appear in `projects` naturally
    /// once the first thread is created.
    func selectFreshProject(serverId: String, cwd: String) {
        selectedServerId = serverId
        let id = projectIdFor(serverId: serverId, cwd: cwd)
        if let existing = projects.first(where: { $0.id == id }) {
            selectedProject = existing
        } else {
            selectedProject = AppProject(
                id: id,
                serverId: serverId,
                cwd: cwd,
                lastUsedAtMs: nil
            )
        }
    }
}
