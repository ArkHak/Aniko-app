import SwiftUI
import UIKit
import ComposeApp

/// Регистрация фоновых задач (P10.T1).
///
/// `BGTaskScheduler.register` обязан быть вызван до возврата из
/// `application(_:didFinishLaunchingWithOptions:)` — иначе система бросает
/// `NSInternalInconsistencyException`. SwiftUI-`App` сам по себе такой точки не даёт, поэтому
/// здесь заведён `AppDelegate` через `@UIApplicationDelegateAdaptor`.
///
/// Идентификатор задачи и требования к `Info.plist` — см. KDoc `IosBackgroundSyncScheduler`
/// (`shared/data/src/iosMain/.../sync/IosBackgroundSyncScheduler.kt`).
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BackgroundSyncRegistrationKt.registerBackgroundSyncTasks()
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                // Deep links (P10.T7): срабатывает и на холодном старте (запуск по ссылке), и пока
                // приложение уже открыто — SwiftUI сам решает эту развилку, отдельно её
                // обрабатывать не нужно. `DeepLinkEntryKt.handleDeepLinkUrl` — экспорт
                // composeApp/src/iosMain/.../DeepLinkEntry.kt, кладёт URL в `DeepLinkDispatcher`
                // (commonMain), который слушает `AnixAppScaffold` в `App.kt`.
                .onOpenURL { url in
                    DeepLinkEntryKt.handleDeepLinkUrl(url: url.absoluteString)
                }
        }
    }
}
