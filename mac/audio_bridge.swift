// audio_bridge.swift - Mac 原生 CoreAudio 音频直通与实时监听中继桥
// 支持：
// 1. 物理扬声器/耳机零延迟实时监听输出 (CoreAudio DefaultOutputDevice)
// 2. BlackHole 2ch 虚拟麦克风系统级音频注入 (供 Siri/微信/腾讯会议/Zoom 使用)
// 3. HTTP 实时音频广播流服务 (端口 9530，供 Web 上位机浏览器实时监听)
// 4. TCP/UDP 双协议低延迟接收 (端口 9528，支持 USB 直连与 Wi-Fi 局域网广播)

import Foundation
import CoreAudio
import AudioToolbox
import Darwin

setbuf(stdout, nil)
setbuf(stderr, nil)

let PORT: UInt16 = 9528
let HTTP_STREAM_PORT: UInt16 = 9530
let BUFFER_CAPACITY = 48000 * 2 // 2秒 48kHz 缓冲区
let MIC_DEMAND_FILE = "/tmp/machud_mic_demand"

print("╔═══════════════════════════════════════════════════════╗")
print("║  🎙️ Mac 原生 CoreAudio 音频实时直通与监听中继桥启动  ║")
print("╚═══════════════════════════════════════════════════════╝")

// ============================================================
// 1. 线程安全环形音频缓冲区 (RingBuffer)
// ============================================================
final class RingBuffer {
    private var buffer: [Float32]
    private var writeIndex = 0
    private var readIndex = 0
    private let capacity: Int
    private let lock = os_unfair_lock_t.allocate(capacity: 1)

    init(capacity: Int) {
        self.capacity = capacity
        self.buffer = [Float32](repeating: 0, count: capacity)
        self.lock.initialize(to: os_unfair_lock())
    }

    func write(samples: UnsafePointer<Int16>, count: Int) {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        for i in 0..<count {
            buffer[writeIndex] = Float32(samples[i]) / 32768.0
            writeIndex = (writeIndex + 1) % capacity
            if writeIndex == readIndex {
                readIndex = (readIndex + 1) % capacity
            }
        }
    }

    func read(into outSamples: UnsafeMutablePointer<Float32>, count: Int) {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        for i in 0..<count {
            if readIndex != writeIndex {
                outSamples[i] = buffer[readIndex]
                readIndex = (readIndex + 1) % capacity
            } else {
                outSamples[i] = 0.0
            }
        }
    }

    func clear() {
        os_unfair_lock_lock(lock)
        defer { os_unfair_lock_unlock(lock) }
        readIndex = 0
        writeIndex = 0
    }
}

let speakerRingBuffer = RingBuffer(capacity: BUFFER_CAPACITY)
let blackHoleRingBuffer = RingBuffer(capacity: BUFFER_CAPACITY)

// ============================================================
// 2. CoreAudio 设备查找与注册
// ============================================================

// 查找默认物理输出设备 (MacBook Pro 扬声器 / 耳机)
func findDefaultOutputDeviceID() -> (AudioDeviceID?, String) {
    var propertySize = UInt32(MemoryLayout<AudioDeviceID>.size)
    var address = AudioObjectPropertyAddress(
        mSelector: kAudioHardwarePropertyDefaultOutputDevice,
        mScope: kAudioObjectPropertyScopeGlobal,
        mElement: kAudioObjectPropertyElementMain
    )
    var id: AudioDeviceID = 0
    guard AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &propertySize, &id) == noErr else {
        return (nil, "未知")
    }

    var nameSize: UInt32 = 256
    var name = [CChar](repeating: 0, count: 256)
    var nameAddress = AudioObjectPropertyAddress(
        mSelector: kAudioDevicePropertyDeviceName,
        mScope: kAudioObjectPropertyScopeGlobal,
        mElement: kAudioObjectPropertyElementMain
    )
    AudioObjectGetPropertyData(id, &nameAddress, 0, nil, &nameSize, &name)
    return (id, String(cString: name))
}

// 查找 BlackHole 2ch 虚拟声卡
func findBlackHoleDeviceID() -> AudioDeviceID? {
    var propertySize: UInt32 = 0
    var address = AudioObjectPropertyAddress(
        mSelector: kAudioHardwarePropertyDevices,
        mScope: kAudioObjectPropertyScopeGlobal,
        mElement: kAudioObjectPropertyElementMain
    )
    guard AudioObjectGetPropertyDataSize(AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &propertySize) == noErr else { return nil }
    let count = Int(propertySize) / MemoryLayout<AudioDeviceID>.size
    var ids = [AudioDeviceID](repeating: 0, count: count)
    guard AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &propertySize, &ids) == noErr else { return nil }

    for id in ids {
        var nameSize: UInt32 = 256
        var name = [CChar](repeating: 0, count: 256)
        var nameAddress = AudioObjectPropertyAddress(
            mSelector: kAudioDevicePropertyDeviceName,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        if AudioObjectGetPropertyData(id, &nameAddress, 0, nil, &nameSize, &name) == noErr {
            let devName = String(cString: name)
            if devName.contains("BlackHole 2ch") {
                return id
            }
        }
    }
    return nil
}

let (speakerDevID, speakerDevName) = findDefaultOutputDeviceID()
let blackHoleDevID = findBlackHoleDeviceID()

if let sId = speakerDevID {
    print("🔊 检测到物理扬声器设备: \(speakerDevName) (Device ID: \(sId))")
}
if let bId = blackHoleDevID {
    print("✅ 检测到虚拟声卡设备: BlackHole 2ch (Device ID: \(bId))")
} else {
    print("ℹ️ 未检测到 BlackHole 驱动，将专注于物理扬声器实时直放监听")
}

// 注册物理扬声器 IOProc
var speakerProcID: AudioDeviceIOProcID?
var speakerActive = false
let speakerLock = NSLock()

if let sId = speakerDevID {
    let ioStatus = AudioDeviceCreateIOProcIDWithBlock(&speakerProcID, sId, nil) { inNow, inInputData, inInputTime, outOutputData, inOutputTime in
        let bufferList = UnsafeMutableAudioBufferListPointer(outOutputData)
        for buffer in bufferList {
            let numChannels = Int(buffer.mNumberChannels)
            let totalSamples = Int(buffer.mDataByteSize) / MemoryLayout<Float32>.size
            let numFrames = totalSamples / max(1, numChannels)
            guard let mData = buffer.mData else { continue }
            let ptr = mData.bindMemory(to: Float32.self, capacity: totalSamples)

            var monoTemp = [Float32](repeating: 0, count: numFrames)
            monoTemp.withUnsafeMutableBufferPointer { tempPtr in
                speakerRingBuffer.read(into: tempPtr.baseAddress!, count: numFrames)
            }

            for i in 0..<numFrames {
                let sample = monoTemp[i] * 0.95 // 适当增益
                for ch in 0..<numChannels {
                    ptr[i * numChannels + ch] = sample
                }
            }
        }
    }
    if ioStatus == noErr, let proc = speakerProcID {
        AudioDeviceStart(sId, proc)
        speakerActive = true
        print("🎉 物理扬声器实时监听已启动！上位机即可直接听到声音！")
    } else {
        print("⚠️ 物理扬声器 IOProc 初始化失败: \(ioStatus)")
    }
}

// 注册 BlackHole 虚拟声卡 IOProc (若存在)
var blackHoleProcID: AudioDeviceIOProcID?
if let bId = blackHoleDevID {
    let ioStatus = AudioDeviceCreateIOProcIDWithBlock(&blackHoleProcID, bId, nil) { inNow, inInputData, inInputTime, outOutputData, inOutputTime in
        let bufferList = UnsafeMutableAudioBufferListPointer(outOutputData)
        if bufferList.count == 1 && bufferList[0].mNumberChannels == 2 {
            let totalSamples = Int(bufferList[0].mDataByteSize) / MemoryLayout<Float32>.size
            let numFrames = totalSamples / 2
            guard let mData = bufferList[0].mData else { return }
            let ptr = mData.bindMemory(to: Float32.self, capacity: totalSamples)

            var monoTemp = [Float32](repeating: 0, count: numFrames)
            monoTemp.withUnsafeMutableBufferPointer { tempPtr in
                blackHoleRingBuffer.read(into: tempPtr.baseAddress!, count: numFrames)
            }
            for i in 0..<numFrames {
                ptr[2 * i] = monoTemp[i]
                ptr[2 * i + 1] = monoTemp[i]
            }
        } else {
            for buffer in bufferList {
                guard let mData = buffer.mData else { continue }
                let numSamples = Int(buffer.mDataByteSize) / MemoryLayout<Float32>.size
                let ptr = mData.bindMemory(to: Float32.self, capacity: numSamples)
                blackHoleRingBuffer.read(into: ptr, count: numSamples)
            }
        }
    }
    if ioStatus == noErr, let proc = blackHoleProcID {
        AudioDeviceStart(bId, proc)
        print("✅ BlackHole 虚拟输入通道已并网运行")
    }
}

// ============================================================
// 3. Web HTTP 实时音频广播分发器 (用于 Web 上位机)
// ============================================================
final class WebAudioBroadcaster {
    private var clientSockets = [Int32]()
    private let lock = NSLock()

    func addClient(sock: Int32) {
        lock.lock()
        defer { lock.unlock() }
        clientSockets.append(sock)
        print("🌐 [Web监听] 客户端已连接 (当前在线监听: \(clientSockets.count))")
    }

    func removeClient(sock: Int32) {
        lock.lock()
        defer { lock.unlock() }
        if let idx = clientSockets.firstIndex(of: sock) {
            clientSockets.remove(at: idx)
            close(sock)
            print("🌐 [Web监听] 客户端断开 (剩余监听: \(clientSockets.count))")
        }
    }

    func broadcast(data: UnsafeRawPointer, count: Int) {
        lock.lock()
        let sockets = clientSockets
        lock.unlock()

        guard !sockets.isEmpty else { return }

        // Chunked transfer encoding format: <hex_size>\r\n<data>\r\n
        let hexHeader = String(format: "%X\r\n", count)
        guard let headerData = hexHeader.data(using: .utf8) else { return }

        var toRemove = [Int32]()
        for sock in sockets {
            var failed = false
            headerData.withUnsafeBytes { hPtr in
                if send(sock, hPtr.baseAddress, hPtr.count, 0) <= 0 { failed = true }
            }
            if !failed && send(sock, data, count, 0) <= 0 { failed = true }
            if !failed {
                let tail = "\r\n"
                tail.withCString { tPtr in
                    if send(sock, tPtr, 2, 0) <= 0 { failed = true }
                }
            }
            if failed {
                toRemove.append(sock)
            }
        }

        if !toRemove.isEmpty {
            lock.lock()
            for deadSock in toRemove {
                if let idx = clientSockets.firstIndex(of: deadSock) {
                    clientSockets.remove(at: idx)
                    close(deadSock)
                }
            }
            lock.unlock()
        }
    }
}

let webBroadcaster = WebAudioBroadcaster()

// 启动 HTTP 音频广播服务器 (端口 9530)
let httpThread = Thread {
    let serverSock = socket(AF_INET, SOCK_STREAM, 0)
    var opt: Int32 = 1
    setsockopt(serverSock, SOL_SOCKET, SO_REUSEADDR, &opt, socklen_t(MemoryLayout<Int32>.size))

    var srvAddr = sockaddr_in()
    srvAddr.sin_family = UInt8(AF_INET)
    srvAddr.sin_port = HTTP_STREAM_PORT.bigEndian
    srvAddr.sin_addr.s_addr = INADDR_ANY

    _ = withUnsafePointer(to: &srvAddr) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
            bind(serverSock, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
        }
    }
    listen(serverSock, 10)
    print("📡 Web 实时音频流服务已启动 (端口: \(HTTP_STREAM_PORT)，路径: /audio_stream)")

    var clientAddr = sockaddr_in()
    var clientLen = socklen_t(MemoryLayout<sockaddr_in>.size)

    while true {
        let clientSock = withUnsafeMutablePointer(to: &clientAddr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                accept(serverSock, saPtr, &clientLen)
            }
        }
        guard clientSock >= 0 else { continue }

        var flag: Int32 = 1
        setsockopt(clientSock, IPPROTO_TCP, TCP_NODELAY, &flag, socklen_t(MemoryLayout<Int32>.size))

        // 读取 HTTP 请求头
        var reqBuf = [UInt8](repeating: 0, count: 1024)
        _ = recv(clientSock, &reqBuf, reqBuf.count, 0)

        // 发送 HTTP 流式响应头
        let httpResponse = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: audio/x-raw; rate=48000; format=s16le; channels=1\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "Connection: keep-alive\r\n" +
            "Cache-Control: no-cache\r\n\r\n"

        httpResponse.withCString { respPtr in
            _ = send(clientSock, respPtr, strlen(respPtr), 0)
        }

        webBroadcaster.addClient(sock: clientSock)
    }
}
httpThread.qualityOfService = .userInitiated
httpThread.start()

// ============================================================
// 4. 数据接收并分发 (分发至物理扬声器、BlackHole、Web流)
// ============================================================
func handleIncomingPcm(rawBytes: UnsafeRawPointer, count: Int) {
    let sampleCount = count / 2
    guard sampleCount > 0 else { return }

    let base = rawBytes.bindMemory(to: Int16.self, capacity: sampleCount)

    // 1. 写入扬声器缓冲区 (上位机物理外放)
    speakerRingBuffer.write(samples: base, count: sampleCount)

    // 2. 写入 BlackHole 虚拟声卡 (系统级麦克风)
    if blackHoleDevID != nil {
        blackHoleRingBuffer.write(samples: base, count: sampleCount)
    }

    // 3. 广播给 Web 浏览器客户端
    webBroadcaster.broadcast(data: rawBytes, count: count)
}

// 启动 TCP 直通监听 (端口 9528)
let tcpThread = Thread {
    let serverSock = socket(AF_INET, SOCK_STREAM, 0)
    var opt: Int32 = 1
    setsockopt(serverSock, SOL_SOCKET, SO_REUSEADDR, &opt, socklen_t(MemoryLayout<Int32>.size))

    var srvAddr = sockaddr_in()
    srvAddr.sin_family = UInt8(AF_INET)
    srvAddr.sin_port = PORT.bigEndian
    srvAddr.sin_addr.s_addr = INADDR_ANY

    _ = withUnsafePointer(to: &srvAddr) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
            bind(serverSock, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
        }
    }

    listen(serverSock, 5)
    print("📡 TCP 直通服务器已就绪 (端口: \(PORT))")

    var clientAddr = sockaddr_in()
    var clientLen = socklen_t(MemoryLayout<sockaddr_in>.size)

    while true {
        let clientSock = withUnsafeMutablePointer(to: &clientAddr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                accept(serverSock, saPtr, &clientLen)
            }
        }
        if clientSock >= 0 {
            print("🎙️ [TCP] 手机端麦克风直连已建立，开始实时接收音频流...")
            var flag: Int32 = 1
            setsockopt(clientSock, IPPROTO_TCP, TCP_NODELAY, &flag, socklen_t(MemoryLayout<Int32>.size))

            var recvBuf = [UInt8](repeating: 0, count: 4096)
            while true {
                let bytesRead = recv(clientSock, &recvBuf, recvBuf.count, 0)
                if bytesRead > 0 {
                    recvBuf.withUnsafeBytes { rawPtr in
                        guard let base = rawPtr.baseAddress else { return }
                        handleIncomingPcm(rawBytes: base, count: bytesRead)
                    }
                } else {
                    break
                }
            }
            close(clientSock)
            print("🛑 [TCP] 手机端麦克风已断开")
        }
    }
}
tcpThread.qualityOfService = .userInteractive
tcpThread.start()

// 启动 UDP 监听 (端口 9528)
let udpThread = Thread {
    let udpSock = socket(AF_INET, SOCK_DGRAM, 0)
    var opt: Int32 = 1
    setsockopt(udpSock, SOL_SOCKET, SO_REUSEADDR, &opt, socklen_t(MemoryLayout<Int32>.size))
    var srvAddr = sockaddr_in()
    srvAddr.sin_family = UInt8(AF_INET)
    srvAddr.sin_port = PORT.bigEndian
    srvAddr.sin_addr.s_addr = INADDR_ANY

    _ = withUnsafePointer(to: &srvAddr) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
            bind(udpSock, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
        }
    }

    var recvBuf = [UInt8](repeating: 0, count: 4096)
    while true {
        let bytesRead = recv(udpSock, &recvBuf, recvBuf.count, 0)
        if bytesRead > 0 {
            recvBuf.withUnsafeBytes { rawPtr in
                guard let base = rawPtr.baseAddress else { return }
                handleIncomingPcm(rawBytes: base, count: bytesRead)
            }
        }
    }
}
udpThread.qualityOfService = .userInteractive
udpThread.start()

RunLoop.main.run()
