import SwiftUI

extension AppServerHealth {
    var displayLabel: String {
        switch self {
        case .connected:
            return "已连接"
        case .connecting:
            return "连接中..."
        case .unresponsive:
            return "无响应"
        case .disconnected:
            return "已断开"
        case .unknown:
            return "未知"
        }
    }

    var accentColor: Color {
        switch self {
        case .connected:
            return LitterTheme.accent
        case .connecting, .unresponsive:
            return .orange
        case .disconnected, .unknown:
            return LitterTheme.textSecondary
        }
    }
}

extension AppServerTransportState {
    var displayLabel: String {
        switch self {
        case .connected:
            return "已连接"
        case .connecting:
            return "连接中..."
        case .unresponsive:
            return "无响应"
        case .disconnected:
            return "已断开"
        case .unknown:
            return "未知"
        }
    }

    var accentColor: Color {
        switch self {
        case .connected:
            return LitterTheme.accent
        case .connecting, .unresponsive:
            return .orange
        case .disconnected, .unknown:
            return LitterTheme.textSecondary
        }
    }
}
