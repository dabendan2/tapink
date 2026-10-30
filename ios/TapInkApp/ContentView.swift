#if canImport(UIKit) && canImport(CoreNFC)
import SwiftUI
import PhotosUI
import CoreNFC
import UIKit
import TapInkCore

public enum TapInkUiState: String {
    case preparing = "PREPARING"
    case nfcDisabled = "NFC_DISABLED"
    case waitingNfc = "WAITING_NFC"
    case transferring = "TRANSFERRING"
    case success = "SUCCESS"
    case error = "ERROR"
}

public final class TapInkViewModel: NSObject, ObservableObject, NFCTagReaderSessionDelegate {

    public static let appGroupId = "group.com.dabendan.tapink"
    public static let sharedImageFilename = "shared_epaper_input.png"

    @Published public var uiState: TapInkUiState = .preparing
    @Published public var headline: String = NSLocalizedString("ui_pick_image_headline", comment: "")
    @Published public var subtitle: String = NSLocalizedString("ui_pick_image_subtitle", comment: "")
    @Published public var isLandscapePreferred: Bool = false
    @Published public var previewImage: UIImage?
    @Published public var hardwareSpecBadge: String?
    @Published public var progressPercent: Int = 0
    @Published public var selectedPhotoItem: PhotosPickerItem? {
        didSet {
            if let item = selectedPhotoItem {
                loadFromPhotosPicker(item)
            }
        }
    }

    private var rawSourceImage: UIImage?
    private var orientedSourceImage: UIImage?
    private var nfcSession: NFCTagReaderSession?
    private let workerQueue = DispatchQueue(label: "com.dabendan.tapink.nfc.worker", qos: .userInitiated)
    private var isTransferringLock = false

    public override init() {
        super.init()
        if !loadSharedImageFromAppGroupIfPresent(autoStartNfc: false) {
            let sample = Self.generateDefaultSampleArtwork()
            applyLoadedImage(sample, autoStartNfc: false)
        }
    }

    public func handleIncomingURL(_ url: URL) {
        if url.scheme == "tapink" {
            _ = loadSharedImageFromAppGroupIfPresent(autoStartNfc: true)
        }
    }

    @discardableResult
    public func loadSharedImageFromAppGroupIfPresent(autoStartNfc: Bool) -> Bool {
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupId) else {
            return false
        }
        let fileURL = containerURL.appendingPathComponent(Self.sharedImageFilename)
        guard FileManager.default.fileExists(atPath: fileURL.path),
              let data = try? Data(contentsOf: fileURL),
              let image = UIImage(data: data) else {
            return false
        }
        try? FileManager.default.removeItem(at: fileURL)
        applyLoadedImage(image, autoStartNfc: autoStartNfc)
        return true
    }

    private func loadFromPhotosPicker(_ item: PhotosPickerItem) {
        Task {
            if let data = try? await item.loadTransferable(type: Data.self),
               let image = UIImage(data: data) {
                await MainActor.run {
                    self.applyLoadedImage(image, autoStartNfc: false)
                }
            } else {
                await MainActor.run {
                    self.uiState = .error
                    self.headline = NSLocalizedString("ui_err_image_headline", comment: "")
                    self.subtitle = NSLocalizedString("ui_err_image_subtitle", comment: "")
                }
            }
        }
    }

    public func applyLoadedImage(_ image: UIImage, autoStartNfc: Bool) {
        rawSourceImage = image
        isLandscapePreferred = image.size.width > image.size.height
        orientedSourceImage = image
        hardwareSpecBadge = nil
        progressPercent = 0

        if let (preview, _) = Self.processUIImageForSpec(
            source: image,
            hwWidth: 400,
            hwHeight: 300,
            colorMode: .bwry4Color
        ) {
            previewImage = preview
        } else {
            previewImage = image
        }

        uiState = .waitingNfc
        headline = NSLocalizedString("ui_ready_headline", comment: "")
        subtitle = NSLocalizedString("ui_ready_subtitle", comment: "")

        if autoStartNfc {
            startCoreNfcScan()
        }
    }

    public func setOrientation(landscape: Bool) {
        guard let raw = rawSourceImage, isLandscapePreferred != landscape else { return }
        isLandscapePreferred = landscape
        let rawIsLandscape = raw.size.width > raw.size.height
        let oriented: UIImage
        if rawIsLandscape == landscape {
            oriented = raw
        } else {
            oriented = Self.rotate90(raw)
        }
        orientedSourceImage = oriented
        if let (preview, _) = Self.processUIImageForSpec(
            source: oriented,
            hwWidth: 400,
            hwHeight: 300,
            colorMode: .bwry4Color
        ) {
            previewImage = preview
        }
    }

    public func startCoreNfcScan() {
        guard NFCTagReaderSession.readingAvailable else {
            uiState = .nfcDisabled
            headline = NSLocalizedString("ui_nfc_disabled_headline", comment: "")
            subtitle = NSLocalizedString("ui_nfc_disabled_subtitle", comment: "")
            return
        }
        nfcSession?.invalidate()
        guard let session = NFCTagReaderSession(pollingOption: [.iso14443], delegate: self, queue: nil) else {
            uiState = .error
            headline = NSLocalizedString("ui_err_reader_headline", comment: "")
            subtitle = NSLocalizedString("ui_err_reader_subtitle", comment: "")
            return
        }
        session.alertMessage = NSLocalizedString("ui_ready_subtitle", comment: "")
        nfcSession = session
        uiState = .waitingNfc
        headline = NSLocalizedString("ui_ready_headline", comment: "")
        subtitle = NSLocalizedString("ui_ready_subtitle", comment: "")
        session.begin()
    }

    /// App Store Reviewer Hardware-Free Demo Mode (triggered by 1-second long-press on the E-Paper preview card).
    public func startSimulatedDemoTransfer() {
        let sim = SimulatedIsoDepTransceiver(
            mode: SimulatedIsoDepTransceiver.modeSuccess420BWRY,
            busyPollCountBeforeIdle: 4
        )
        runTransfer(transceiver: sim, isSimulated: true, session: nil)
    }

    private func runTransfer(
        transceiver: IsoDepTransceiver,
        isSimulated: Bool,
        session: NFCTagReaderSession?
    ) {
        guard let srcImage = orientedSourceImage ?? rawSourceImage else { return }
        if isTransferringLock { return }
        isTransferringLock = true

        DispatchQueue.main.async {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            self.progressPercent = 1
            self.uiState = .transferring
            self.headline = NSLocalizedString("ui_transferring_headline", comment: "")
            self.subtitle = NSLocalizedString("ui_transferring_subtitle", comment: "")
        }

        workerQueue.async { [weak self] in
            guard let self = self else { return }
            let refreshWaitMs: Int64 = isSimulated ? 150 : 2000
            let refreshPollMs: Int64 = isSimulated ? 150 : 500

            do {
                let detectedCfg = try TapInkProtocol.executeDynamicUpdate(
                    transceiver: transceiver,
                    refreshInitialWaitMs: refreshWaitMs,
                    refreshPollIntervalMs: refreshPollMs,
                    refreshTimeoutMs: 28000,
                    imageProvider: { cfg in
                        guard let (ditheredImg, ditherRes) = Self.processUIImageForSpec(
                            source: srcImage,
                            hwWidth: cfg.normWidth,
                            hwHeight: cfg.normHeight,
                            colorMode: cfg.colorMode
                        ) else {
                            throw TapInkProtocolError(message: "無法將圖片轉換為 \(cfg.modelSummary) 格式")
                        }
                        DispatchQueue.main.async {
                            self.previewImage = ditheredImg
                            self.hardwareSpecBadge = self.formatHardwarePillLabel(cfg)
                        }
                        return ditherRes.packedHardwareBytes
                    },
                    onProgress: { _, _, pct, rawMsg in
                        DispatchQueue.main.async {
                            if pct > self.progressPercent {
                                self.progressPercent = pct
                            }
                            session?.alertMessage = "\(pct)% — \(rawMsg)"
                        }
                    }
                )

                DispatchQueue.main.async {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    self.progressPercent = 100
                    self.uiState = .success
                    self.headline = NSLocalizedString("ui_success_headline", comment: "")
                    self.subtitle = detectedCfg.modelSummary
                    session?.alertMessage = NSLocalizedString("ui_success_headline", comment: "")
                    session?.invalidate()
                    self.isTransferringLock = false
                }
            } catch {
                let rawErr = error.localizedDescription
                let (errHead, errSub) = self.translateErrorForConsumer(rawErr)
                DispatchQueue.main.async {
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                    self.uiState = .error
                    self.headline = errHead
                    self.subtitle = errSub
                    session?.invalidate(errorMessage: "\(errHead): \(errSub)")
                    self.isTransferringLock = false
                }
            }
            transceiver.close()
        }
    }

    private func formatHardwarePillLabel(_ cfg: TapInkProtocol.HardwareConfig) -> String {
        let inchLabel: String
        if cfg.normWidth == 400 && cfg.normHeight == 300 {
            inchLabel = "4.2\""
        } else if cfg.normWidth == 296 && cfg.normHeight == 128 {
            inchLabel = "2.9\""
        } else if cfg.normWidth == 250 && cfg.normHeight == 122 {
            inchLabel = "2.13\""
        } else if cfg.normWidth == 416 && cfg.normHeight == 240 {
            inchLabel = "3.7\""
        } else if cfg.normWidth == 200 && cfg.normHeight == 200 {
            inchLabel = "1.54\""
        } else if cfg.normWidth == 600 && cfg.normHeight == 400 {
            inchLabel = "3.6\""
        } else {
            inchLabel = "\(cfg.normWidth)×\(cfg.normHeight)"
        }
        let colorKey: String
        switch cfg.colorMode {
        case .bw2Color: colorKey = "ui_color_bw"
        case .bwr3Color: colorKey = "ui_color_bwr"
        case .bwry4Color: colorKey = "ui_color_bwry"
        case .spectra6Color: colorKey = "ui_color_spectra6"
        }
        return "\(inchLabel) \(NSLocalizedString(colorKey, comment: ""))"
    }

    private func translateErrorForConsumer(_ rawError: String) -> (String, String) {
        if rawError.contains("感應斷開") || rawError.contains("Tag was lost") || rawError.contains("通訊中斷") || rawError.contains("刷新期間") {
            return (
                NSLocalizedString("ui_err_disconnected_headline", comment: ""),
                NSLocalizedString("ui_err_disconnected_subtitle", comment: "")
            )
        }
        if rawError.contains("PIN") || rawError.contains("6982") || rawError.contains("色碼") || rawError.contains("規格") || rawError.contains("TLV") {
            return (
                NSLocalizedString("ui_err_unsupported_headline", comment: ""),
                NSLocalizedString("ui_err_unsupported_subtitle", comment: "")
            )
        }
        return (
            NSLocalizedString("ui_err_disconnected_headline", comment: ""),
            NSLocalizedString("ui_err_disconnected_subtitle", comment: "")
        )
    }

    // MARK: - NFCTagReaderSessionDelegate

    public func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    public func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        DispatchQueue.main.async {
            if self.uiState == .transferring {
                self.uiState = .error
                self.headline = NSLocalizedString("ui_err_disconnected_headline", comment: "")
                self.subtitle = NSLocalizedString("ui_err_disconnected_subtitle", comment: "")
                self.isTransferringLock = false
            }
        }
    }

    public func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let firstTag = tags.first else { return }
        if tags.count > 1 {
            session.PollingRestart()
            return
        }
        guard case let .iso7816(iso7816Tag) = firstTag else {
            let errHead = NSLocalizedString("ui_err_unsupported_headline", comment: "")
            let errSub = NSLocalizedString("ui_err_unsupported_subtitle", comment: "")
            session.invalidate(errorMessage: "\(errHead): \(errSub)")
            DispatchQueue.main.async {
                self.uiState = .error
                self.headline = errHead
                self.subtitle = errSub
            }
            return
        }

        let transceiver = CoreNfcIsoDepTransceiver(tag: iso7816Tag, session: session)
        runTransfer(transceiver: transceiver, isSimulated: false, session: session)
    }

    // MARK: - Image Processing Helpers

    public static func processUIImageForSpec(
        source: UIImage,
        hwWidth: Int,
        hwHeight: Int,
        colorMode: FloydSteinbergDitherer.ColorMode
    ) -> (UIImage, FloydSteinbergDitherer.DitherResult)? {
        let longSide = max(hwWidth, hwHeight)
        let shortSide = min(hwWidth, hwHeight)
        let srcW = source.size.width
        let srcH = source.size.height
        guard srcW > 0 && srcH > 0 else { return nil }
        let isPortrait = srcH > srcW

        let targetW = isPortrait ? shortSide : longSide
        let targetH = isPortrait ? longSide : shortSide

        // 1. Render UIImage via UIGraphicsImageRenderer (Top-Left origin, respects EXIF UIImage.imageOrientation)
        let scale = max(CGFloat(targetW) / srcW, CGFloat(targetH) / srcH)
        let drawW = srcW * scale
        let drawH = srcH * scale
        let dx = (CGFloat(targetW) - drawW) * 0.5
        let dy = (CGFloat(targetH) - drawH) * 0.5

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: targetW, height: targetH), format: format)
        let normalizedUIImage = renderer.image { rendererCtx in
            UIColor.white.setFill()
            rendererCtx.fill(CGRect(x: 0, y: 0, width: targetW, height: targetH))
            rendererCtx.cgContext.interpolationQuality = .none
            source.draw(in: CGRect(x: dx, y: dy, width: drawW, height: drawH))
        }

        guard let normalizedCgImage = normalizedUIImage.cgImage else { return nil }
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bytesPerPixel = 4
        let bytesPerRow = targetW * bytesPerPixel
        var rawRgba = [UInt8](repeating: 255, count: targetW * targetH * bytesPerPixel)

        guard let context = CGContext(
            data: &rawRgba,
            width: targetW,
            height: targetH,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        ) else {
            return nil
        }

        // CGContext.draw(cgImage, in:) maps CGImage row 0 (top) to rawRgba row 0 (top)
        context.interpolationQuality = .none
        context.draw(normalizedCgImage, in: CGRect(x: 0, y: 0, width: targetW, height: targetH))

        let totalPixels = targetW * targetH
        var argbPixels = [UInt32](repeating: 0xFFFFFFFF, count: totalPixels)
        for i in 0..<totalPixels {
            let offset = i * 4
            let r = UInt32(rawRgba[offset])
            let g = UInt32(rawRgba[offset + 1])
            let b = UInt32(rawRgba[offset + 2])
            let a = UInt32(rawRgba[offset + 3])
            argbPixels[i] = (a << 24) | (r << 16) | (g << 8) | b
        }

        let ditherResult = FloydSteinbergDitherer.ditherAndPackPixels(
            width: targetW,
            height: targetH,
            argbPixels: argbPixels,
            colorMode: colorMode,
            hwWidth: longSide,
            hwHeight: shortSide
        )

        var outRgba = [UInt8](repeating: 255, count: totalPixels * 4)
        for i in 0..<totalPixels {
            let argb = ditherResult.ditheredArgb[i]
            let offset = i * 4
            outRgba[offset] = UInt8((argb >> 16) & 0xFF)
            outRgba[offset + 1] = UInt8((argb >> 8) & 0xFF)
            outRgba[offset + 2] = UInt8(argb & 0xFF)
            outRgba[offset + 3] = UInt8((argb >> 24) & 0xFF)
        }

        guard let outContext = CGContext(
            data: &outRgba,
            width: targetW,
            height: targetH,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        ), let outCgImage = outContext.makeImage() else {
            return nil
        }

        return (UIImage(cgImage: outCgImage, scale: 1.0, orientation: .up), ditherResult)
    }

    private static func rotate90(_ image: UIImage) -> UIImage {
        let newSize = CGSize(width: image.size.height, height: image.size.width)
        UIGraphicsBeginImageContextWithOptions(newSize, true, 1.0)
        guard let ctx = UIGraphicsGetCurrentContext() else { return image }
        ctx.translateBy(x: newSize.width / 2, y: newSize.height / 2)
        ctx.rotate(by: .pi / 2)
        image.draw(in: CGRect(
            x: -image.size.width / 2,
            y: -image.size.height / 2,
            width: image.size.width,
            height: image.size.height
        ))
        let rotated = UIGraphicsGetImageFromCurrentImageContext() ?? image
        UIGraphicsEndImageContext()
        return rotated
    }

    private static func generateDefaultSampleArtwork() -> UIImage {
        let size = CGSize(width: 400, height: 300)
        UIGraphicsBeginImageContextWithOptions(size, true, 1.0)
        let ctx = UIGraphicsGetCurrentContext()!

        // Warm Ivory background
        UIColor(red: 0.965, green: 0.957, blue: 0.933, alpha: 1.0).setFill()
        ctx.fill(CGRect(origin: .zero, size: size))

        // Amber sun
        UIColor(red: 0.949, green: 0.718, blue: 0.020, alpha: 1.0).setFill()
        ctx.fillEllipse(in: CGRect(x: 220, y: 36, width: 96, height: 96))

        // Crimson mountains
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 0, y: 300))
        path.addLine(to: CGPoint(x: 130, y: 145))
        path.addLine(to: CGPoint(x: 235, y: 225))
        path.addLine(to: CGPoint(x: 310, y: 165))
        path.addLine(to: CGPoint(x: 400, y: 300))
        path.close()
        UIColor(red: 0.851, green: 0.220, blue: 0.161, alpha: 1.0).setFill()
        path.fill()

        // Carbon foreground ridge
        let ridge = UIBezierPath()
        ridge.move(to: CGPoint(x: 0, y: 300))
        ridge.addLine(to: CGPoint(x: 85, y: 205))
        ridge.addLine(to: CGPoint(x: 195, y: 300))
        ridge.close()
        UIColor(red: 0.110, green: 0.118, blue: 0.129, alpha: 1.0).setFill()
        ridge.fill()

        let img = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return img
    }
}

private extension NFCTagReaderSession {
    func PollingRestart() {
        restartPolling()
    }
}

public struct ContentView: View {
    @ObservedObject var viewModel: TapInkViewModel

    public init(viewModel: TapInkViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            Color(red: 0.06, green: 0.07, blue: 0.09)
                .ignoresSafeArea()

            VStack(spacing: 18) {
                Capsule()
                    .fill(Color.white.opacity(0.35))
                    .frame(width: 36, height: 4)
                    .padding(.top, 10)

                // Zone 1: Header
                VStack(spacing: 6) {
                    Text(viewModel.headline)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundColor(Color(red: 0.89, green: 0.89, blue: 0.91))
                        .multilineTextAlignment(.center)
                        .accessibilityIdentifier("status_text")

                    if !viewModel.subtitle.isEmpty {
                        Text(viewModel.subtitle)
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.77, green: 0.78, blue: 0.82))
                            .multilineTextAlignment(.center)
                            .accessibilityIdentifier("status_subtitle")
                    }
                }
                .padding(.horizontal, 20)

                // Zone 2: Hero E-Paper Preview Card (Long-press 1s triggers App Store Reviewer Demo Mode)
                ZStack {
                    VStack(spacing: 12) {
                        ZStack(alignment: .bottomTrailing) {
                            if let preview = viewModel.previewImage {
                                Image(uiImage: preview)
                                    .interpolation(.none)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(maxWidth: 210, maxHeight: 210)
                                    .padding(10)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color(red: 0.20, green: 0.21, blue: 0.23))
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .stroke(borderColorForState, lineWidth: viewModel.uiState == .error ? 2 : 1.5)
                                    )
                                    .onLongPressGesture(minimumDuration: 1.0) {
                                        viewModel.startSimulatedDemoTransfer()
                                    }
                                    .accessibilityIdentifier("dither_preview")
                            }

                            if viewModel.uiState == .success {
                                Text("✓")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(Color(red: 0.02, green: 0.18, blue: 0.44))
                                    .frame(width: 36, height: 36)
                                    .background(Circle().fill(Color(red: 0.66, green: 0.78, blue: 0.98)))
                                    .offset(x: 8, y: 8)
                            }
                        }

                        if let specBadge = viewModel.hardwareSpecBadge {
                            Text(specBadge)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(red: 0.66, green: 0.78, blue: 0.98))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(Color(red: 0.12, green: 0.16, blue: 0.23)))
                        } else if viewModel.uiState == .transferring {
                            ProgressView(value: Double(viewModel.progressPercent), total: 100.0)
                                .tint(Color(red: 0.66, green: 0.78, blue: 0.98))
                                .frame(width: 180)
                        }
                    }

                    if viewModel.uiState == .error {
                        Text(NSLocalizedString("ui_error_overlay_pill", comment: ""))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(red: 1.0, green: 0.85, blue: 0.84))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(Color(red: 0.58, green: 0.0, blue: 0.04))
                                    .overlay(Capsule().stroke(Color(red: 1.0, green: 0.71, blue: 0.67), lineWidth: 1))
                            )
                    }
                }
                .frame(height: 256)

                // Zone 3: Controls & Action Footer
                VStack(spacing: 12) {
                    HStack(spacing: 12) {
                        // Orientation Segmented Toggle
                        HStack(spacing: 4) {
                            Button(action: { viewModel.setOrientation(landscape: false) }) {
                                Text(NSLocalizedString("ui_btn_portrait", comment: ""))
                                    .font(.system(size: 13, weight: .medium))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule().fill(!viewModel.isLandscapePreferred
                                            ? Color(red: 0.24, green: 0.28, blue: 0.35)
                                            : Color.clear)
                                    )
                                    .foregroundColor(!viewModel.isLandscapePreferred ? .white : .gray)
                            }
                            Button(action: { viewModel.setOrientation(landscape: true) }) {
                                Text(NSLocalizedString("ui_btn_landscape", comment: ""))
                                    .font(.system(size: 13, weight: .medium))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule().fill(viewModel.isLandscapePreferred
                                            ? Color(red: 0.24, green: 0.28, blue: 0.35)
                                            : Color.clear)
                                    )
                                    .foregroundColor(viewModel.isLandscapePreferred ? .white : .gray)
                            }
                        }
                        .padding(3)
                        .background(
                            Capsule()
                                .fill(Color(red: 0.13, green: 0.15, blue: 0.17))
                                .overlay(Capsule().stroke(Color(red: 0.27, green: 0.28, blue: 0.31), lineWidth: 1))
                        )

                        Spacer()

                        PhotosPicker(
                            selection: $viewModel.selectedPhotoItem,
                            matching: .images,
                            photoLibrary: .shared()
                        ) {
                            Label(NSLocalizedString("ui_picker_chooser_title", comment: ""), systemImage: "photo.on.rectangle")
                                .font(.system(size: 14, weight: .medium))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(Capsule().fill(Color(red: 0.20, green: 0.22, blue: 0.26)))
                                .foregroundColor(.white)
                        }
                    }

                    Button(action: {
                        viewModel.startCoreNfcScan()
                    }) {
                        HStack {
                            Image(systemName: "wave.3.right.circle.fill")
                            Text(NSLocalizedString("share_action_label", comment: ""))
                                .font(.system(size: 16, weight: .bold))
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Capsule().fill(Color(red: 0.66, green: 0.78, blue: 0.98)))
                        .foregroundColor(Color(red: 0.02, green: 0.18, blue: 0.44))
                    }
                    .accessibilityIdentifier("scan_nfc_button")
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 28)
            }
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Color(red: 0.10, green: 0.11, blue: 0.13))
                    .ignoresSafeArea(edges: .bottom)
            )
        }
    }

    private var borderColorForState: Color {
        switch viewModel.uiState {
        case .error:
            return Color(red: 1.0, green: 0.71, blue: 0.67)
        case .success:
            return Color(red: 0.66, green: 0.78, blue: 0.98)
        default:
            return Color(red: 0.27, green: 0.28, blue: 0.31)
        }
    }
}
#endif
