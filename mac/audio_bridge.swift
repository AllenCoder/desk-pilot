// audio_bridge.swift - Mac 原生 CoreAudio 音频直通中继桥
// 支持按需唤醒 (On-Demand Activation): 仅在 Mac 端有录音需求时才启动链路并广播通知手机
// 实时注入 BlackHole 2ch 虚拟麦克风驱动

import Foundation
import CoreAudio
import AudioToolbox
import Darwin

setbuf(stdout, nil)
setbuf(stderr, nil)

let PORT: UInt16 = 9528
let BUFFER_CAPACITY = 48000 * 2 // 2秒 48kHz 缓冲区
let MIC_DEMAND_FILE = "/tmp/machud_mic_demand"

print("╔═════════════════════════════════════════════╗")
print("║  🎙️ Mac 原生 CoreAudio 音频直通中继桥启动  ║")
print("╚═════════════════════════════════════════════╝")

// 1. 查找 BlackHole 2ch
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

guard let devID = findBlackHoleDeviceID() else {
    print("❌ 错误: 未检测到 BlackHole 2ch 驱动")
    exit(1)
}
print("✅ 检测到虚拟声卡: BlackHole 2ch (Device ID: \(devID))")

// 2. 线程安全环形缓冲区
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

let ringBuffer = RingBuffer(capacity: BUFFER_CAPACITY)

// 3. 注册 CoreAudio HAL 硬件直通 IOProc 回调
var procID: AudioDeviceIOProcID?
let ioStatus = AudioDeviceCreateIOProcIDWithBlock(&procID, devID, nil) { inNow, inInputData, inInputTime, outOutputData, inOutputTime in
    let bufferList = UnsafeMutableAudioBufferListPointer(outOutputData)
    if bufferList.count == 1 && bufferList[0].mNumberChannels == 2 {
        let totalSamples = Int(bufferList[0].mDataByteSize) / MemoryLayout<Float32>.size
        let numFrames = totalSamples / 2
        guard let mData = bufferList[0].mData else { return }
        let ptr = mData.bindMemory(to: Float32.self, capacity: totalSamples)

        var monoTemp = [Float32](repeating: 0, count: numFrames)
        monoTemp.withUnsafeMutableBufferPointer { tempPtr in
            ringBuffer.read(into: tempPtr.baseAddress!, count: numFrames)
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
            ringBuffer.read(into: ptr, count: numSamples)
        }
    }
}

guard ioStatus == noErr, let proc = procID else {
    print("❌ AudioDeviceCreateIOProcIDWithBlock 失败: \(ioStatus)")
    exit(1)
}



// 3.2 查找 MacBook 物理内置麦克风 (双通道监听支持)
func findBuiltInMicDeviceID() -> AudioDeviceID? {
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
            if (devName.contains("麦克风") || devName.contains("Microphone")) && !devName.contains("BlackHole") {
                return id
            }
        }
    }
    return nil
}
let builtInMicID = findBuiltInMicDeviceID()
if let bId = builtInMicID {
    print("✅ 检测到物理麦克风 (Device ID: \(bId))，联动双通道监听")
}

// 4. 监听 Mac 端应用对麦克风的使用需求 (智能按需启停)
var isMacRecording = false
let macStateLock = NSLock()

// 全局通知 UDP 广播器 (向 Note 3 广播 Mac 端的麦克风状态)
func broadcastMicCommand(active: Bool) {
    let bcastSock = socket(AF_INET, SOCK_DGRAM, 0)
    var opt: Int32 = 1
    setsockopt(bcastSock, SOL_SOCKET, SO_BROADCAST, &opt, socklen_t(MemoryLayout<Int32>.size))
    var bcastAddr = sockaddr_in()
    bcastAddr.sin_family = UInt8(AF_INET)
    bcastAddr.sin_port = UInt16(9529).bigEndian
    bcastAddr.sin_addr.s_addr = INADDR_BROADCAST

    let cmdMsg = "{\"event\":\"mic_state\",\"active\":\(active)}"
    if let data = cmdMsg.data(using: .utf8) {
        _ = withUnsafePointer(to: &bcastAddr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                sendto(bcastSock, [UInt8](data), data.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
    }
    close(bcastSock)

    // 写入共享状态文件供 stats_server 兜底同步
    try? (active ? "1" : "0").write(toFile: MIC_DEMAND_FILE, atomically: true, encoding: .utf8)
}

// 初始化状态标记
try? "0".write(toFile: MIC_DEMAND_FILE, atomically: true, encoding: .utf8)

func isInputActive() -> Bool {
    let myPID = getpid()
    var address = AudioObjectPropertyAddress(
        mSelector: kAudioHardwarePropertyProcessObjectList,
        mScope: kAudioObjectPropertyScopeGlobal,
        mElement: kAudioObjectPropertyElementMain
    )
    var size: UInt32 = 0
    guard AudioObjectGetPropertyDataSize(AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &size) == noErr else {
        return false
    }
    let count = Int(size) / MemoryLayout<AudioObjectID>.size
    var procIDs = [AudioObjectID](repeating: 0, count: count)
    guard AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &address, 0, nil, &size, &procIDs) == noErr else {
        return false
    }

    for pID in procIDs {
        var pid: pid_t = 0
        var pidSz = UInt32(MemoryLayout<pid_t>.size)
        var addrPID = AudioObjectPropertyAddress(
            mSelector: kAudioProcessPropertyPID,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        guard AudioObjectGetPropertyData(pID, &addrPID, 0, nil, &pidSz, &pid) == noErr else { continue }
        if pid == myPID { continue } // 严格排除自身，防止把自身的输出混淆为外界录音输入

        var isInput: UInt32 = 0
        var runSz = UInt32(MemoryLayout<UInt32>.size)
        var addrIn = AudioObjectPropertyAddress(
            mSelector: kAudioProcessPropertyIsRunningInput,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        if AudioObjectGetPropertyData(pID, &addrIn, 0, nil, &runSz, &isInput) == noErr && isInput != 0 {
            return true
        }
    }
    return false
}

func evaluateRecordingState() {
    macStateLock.lock()
    defer { macStateLock.unlock() }

    let active = isInputActive()
    if active && !isMacRecording {
        isMacRecording = true
        ringBuffer.clear()
        AudioDeviceStart(devID, proc)
        print("🎙️ Mac 开启录音 (Siri/语音输入/微信) -> 启动副屏采集")
        fflush(stdout)
        broadcastMicCommand(active: true)
    } else if !active && isMacRecording {
        isMacRecording = false
        AudioDeviceStop(devID, proc)
        ringBuffer.clear()
        print("🛑 Mac 停止录音 -> 副屏麦克风休眠")
        fflush(stdout)
        broadcastMicCommand(active: false)
    }
}

// 注册系统级音频进程列表监听器 (任何应用发起或结束录音均会即时回调)
var addrProcList = AudioObjectPropertyAddress(
    mSelector: kAudioHardwarePropertyProcessObjectList,
    mScope: kAudioObjectPropertyScopeGlobal,
    mElement: kAudioObjectPropertyElementMain
)
AudioObjectAddPropertyListenerBlock(AudioObjectID(kAudioObjectSystemObject), &addrProcList, DispatchQueue.main) { _, _ in
    evaluateRecordingState()
}

// 注册物理麦克风监听器 (覆盖绕过虚拟声卡的直接录音)
if let bId = builtInMicID {
    var addrPhys = AudioObjectPropertyAddress(
        mSelector: kAudioDevicePropertyDeviceIsRunningSomewhere,
        mScope: kAudioObjectPropertyScopeGlobal,
        mElement: kAudioObjectPropertyElementMain
    )
    AudioObjectAddPropertyListenerBlock(bId, &addrPhys, DispatchQueue.main) { _, _ in
        evaluateRecordingState()
    }
}

// 定时巡检看门狗 (300ms 周期，确保即便无事件通知也绝不残留悬挂)
let watchdogTimer = DispatchSource.makeTimerSource(queue: DispatchQueue.main)
watchdogTimer.schedule(deadline: .now() + 0.3, repeating: 0.3)
watchdogTimer.setEventHandler {
    evaluateRecordingState()
}
watchdogTimer.resume()

print("✅ CoreAudio 硬件级智能按需监听器已激活 (无录音时 0 开销休眠)")

// 5. 启动 TCP 服务器 (监听 9528，专用于 ADB Reverse USB 直通)
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
    print("📡 TCP 直通服务器已就绪 (ADB Reverse 端口: \(PORT))")
    fflush(stdout)

    var clientAddr = sockaddr_in()
    var clientLen = socklen_t(MemoryLayout<sockaddr_in>.size)

    while true {
        let clientSock = withUnsafeMutablePointer(to: &clientAddr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                accept(serverSock, saPtr, &clientLen)
            }
        }
        if clientSock >= 0 {
            var flag: Int32 = 1
            setsockopt(clientSock, IPPROTO_TCP, TCP_NODELAY, &flag, socklen_t(MemoryLayout<Int32>.size))

            var recvBuf = [UInt8](repeating: 0, count: 4096)
            while true {
                let bytesRead = recv(clientSock, &recvBuf, recvBuf.count, 0)
                if bytesRead > 0 {
                    let sampleCount = bytesRead / 2
                    recvBuf.withUnsafeBytes { rawPtr in
                        guard let base = rawPtr.bindMemory(to: Int16.self).baseAddress else { return }
                        ringBuffer.write(samples: base, count: sampleCount)
                    }
                } else {
                    break
                }
            }
            close(clientSock)
        }
    }
}
tcpThread.qualityOfService = .userInteractive
tcpThread.start()

// 6. 启动 UDP 监听 (支持局域网无线广播)
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
            let sampleCount = bytesRead / 2
            recvBuf.withUnsafeBytes { rawPtr in
                guard let base = rawPtr.bindMemory(to: Int16.self).baseAddress else { return }
                ringBuffer.write(samples: base, count: sampleCount)
            }
        }
    }
}
udpThread.qualityOfService = .userInteractive
udpThread.start()

RunLoop.main.run()
