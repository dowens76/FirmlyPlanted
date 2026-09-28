import Shared
import SwiftUI
import UIKit

/// Hosts the shared Compose UI (see shared/src/iosMain/.../MainViewController.kt). The whole app
/// lives on the Kotlin side; this file and iOSApp.swift are the only Swift in the project.
struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose's Scaffolds handle status-bar, home-indicator and keyboard insets themselves.
            .ignoresSafeArea()
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
