import SwiftUI
import UIKit
import SommelleShared

@main
struct SommelleApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.2.0"
        return MainViewControllerKt.MainViewController(appVersion: version)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
