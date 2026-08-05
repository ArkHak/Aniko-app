import SwiftUI
import UIKit
import ComposeApp

/// Мост между SwiftUI и Compose Multiplatform.
/// `MainViewControllerKt.MainViewController()` — это `composeApp/src/iosMain/.../MainViewController.kt`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
