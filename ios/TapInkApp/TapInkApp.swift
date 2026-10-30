#if canImport(UIKit) && canImport(CoreNFC)
import SwiftUI

@main
struct TapInkApp: App {
    @StateObject private var viewModel = TapInkViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    viewModel.handleIncomingURL(url)
                }
        }
    }
}
#endif
