import ComposeApp
import SwiftUI
import UIKit

/// The bottom bar's tabs (M4-M8), as Swift names them. The order, and everything about what a tab
/// shows, is the shared code's (`AppTab` in composeApp); this only maps each one to an SF Symbol and a
/// stable string for the stored selection.
enum BahrTab: String, CaseIterable {
    case home, search, map, mytrips, settings

    var kotlin: AppTab {
        switch self {
        case .home: return .home
        case .search: return .search
        case .map: return .map
        case .mytrips: return .mytrips
        case .settings: return .settings
        }
    }

    init(_ tab: AppTab) {
        switch tab {
        case .search: self = .search
        case .map: self = .map
        case .mytrips: self = .mytrips
        case .settings: self = .settings
        default: self = .home
        }
    }

    /// SF Symbols, the iOS counterpart of the Android bar's Material Symbols.
    var symbol: String {
        switch self {
        case .home: return "house"
        case .search: return "magnifyingglass"
        case .map: return "map"
        case .mytrips: return "suitcase"
        case .settings: return "gearshape"
        }
    }

    /// Selected: the filled form, as on Android. The magnifying glass has no filled form.
    var selectedSymbol: String { self == .search ? symbol : symbol + ".fill" }

    /// The order the shared code sends titles in (`AppTab.entries`).
    var index: Int { BahrTab.allCases.firstIndex(of: self) ?? 0 }
}

/// What the SwiftUI `TabView` shows, driven by the shared code through `AppTabBar` (M4-M8). Swift
/// decides nothing here beyond remembering the selected tab across launches: labels, language and
/// direction, each tab's colour, when the bar hides (a pushed screen) and programmatic tab switches all come from Kotlin.
/// The shared code calls it on the main thread.
final class TabBarModel: NSObject, ObservableObject, AppTabBar {
    static let shared = TabBarModel()

    /// Where the selected tab is kept, so a relaunch after the system ended the app in the background
    /// comes back on the same tab. The language is kept by the shared code (DataStore).
    private static let selectedKey = "bahr.selectedTab"

    @Published private(set) var selected: BahrTab
    /// Empty until a Compose root has reported them; the bar stays hidden until then.
    @Published private(set) var titles: [String] = []
    @Published private(set) var languageTag = "ar"
    @Published private(set) var rightToLeft = true
    /// Each tab's colour when selected, from the shared theme's tab tones. Empty: the system accent.
    @Published private(set) var tints: [UIColor] = []
    /// Home starts on splash, which is not a tab root.
    @Published private(set) var hiddenOn: Set<BahrTab> = [.home]

    private override init() {
        let stored = UserDefaults.standard.string(forKey: Self.selectedKey)
        selected = stored.flatMap(BahrTab.init(rawValue:)) ?? .home
        super.init()
    }

    func title(_ tab: BahrTab) -> String { titles.indices.contains(tab.index) ? titles[tab.index] : "" }

    /// The tab's icon. Selected: the filled symbol as a template, which the bar tints with the tab's
    /// colour. Not selected: the outlined symbol already drawn in the muted colour (`.alwaysOriginal`),
    /// because the iOS 26 glass bar keeps its own near-black for unselected template icons whatever
    /// `unselectedItemTintColor` or the item appearance's `iconColor` say (M4-M8 review #10); the
    /// label does take the appearance's colour.
    func icon(_ tab: BahrTab) -> Image {
        if tab == selected { return Image(systemName: tab.selectedSymbol) }
        guard let muted = unselectedTint,
              let symbol = UIImage(systemName: tab.symbol)?.withTintColor(muted, renderingMode: .alwaysOriginal)
        else { return Image(systemName: tab.symbol) }
        return Image(uiImage: symbol)
    }

    /// The bar's accent: the selected tab's own colour. The iOS 26 glass bar tints every selected item
    /// with one accent (there is no per-item tint), so the colour follows the selection instead.
    var selectedTint: Color? { tints.indices.contains(selected.index) ? Color(uiColor: tints[selected.index]) : nil }

    func isBarHidden(_ tab: BahrTab) -> Bool { titles.isEmpty || hiddenOn.contains(tab) }

    /// The `TabView`'s selection. Its setter also runs for a tap on the tab already selected, which is
    /// how a re-tap reaches the shared code (back to the tab's root, or to the top of its list).
    var selection: Binding<BahrTab> {
        Binding(
            get: { self.selected },
            set: { tapped in
                self.show(tapped)
                MainViewControllerKt.onTabTapped(tab: tapped.kotlin)
            }
        )
    }

    /// A link from outside opens in the Home tab's stack. The shared code switches to Home too; doing it
    /// here as well means Home's root exists to open it even when another tab was restored at launch.
    func showHomeForLink() { show(.home) }

    private func show(_ tab: BahrTab) {
        guard tab != selected else { return }
        selected = tab
        UserDefaults.standard.set(tab.rawValue, forKey: Self.selectedKey)
    }

    // MARK: AppTabBar (called by the shared code)

    func showContent(languageTag: String, rightToLeft: Bool, titles: [String], tints: [UIColor], unselectedTint: UIColor) {
        // The tabs not selected, muted (as on Android). Their icons are drawn already muted (`icon(_:)`);
        // their labels take the colour from the bar's item appearance. SwiftUI's TabView has no modifier
        // for that, and its UITabBar exists before this first call (hiding the bar does not delay it), so
        // the live bar is set (M4-M8 review #1).
        if self.unselectedTint != unselectedTint {
            self.unselectedTint = unselectedTint
            Self.muteLabels(unselectedTint)
        }
        if self.languageTag != languageTag { self.languageTag = languageTag }
        if self.rightToLeft != rightToLeft { self.rightToLeft = rightToLeft }
        if self.titles != titles { self.titles = titles }
        if self.tints != tints { self.tints = tints }
    }

    func selectTab(tab: AppTab) { show(BahrTab(tab)) }

    /// The muted colour of the tabs not selected (the shared theme's onSurfaceVariant).
    @Published private(set) var unselectedTint: UIColor?

    /// Every tab bar on screen gets [color] for the labels of its unselected items. Labels only: the
    /// iOS 26 glass bar ignores `unselectedItemTintColor` and the appearance's `iconColor` for icons,
    /// which is why `icon(_:)` draws them muted itself (M4-M8 review #10, #12).
    private static func muteLabels(_ color: UIColor) {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        windows.flatMap { tabBars(in: $0) }.forEach { bar in
            let appearance = bar.standardAppearance.copy()
            for layout in [appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance, appearance.compactInlineLayoutAppearance] {
                layout.normal.titleTextAttributes = [.foregroundColor: color]
            }
            bar.standardAppearance = appearance
            bar.scrollEdgeAppearance = appearance
        }
    }

    private static func tabBars(in view: UIView) -> [UITabBar] {
        (view as? UITabBar).map { [$0] } ?? view.subviews.flatMap { tabBars(in: $0) }
    }

    func setBarHidden(tab: AppTab, hidden: Bool) {
        let swiftTab = BahrTab(tab)
        if hidden { hiddenOn.insert(swiftTab) } else { hiddenOn.remove(swiftTab) }
    }
}

/// One tab's Compose root: its own NavHost over the one Koin graph (`startApp` ran at launch).
struct TabRoot: UIViewControllerRepresentable {
    let tab: BahrTab

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.TabViewController(tab: tab.kotlin)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// iOS 26: the Liquid Glass bar shrinks while content scrolls down. SwiftUI only sees scrolling in its
/// own scroll views; the tabs' content is Compose (no UIScrollView), so in practice the bar stays full
/// size. Kept so it applies as soon as SwiftUI can observe the scroll.
struct MinimizesOnScroll: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            content
        }
    }
}
