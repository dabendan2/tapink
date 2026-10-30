#if canImport(UIKit) && canImport(CoreNFC)
import Foundation
import CoreNFC
import TapInkCore

/// Bridges Apple's `NFCTagReaderSession` and `NFCISO7816Tag` to `TapInkProtocol`'s synchronous `IsoDepTransceiver`
/// on a dedicated background worker queue, updating `session.alertMessage` in real time.
public final class CoreNfcIsoDepTransceiver: IsoDepTransceiver {

    private let tag: NFCISO7816Tag
    private weak var session: NFCTagReaderSession?
    private var connected: Bool = false

    public init(tag: NFCISO7816Tag, session: NFCTagReaderSession?) {
        self.tag = tag
        self.session = session
    }

    public func connect(timeoutMs: Int = 50000) throws {
        guard let session = self.session else {
            throw TapInkIOError("NFCTagReaderSession is no longer active")
        }
        let sema = DispatchSemaphore(value: 0)
        var connectError: Error?

        session.connect(to: .iso7816(self.tag)) { error in
            connectError = error
            sema.signal()
        }

        let waitResult = sema.wait(timeout: .now() + .milliseconds(timeoutMs))
        if waitResult == .timedOut {
            throw TapInkIOError("CoreNFC connect timed out (\(timeoutMs)ms)")
        }
        if let err = connectError {
            throw TapInkIOError("CoreNFC connect failed: \(err.localizedDescription)")
        }
        self.connected = true
    }

    public func isConnected() -> Bool {
        return connected && tag.isAvailable
    }

    /// Constructs an explicit ISO/IEC 7816-4 APDU from raw command bytes (`CLA INS P1 P2 [Lc/Le] [Data]`)
    /// so Case 1/2 (`00 D1 00 00`, `F0 D4 ...`, `F0 DE ...`) and Case 3 (`00 20 ...`, `F0 D3 ...`, `F0 D2 ...`)
    /// commands work deterministically across all iOS versions.
    internal static func buildIso7816Apdu(from command: [UInt8]) throws -> NFCISO7816APDU {
        guard command.count >= 4 else {
            throw TapInkIOError("APDU command too short (\(command.count) bytes)")
        }
        let cla = command[0]
        let ins = command[1]
        let p1 = command[2]
        let p2 = command[3]

        let payloadData: Data
        let expectedRespLen: Int

        if command.count == 4 {
            // Case 1 / Case 2 implicit Le (e.g. 00 D1 00 00 hardware config query expects TLV response)
            payloadData = Data()
            expectedRespLen = 256
        } else if command.count == 5 {
            // Case 2 Short APDU: [CLA, INS, P1, P2, Le] (e.g. F0 D4 <param> 80 00 or F0 DE 00 00 01)
            payloadData = Data()
            let le = Int(command[4])
            expectedRespLen = (le == 0) ? 256 : le
        } else {
            // Case 3 / Case 4 Short APDU: [CLA, INS, P1, P2, Lc, ...Data]
            let lc = Int(command[4])
            if 5 + lc == command.count {
                payloadData = Data(command[5..<command.count])
                expectedRespLen = -1
            } else if 5 + lc + 1 == command.count {
                payloadData = Data(command[5..<(5 + lc)])
                let le = Int(command[command.count - 1])
                expectedRespLen = (le == 0) ? 256 : le
            } else {
                payloadData = Data(command[5..<command.count])
                expectedRespLen = -1
            }
        }

        return NFCISO7816APDU(
            instructionClass: cla,
            instructionCode: ins,
            p1Parameter: p1,
            p2Parameter: p2,
            data: payloadData,
            expectedResponseLength: expectedRespLen
        )
    }

    public func transceive(command: [UInt8]) throws -> [UInt8] {
        guard connected else {
            throw TapInkIOError("CoreNFC channel is not connected")
        }
        let apdu = try Self.buildIso7816Apdu(from: command)

        let sema = DispatchSemaphore(value: 0)
        var responsePayload = Data()
        var respSw1: UInt8 = 0
        var respSw2: UInt8 = 0
        var sendError: Error?

        tag.sendCommand(apdu: apdu) { payload, sw1, sw2, error in
            responsePayload = payload
            respSw1 = sw1
            respSw2 = sw2
            sendError = error
            sema.signal()
        }

        let waitResult = sema.wait(timeout: .now() + .seconds(15))
        if waitResult == .timedOut {
            connected = false
            throw TapInkIOError("CoreNFC sendCommand timed out")
        }
        if let err = sendError {
            connected = false
            throw TapInkIOError(err.localizedDescription)
        }

        var fullResponse = [UInt8](responsePayload)
        fullResponse.append(respSw1)
        fullResponse.append(respSw2)
        return fullResponse
    }

    public func close() {
        connected = false
    }

    public func updateAlertMessage(_ text: String) {
        DispatchQueue.main.async { [weak session] in
            session?.alertMessage = text
        }
    }
}
#endif
