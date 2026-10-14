import ComposeApp
import SwiftUI
import UIKit

/// The bottom bar's tabs (M4-M8; since M3-M0b Home · Map · My trips · Alerts · Profile), as Swift names them. The order, and everything about what a tab
/// shows, is the shared code's (`AppTab` in composeApp); this only maps each one to an SF Symbol and a
/// stable string for the stored selection.
enum BahrTab: String, CaseIterable {
    case home, map, mytrips, alerts, profile

    var kotlin: AppTab {
        switch self {
        case .home: return .home
        case .map: return .map
        case .mytrips: return .mytrips
        case .alerts: return .alerts
        case .profile: return .profile
        }
    }

    init(_ tab: AppTab) {
        switch tab {
        case .map: self = .map
        case .mytrips: self = .mytrips
        case .alerts: self = .alerts
        case .profile: self = .profile
        default: self = .home
        }
    }

    /// SF Symbols, the iOS counterpart of the Android bar's Material Symbols (the handoff's `home`, `map`,
    /// `confirmation_number`, `notifications`, `person`).
    var symbol: String {
        switch self {
        case .home: return "house"
        case .map: return "map"
        case .mytrips: return "ticket"
        case .alerts: return "bell"
        case .profile: return "person"
        }
    }

    /// Selected: the filled form, as on Android.
    var selectedSymbol: String { symbol + ".fill" }

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
    /// What VoiceOver says for each tab: its name, with the unread count inside (handoff §1).
    @Published private(set) var spokenLabels: [String] = []
    /// Each tab's badge ("3", "9+"), "" for none. None until M3-B9 brings the unread count.
    @Published private(set) var badges: [String] = []
    /// Profile → Appearance (M3-M0b): light (the default), dark, or nil to follow the phone. The whole
    /// window takes it, so the bar and every tab's Compose root agree.
    @Published private(set) var colorScheme: ColorScheme? = .light
    /// The selected tab's colour, from the shared theme. Nil: the system accent.
    @Published private(set) var selectedTintColor: UIColor?
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

    /// The bar's accent for the selected item (one colour for every tab since M3-M0b, as on Android).
    var selectedTint: Color? { selectedTintColor.map { Color(uiColor: $0) } }

    /// The tab's badge, or nil for none (SwiftUI draws no badge for nil).
    func badge(_ tab: BahrTab) -> Text? {
        guard badges.indices.contains(tab.index), !badges[tab.index].isEmpty else { return nil }
        return Text(badges[tab.index])
    }

    /// The label VoiceOver reads for [tab]: the badge is not read on its own (handoff §1).
    func spokenLabel(_ tab: BahrTab) -> String {
        spokenLabels.indices.contains(tab.index) ? spokenLabels[tab.index] : title(tab)
    }

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

    func showContent(
        languageTag: String,
        rightToLeft: Bool,
        titles: [String],
        spokenLabels: [String],
        badges: [String],
        selectedTint: UIColor,
        unselectedTint: UIColor
    ) {
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
        if self.spokenLabels != spokenLabels { self.spokenLabels = spokenLabels }
        if self.badges != badges { self.badges = badges }
        if self.selectedTintColor != selectedTint { self.selectedTintColor = selectedTint }
    }

    func selectTab(tab: AppTab) { show(BahrTab(tab)) }

    /// Profile → Appearance (M3-M0b). Called with the stored value before any tab draws, then on each pick.
    /// SwiftUI's `preferredColorScheme` alone may keep the window's last forced style when it goes back
    /// to nil, so the windows' own `overrideUserInterfaceStyle` is set as well, `.unspecified` for "Same
    /// as phone" (M3-M0b review R4-5): every tab's Compose root reads its trait collection from it.
    func applyAppearance(appearance: String) {
        let scheme: ColorScheme? = appearance == "dark" ? .dark : appearance == "light" ? .light : nil
        if colorScheme != scheme { colorScheme = scheme }
        Self.applyWindowStyle(scheme)
    }

    /// The window style for [scheme]; also run when the window first appears (the first call comes
    /// before SwiftUI has made it).
    static func applyWindowStyle(_ scheme: ColorScheme?) {
        let style: UIUserInterfaceStyle = scheme == .dark ? .dark : scheme == .light ? .light : .unspecified
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .forEach { $0.overrideUserInterfaceStyle = style }
    }

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
