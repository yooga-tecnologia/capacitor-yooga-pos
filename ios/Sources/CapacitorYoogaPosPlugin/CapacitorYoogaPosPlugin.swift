import Foundation
import Capacitor

/**
 * Hardware-only plugin: o iOS não tem display traseiro / impressora térmica
 * Elgin, então estes métodos apenas resolvem como no-op para que o app
 * Capacitor que roda em iOS não quebre ao chamar a mesma API que usa em
 * Android.
 */
@objc(CapacitorYoogaPosPlugin)
public class CapacitorYoogaPosPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapacitorYoogaPosPlugin"
    public let jsName = "CapacitorYoogaPos"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "showLogoOnDisplay", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "showPix", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "print", returnType: CAPPluginReturnPromise)
    ]

    @objc func showLogoOnDisplay(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func showPix(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func print(_ call: CAPPluginCall) {
        call.resolve()
    }
}
