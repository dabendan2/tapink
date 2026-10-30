// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TapInkCore",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "TapInkCore",
            targets: ["TapInkCore"]
        )
    ],
    targets: [
        .target(
            name: "TapInkCore",
            path: "Sources/TapInkCore"
        ),
        .testTarget(
            name: "TapInkCoreTests",
            dependencies: ["TapInkCore"],
            path: "Tests/TapInkCoreTests"
        )
    ]
)
