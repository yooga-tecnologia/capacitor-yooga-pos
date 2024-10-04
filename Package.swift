// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorYoogaPos",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "CapacitorYoogaPos",
            targets: ["CapacitorYoogaPosPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "CapacitorYoogaPosPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CapacitorYoogaPosPlugin"),
        .testTarget(
            name: "CapacitorYoogaPosPluginTests",
            dependencies: ["CapacitorYoogaPosPlugin"],
            path: "ios/Tests/CapacitorYoogaPosPluginTests")
    ]
)