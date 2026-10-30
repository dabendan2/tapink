#if canImport(UIKit)
import UIKit
import UniformTypeIdentifiers

/// iOS Action/Share Extension receiving `public.image` from Photos, Canva, Figma, or Safari,
/// writing the PNG to the App Group container (`group.com.dabendan.tapink`), and deep-linking
/// into `tapink://share` to immediately launch the CoreNFC Tag Reader sheet.
public final class ShareViewController: UIViewController {

    private static let appGroupId = "group.com.dabendan.tapink"
    private static let sharedImageFilename = "shared_epaper_input.png"

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.10, green: 0.11, blue: 0.13, alpha: 0.92)
        handleIncomingSharedItems()
    }

    private func handleIncomingSharedItems() {
        guard let extensionItems = extensionContext?.inputItems as? [NSExtensionItem] else {
            completeExtension()
            return
        }

        let imageTypeIdentifier = UTType.image.identifier
        for item in extensionItems {
            guard let attachments = item.attachments else { continue }
            for provider in attachments {
                if provider.hasItemConformingToTypeIdentifier(imageTypeIdentifier) {
                    provider.loadItem(forTypeIdentifier: imageTypeIdentifier, options: nil) { [weak self] itemData, _ in
                        guard let self = self else { return }
                        if let image = self.extractUIImage(from: itemData) {
                            self.saveImageToAppGroupAndOpenHostApp(image)
                        } else {
                            self.completeExtension()
                        }
                    }
                    return
                }
            }
        }
        completeExtension()
    }

    private func extractUIImage(from itemData: NSSecureCoding?) -> UIImage? {
        if let url = itemData as? URL, let data = try? Data(contentsOf: url), let img = UIImage(data: data) {
            return img
        }
        if let data = itemData as? Data, let img = UIImage(data: data) {
            return img
        }
        if let img = itemData as? UIImage {
            return img
        }
        return nil
    }

    private func saveImageToAppGroupAndOpenHostApp(_ image: UIImage) {
        if let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupId),
           let pngData = image.pngData() {
            let fileURL = containerURL.appendingPathComponent(Self.sharedImageFilename)
            try? pngData.write(to: fileURL, options: .atomic)
        }

        DispatchQueue.main.async {
            if let shareURL = URL(string: "tapink://share") {
                self.openURLInHostApp(shareURL)
            }
            self.completeExtension()
        }
    }

    @objc private func openURL(_ url: URL) -> Bool {
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                return true
            }
            responder = current.next
        }
        return false
    }

    private func openURLInHostApp(_ url: URL) {
        _ = openURL(url)
    }

    private func completeExtension() {
        DispatchQueue.main.async {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
    }
}
#endif
