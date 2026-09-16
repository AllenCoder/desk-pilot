// stats_server.swift - Mac Monitor · Swift 原生极致低功耗实现
// 编译: swiftc stats_server.swift -O -framework Foundation -framework IOKit -framework CoreAudio -o stats_server
// 运行: ./stats_server

import Foundation
import Darwin
import IOKit.ps
import CoreAudio

setbuf(stdout, nil)
setbuf(stderr, nil)

// ─────────────────────────────────────────────
// MARK: - SMC CPU 温度 (Intel Mac 原生)
// ─────────────────────────────────────────────
let KERN_SMC: UInt32 = 2

struct SMCKeyData_t {
    var key: UInt32 = 0
    var vers: (UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8) = (0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    var pLimitData: (UInt16,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8) = (0,0,0,0,0,0,0,0,0,0,0,0,0)
    var keyInfo: (UInt32, UInt32, UInt8, UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8) = (0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    var result: UInt8 = 0; var status: UInt8 = 0; var data8: UInt8 = 0; var data32: UInt32 = 0
    var bytes: (UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,
                UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8,UInt8) = (0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
}

var smcConn: io_connect_t = 0
var smcOk = false

func smcOpen() {
    let s = IOServiceGetMatchingService(kIOMainPortDefault, IOServiceMatching("AppleSMC"))
    guard s != 0 else { return }
    smcOk = IOServiceOpen(s, mach_task_self_, 0, &smcConn) == kIOReturnSuccess
    IOObjectRelease(s)
}

func smcRW(_ cmd: UInt8, _ inp: inout SMCKeyData_t, _ out: inout SMCKeyData_t) -> kern_return_t {
    var sz = MemoryLayout<SMCKeyData_t>.size
    return withUnsafeMutablePointer(to: &inp) { i in
        withUnsafeMutablePointer(to: &out) { o in
            IOConnectCallStructMethod(smcConn, KERN_SMC, i, sz, o, &sz)
        }
    }
}

func cpuTemp() -> Double? {
    guard smcOk else { return nil }
    for k in ["TC0P","TC0D","TC0E","TCXC","TCGC"] {
        let c = Array(k.utf8); guard c.count == 4 else { continue }
        var i = SMCKeyData_t(); var o = SMCKeyData_t()
        i.key = UInt32(c[0])<<24 | UInt32(c[1])<<16 | UInt32(c[2])<<8 | UInt32(c[3])
        i.data8 = 9
        guard smcRW(9, &i, &o) == kIOReturnSuccess else { continue }
        i.keyInfo = o.keyInfo; i.data8 = 5
        guard smcRW(5, &i, &o) == kIOReturnSuccess else { continue }
        let v = Double(Int16(bitPattern: UInt16(o.bytes.0)<<8 | UInt16(o.bytes.1))) / 256.0
        if v > 15 && v < 125 { return v }
    }
    return nil
}

// ─────────────────────────────────────────────
// MARK: - 系统指标采集 (纯原生 C / Mach API)
// ─────────────────────────────────────────────

func cpuUsage() -> (total: Double, cores: [Double]) {
    var arr: processor_info_array_t?; var cnt: mach_msg_type_number_t = 0; var num: natural_t = 0
    guard host_processor_info(mach_host_self(), PROCESSOR_CPU_LOAD_INFO, &num, &arr, &cnt) == KERN_SUCCESS,
          let a = arr else { return (0,[]) }
    var cores: [Double] = []
    for i in 0..<Int(num) {
        let b = i * Int(CPU_STATE_MAX)
        let u = Double(a[b+Int(CPU_STATE_USER)]), s = Double(a[b+Int(CPU_STATE_SYSTEM)])
        let id = Double(a[b+Int(CPU_STATE_IDLE)]), n = Double(a[b+Int(CPU_STATE_NICE)])
        let tot = u + s + id + n
        cores.append(tot > 0 ? (u + s) / tot * 100.0 : 0.0)
    }
    vm_deallocate(mach_task_self_, vm_address_t(bitPattern: a), vm_size_t(cnt)*4)
    let avg = cores.isEmpty ? 0.0 : cores.reduce(0,+) / Double(cores.count)
    return (avg, cores)
}

func memInfo() -> (used: Double, total: Double, pct: Double, appGB: Double, wiredGB: Double, compGB: Double, swapUsedMB: Double, swapTotalMB: Double) {
    var stat = vm_statistics64_t.allocate(capacity: 1)
    var cnt = mach_msg_type_number_t(MemoryLayout<vm_statistics64_data_t>.size / 4)
    let kerr = withUnsafeMutablePointer(to: &stat.pointee) {
        $0.withMemoryRebound(to: integer_t.self, capacity: Int(cnt)) {
            host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &cnt)
        }
    }
    defer { stat.deallocate() }
    var ps: vm_size_t = 4096; _ = host_page_size(mach_host_self(), &ps)
    var totBytes: UInt64 = 0; var sz = MemoryLayout<UInt64>.size
    _ = sysctlbyname("hw.memsize", &totBytes, &sz, nil, 0)
    let totGB = Double(totBytes) / (1024*1024*1024)
    guard kerr == KERN_SUCCESS else { return (0, totGB, 0, 0, 0, 0, 0, 0) }
    let s = stat.pointee; let pg = Double(ps)
    let usedBytes = (Double(s.active_count) + Double(s.wire_count) + Double(s.speculative_count)) * pg
    let usedGB = usedBytes / (1024*1024*1024)
    let pct = totGB > 0 ? min(100.0, usedGB / totGB * 100.0) : 0.0

    // macOS 架构细分: App 内存, Wired 核心驻留, Compressed 压缩
    let appBytes = max(0, (Double(s.internal_page_count) - Double(s.purgeable_count))) * pg
    let appGB = appBytes / (1024*1024*1024)
    let wiredGB = (Double(s.wire_count) * pg) / (1024*1024*1024)
    let compGB = (Double(s.compressor_page_count) * pg) / (1024*1024*1024)

    // Swap 交换区
    var swap = xsw_usage(); var swSz = MemoryLayout<xsw_usage>.size
    _ = sysctlbyname("vm.swapusage", &swap, &swSz, nil, 0)
    let swapUsedMB = Double(swap.xsu_used) / (1024*1024)
    let swapTotalMB = Double(swap.xsu_total) / (1024*1024)

    return (usedGB, totGB, pct, appGB, wiredGB, compGB, swapUsedMB, swapTotalMB)
}

func battInfo() -> (pct: Int, charging: Bool, timeLeft: String) {
    guard let b = IOPSCopyPowerSourcesInfo()?.takeRetainedValue(),
          let list = IOPSCopyPowerSourcesList(b)?.takeRetainedValue() as? [CFTypeRef] else {
        return (100, true, "交流电")
    }
    for ps in list {
        if let desc = IOPSGetPowerSourceDescription(b, ps)?.takeUnretainedValue() as? [String: Any] {
            let cur = desc[kIOPSCurrentCapacityKey] as? Int ?? 100
            let chg = desc[kIOPSIsChargingKey] as? Bool ?? false
            let pwr = desc[kIOPSPowerSourceStateKey] as? String ?? ""
            let isAC = pwr == kIOPSACPowerValue
            let t = desc[kIOPSTimeToEmptyKey] as? Int ?? -1
            var tl = isAC ? (chg ? "充电中" : "已充满") : (t > 0 ? "\(t/60)小时\(t%60)分" : "计算中")
            return (cur, chg, tl)
        }
    }
    return (100, true, "交流电")
}

func diskInfo() -> (used: Double, total: Double, pct: Double) {
    var stat = statfs()
    guard statfs("/", &stat) == 0 else { return (0, 0, 0) }
    let bs = Double(stat.f_bsize)
    let tot = (Double(stat.f_blocks) * bs) / (1024*1024*1024)
    let free = (Double(stat.f_bavail) * bs) / (1024*1024*1024)
    let used = tot - free
    let pct = tot > 0 ? (used / tot) * 100.0 : 0.0
    return (used, tot, pct)
}

func readNet() -> (inn: UInt64, out: UInt64) {
    var ifap: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&ifap) == 0, let fa = ifap else { return (0,0) }
    defer { freeifaddrs(fa) }
    var inn: UInt64 = 0, out: UInt64 = 0
    var p = fa
    while true {
        let f = p.pointee.ifa_flags
        if (f & UInt32(IFF_UP)) != 0 && (f & UInt32(IFF_LOOPBACK)) == 0 {
            if let data = p.pointee.ifa_data {
                let d = data.bindMemory(to: if_data.self, capacity: 1)
                inn += UInt64(d.pointee.ifi_ibytes)
                out += UInt64(d.pointee.ifi_obytes)
            }
        }
        guard let nx = p.pointee.ifa_next else { break }; p = nx
    }
    return (inn, out)
}

func uptimeStr() -> String {
    var bootTime = timeval(); var sz = MemoryLayout<timeval>.size
    guard sysctlbyname("kern.boottime", &bootTime, &sz, nil, 0) == 0 else { return "--" }
    let sec = Int(Date().timeIntervalSince1970) - bootTime.tv_sec
    let d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60
    return d > 0 ? "\(d)天\(h)时\(m)分" : "\(h)时\(m)分"
}

func powerInfo() -> (watts: Double, wattsStr: String, level: String, isAC: Bool, adapterWatts: Int, adapterName: String, voltage: Double, amperage: Double) {
    let s = IOServiceGetMatchingService(kIOMainPortDefault, IOServiceMatching("AppleSmartBattery"))
    guard s != 0 else {
        return (0.0, "-- W", "OFFLINE", false, 0, "未知", 0.0, 0.0)
    }
    defer { IOObjectRelease(s) }

    var props: Unmanaged<CFMutableDictionary>?
    guard IORegistryEntryCreateCFProperties(s, &props, kCFAllocatorDefault, 0) == kIOReturnSuccess,
          let dict = props?.takeRetainedValue() as? [String: Any] else {
        return (0.0, "-- W", "OFFLINE", false, 0, "未知", 0.0, 0.0)
    }

    var powerWatts = 0.0
    var adapterWatts = 0
    var adapterDesc = "电池供电"
    var isAC = false
    var volt = 0.0
    var amp = 0.0

    if let adapter = dict["AdapterDetails"] as? [String: Any] {
        adapterWatts = adapter["Watts"] as? Int ?? 0
        let desc = adapter["Description"] as? String ?? ""
        if adapterWatts > 0 {
            isAC = true
            adapterDesc = desc.isEmpty ? "PD \(adapterWatts)W" : "\(desc.uppercased()) \(adapterWatts)W"
        }
    }

    if let telemetry = dict["PowerTelemetryData"] as? [String: Any] {
        let sysPowerIn = telemetry["SystemPowerIn"] as? Int ?? (telemetry["SystemLoad"] as? Int ?? 0)
        powerWatts = Double(sysPowerIn) / 1000.0
        let vIn = telemetry["SystemVoltageIn"] as? Int ?? 0
        let aIn = telemetry["SystemCurrentIn"] as? Int ?? 0
        volt = Double(vIn) / 1000.0
        amp = Double(aIn) / 1000.0
    }

    if powerWatts <= 0 {
        let v = dict["Voltage"] as? Int ?? 0
        let a = abs(dict["InstantAmperage"] as? Int ?? (dict["Amperage"] as? Int ?? 0))
        powerWatts = Double(v * a) / 1_000_000.0
        volt = Double(v) / 1000.0
        amp = Double(a) / 1000.0
    }

    let level: String
    if powerWatts < 12.0 {
        level = "LOW"
    } else if powerWatts <= 30.0 {
        level = "BALANCED"
    } else {
        level = "HIGH"
    }

    let wattsStr = String(format: "%.1f W", powerWatts)
    return (powerWatts, wattsStr, level, isAC, adapterWatts, adapterDesc, volt, amp)
}

func topProcs() -> [(name: String, cpu: Double, memStr: String)] {
    let p = Process(); let out = Pipe()
    p.executableURL = URL(fileURLWithPath: "/bin/ps")
    p.arguments = ["-arcx", "-o", "%cpu,rss,comm"]
    p.standardOutput = out
    p.standardError = Pipe()
    do {
        try p.run()
        let data = out.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        guard let str = String(data: data, encoding: .utf8) else { return [] }
        var res: [(String, Double, String)] = []
        let lines = str.components(separatedBy: "\n").dropFirst()
    for l in lines {
        let t = l.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { continue }
        let parts = t.split(separator: " ", maxSplits: 2, omittingEmptySubsequences: true)
        guard parts.count == 3, let cpu = Double(parts[0]), let rssKB = Double(parts[1]) else { continue }
        let name = String(parts[2]).trimmingCharacters(in: .whitespaces)
        if name == "ps" || name == "stats_server" || name == "audio_bridge" { continue }

        let memStr: String
        if rssKB >= 1024 * 1024 {
            memStr = String(format: "%.1f GB", rssKB / (1024 * 1024))
        } else {
            memStr = String(format: "%.0f MB", rssKB / 1024)
        }

        res.append((name, cpu, memStr))
        if res.count >= 4 { break }
    }
    return res
    } catch {
        return []
    }
}

func fmtSpeed(_ b: Double) -> String {
    if b < 1024 { return String(format: "%.0f B/s", b) }
    if b < 1024*1024 { return String(format: "%.1f KB/s", b/1024) }
    return String(format: "%.1f MB/s", b/(1024*1024))
}

func fmtBytes(_ b: UInt64) -> String {
    if b < 1024 { return "\(b) B" }
    let kb = Double(b) / 1024.0
    if kb < 1024 { return String(format: "%.0f KB", kb) }
    let mb = kb / 1024.0
    if mb < 1024 { return String(format: "%.1f MB", mb) }
    let gb = mb / 1024.0
    return String(format: "%.1f GB", gb)
}

// ─────────────────────────────────────────────
// MARK: - 流派 1 工控扩展指标: CPU 型号 / UNIX 负载 / 核心进程数 / 网络延迟
// ─────────────────────────────────────────────
var gBrandShort: String = {
    var size = 0
    sysctlbyname("machdep.cpu.brand_string", nil, &size, nil, 0)
    if size > 0 {
        var machine = [CChar](repeating: 0, count: size)
        sysctlbyname("machdep.cpu.brand_string", &machine, &size, nil, 0)
        let brandRaw = String(cString: machine).trimmingCharacters(in: .whitespacesAndNewlines)
        return brandRaw.replacingOccurrences(of: "Intel(R) Core(TM) ", with: "")
                       .replacingOccurrences(of: " CPU", with: "")
    }
    return "i5 @ 2.30GHz"
}()

func systemLoadAndProcs() -> (loadAvg: String, procCount: Int) {
    var loadavg = [Double](repeating: 0.0, count: 3)
    getloadavg(&loadavg, 3)
    let loadStr = String(format: "%.2f · %.2f · %.2f", loadavg[0], loadavg[1], loadavg[2])

    var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_ALL, 0]
    var len: Int = 0
    sysctl(&mib, 4, nil, &len, nil, 0)
    let procs = len / MemoryLayout<kinfo_proc>.size
    return (loadStr, procs)
}

var gPingMs = "14ms"
func startPingMonitor() {
    let t = Thread {
        while true {
            let p = Process(); let out = Pipe()
            p.executableURL = URL(fileURLWithPath: "/sbin/ping")
            p.arguments = ["-c", "1", "-t", "1", "223.5.5.5"]
            p.standardOutput = out
            do {
                try p.run()
                let d = out.fileHandleForReading.readDataToEndOfFile()
                p.waitUntilExit()
                if let str = String(data: d, encoding: .utf8),
                   let range = str.range(of: "time=") {
                    let sub = str[range.upperBound...]
                    let parts = sub.split(separator: " ")
                    if let first = parts.first, let ms = Double(first) {
                        gPingMs = String(format: "%.0fms", ms)
                    }
                }
            } catch {}
            Thread.sleep(forTimeInterval: 4.0)
        }
    }
    t.qualityOfService = .background
    t.start()
}

// ─────────────────────────────────────────────
// MARK: - CoreAudio 麦克风按需状态检测
// ─────────────────────────────────────────────
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
            if devName.contains("BlackHole 2ch") { return id }
        }
    }
    return nil
}

let bhDeviceID = findBlackHoleDeviceID()

func checkMicDemand() -> Bool {
    if let str = try? String(contentsOfFile: "/tmp/machud_mic_demand", encoding: .utf8) {
        return str.trimmingCharacters(in: .whitespacesAndNewlines) == "1"
    }
    return false
}

// ─────────────────────────────────────────────
// MARK: - 缓存刷新逻辑
// ─────────────────────────────────────────────

var prevNetIn: UInt64 = 0, prevNetOut: UInt64 = 0
var prevNetTime = Date()
var gJSON = "{}"
let gLock = NSLock()

func refreshData() {
    let now = Date()
    let cpu = cpuUsage(); let mem = memInfo(); let disk = diskInfo()
    let temp = cpuTemp(); let up = uptimeStr(); let batt = battInfo()
    let pwr = powerInfo()
    let procs = topProcs()
    let micActive = checkMicDemand()

    let net = readNet(); let dt = now.timeIntervalSince(prevNetTime)
    var dn = 0.0; var up2 = 0.0
    if dt > 0.1 {
        dn  = max(0, Double(net.inn - prevNetIn)  / dt)
        up2 = max(0, Double(net.out - prevNetOut) / dt)
    }
    prevNetIn = net.inn; prevNetOut = net.out; prevNetTime = now

    let df1 = DateFormatter(); df1.dateFormat = "HH:mm:ss"; df1.timeZone = TimeZone.current
    let df2 = DateFormatter(); df2.dateFormat = "yyyy年M月d日 EEE"; df2.locale = Locale(identifier:"zh_CN"); df2.timeZone = TimeZone.current

    let cores = cpu.cores.map { String(format:"%.1f",$0) }.joined(separator:",")
    let procsJ = procs.map { p in
        let n = p.name.replacingOccurrences(of:"\"",with:"'").replacingOccurrences(of:"\\",with:"")
        return "{\"name\":\"\(n)\",\"cpu\":\(String(format:"%.1f",p.cpu)),\"mem\":\"\(p.memStr)\"}"
    }.joined(separator:",")
    let tempJ = temp.map { String(format:"%.1f",$0) } ?? "null"
    let (loadAvg, procCount) = systemLoadAndProcs()
    let currentLanIP = localIP()

    let json = """
    {"time":"\(df1.string(from:now))","date":"\(df2.string(from:now))",
    "cpu":{"total":\(String(format:"%.1f",cpu.total)),"cores":[\(cores)],"temp":\(tempJ),"brand":"\(gBrandShort)","loadAvg":"\(loadAvg)","procs":\(procCount)},
    "memory":{"used":\(String(format:"%.2f",mem.used)),"total":\(String(format:"%.2f",mem.total)),"percent":\(String(format:"%.1f",mem.pct)),"app":\(String(format:"%.2f",mem.appGB)),"wired":\(String(format:"%.2f",mem.wiredGB)),"compressed":\(String(format:"%.2f",mem.compGB)),"swapUsed":\(String(format:"%.0f",mem.swapUsedMB)),"swapTotal":\(String(format:"%.0f",mem.swapTotalMB))},
    "battery":{"percent":\(batt.pct),"charging":\(batt.charging),"timeLeft":"\(batt.timeLeft)"},
    "power":{"watts":\(String(format:"%.1f",pwr.watts)),"wattsStr":"\(pwr.wattsStr)","level":"\(pwr.level)","isAC":\(pwr.isAC),"adapterWatts":\(pwr.adapterWatts),"adapterName":"\(pwr.adapterName)","voltage":\(String(format:"%.1f",pwr.voltage)),"amperage":\(String(format:"%.2f",pwr.amperage))},
    "network":{"down":\(String(format:"%.0f",dn)),"up":\(String(format:"%.0f",up2)),"downStr":"\(fmtSpeed(dn))","upStr":"\(fmtSpeed(up2))","lanIP":"\(currentLanIP)","pingMs":"\(gPingMs)","totalIn":"\(fmtBytes(net.inn))","totalOut":"\(fmtBytes(net.out))"},
    "disk":{"used":\(String(format:"%.0f",disk.used)),"total":\(String(format:"%.0f",disk.total)),"percent":\(String(format:"%.1f",disk.pct))},
    "mic":{"active":\(micActive)},
    "version":"v1.2.0",
    "uptime":"\(up)","processes":[\(procsJ)]}
    """
    gLock.lock(); gJSON = json; gLock.unlock()
}

// ─────────────────────────────────────────────
// MARK: - HTTP 服务 (原生非阻塞快速响应)
// ─────────────────────────────────────────────

let dashboardPath = "/Users/wangshuang/.gemini/antigravity/scratch/note3-dashboard/dashboard.html"

func serveClient(_ sock: Int32) {
    defer { close(sock) }
    var buf = [CChar](repeating: 0, count: 2048)
    let n = Darwin.recv(sock, &buf, buf.count - 1, 0)
    guard n > 0 else { return }
    buf[n] = 0
    let req = String(cString: buf)

    let body: String; let ct: String
    if req.contains("GET /api/stats") {
        gLock.lock(); body = gJSON; gLock.unlock()
        ct = "application/json; charset=utf-8"
    } else {
        body = (try? String(contentsOfFile: dashboardPath, encoding: .utf8)) ?? "<h1>Mac Monitor · Running</h1>"
        ct = "text/html; charset=utf-8"
    }

    let hdr = "HTTP/1.1 200 OK\r\nContent-Type: \(ct)\r\nContent-Length: \(body.utf8.count)\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
    let full = hdr + body
    full.utf8CString.withUnsafeBufferPointer { ptr in
        _ = Darwin.send(sock, ptr.baseAddress, ptr.count - 1, 0)
    }
}

func localIP() -> String {
    var ifap: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&ifap)==0, let fa=ifap else { return "localhost" }
    defer { freeifaddrs(fa) }
    var p = fa
    while true {
        if p.pointee.ifa_addr.pointee.sa_family == UInt8(AF_INET) {
            let nm = String(cString:p.pointee.ifa_name)
            if nm.hasPrefix("en") {
                var h = [CChar](repeating:0, count:Int(NI_MAXHOST))
                getnameinfo(p.pointee.ifa_addr, socklen_t(MemoryLayout<sockaddr_in>.size), &h, socklen_t(h.count), nil, 0, NI_NUMERICHOST)
                let ip = String(cString:h); if !ip.isEmpty && ip != "127.0.0.1" { return ip }
            }
        }
        guard let nx = p.pointee.ifa_next else { break }; p = nx
    }
    return "localhost"
}

// ─────────────────────────────────────────────
// MARK: - 主入口
// ─────────────────────────────────────────────

smcOpen()
startPingMonitor()
let initNet = readNet()
prevNetIn = initNet.inn; prevNetOut = initNet.out; prevNetTime = Date()
refreshData() // 首次预加载

// 后台 1.0 秒更新一次缓存 (采用独立 Thread，确保系统休眠唤醒后不丢失调度)
let refreshThread = Thread {
    while true {
        refreshData()
        Thread.sleep(forTimeInterval: 1.0)
    }
}
refreshThread.name = "StatsRefreshThread"
refreshThread.qualityOfService = .userInitiated
refreshThread.start()

let port: UInt16 = 9527
let srv = socket(AF_INET, SOCK_STREAM, 0)
var opt: Int32 = 1
setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &opt, socklen_t(MemoryLayout<Int32>.size))
var addr = sockaddr_in()
addr.sin_family = UInt8(AF_INET); addr.sin_port = port.bigEndian; addr.sin_addr.s_addr = INADDR_ANY
_ = withUnsafePointer(to: &addr) {
    $0.withMemoryRebound(to: sockaddr.self, capacity:1) {
        bind(srv, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
    }
}
listen(srv, 64)

// 局域网 UDP 自动发现广播线程 (每 3 秒发送一次心跳)
let beaconThread = Thread {
    let bcastSock = socket(AF_INET, SOCK_DGRAM, 0)
    var opt: Int32 = 1
    setsockopt(bcastSock, SOL_SOCKET, SO_BROADCAST, &opt, socklen_t(MemoryLayout<Int32>.size))
    var bcastAddr = sockaddr_in()
    bcastAddr.sin_family = UInt8(AF_INET)
    bcastAddr.sin_port = UInt16(9529).bigEndian
    bcastAddr.sin_addr.s_addr = INADDR_BROADCAST

    while true {
        let currentIp = localIP()
        let beaconMsg = "{\"service\":\"machud\",\"ip\":\"\(currentIp)\",\"statsPort\":9527,\"audioPort\":9528}"
        if let data = beaconMsg.data(using: .utf8) {
            _ = withUnsafePointer(to: &bcastAddr) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    sendto(bcastSock, [UInt8](data), data.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
        }
        Thread.sleep(forTimeInterval: 3.0)
    }
}
beaconThread.qualityOfService = .background
beaconThread.start()

let ip = localIP()
print("╔═══════════════════════════════════════════╗")
print("║      🖥  Mac Monitor · 已启动            ║")
print("╠═══════════════════════════════════════════╣")
let u1 = "║  📱 Note3: http://\(ip):9527"
print(u1.padding(toLength:44,withPad:" ",startingAt:0)+"║")
print("║  💻 本机:  http://localhost:9527           ║")
print("╚═══════════════════════════════════════════╝")
fflush(stdout)

while true {
    var ca = sockaddr_in(); var cl = socklen_t(MemoryLayout<sockaddr_in>.size)
    let cs = withUnsafeMutablePointer(to:&ca) {
        $0.withMemoryRebound(to:sockaddr.self, capacity:1) { accept(srv,$0,&cl) }
    }
    if cs < 0 { continue }
    DispatchQueue.global(qos:.userInteractive).async { serveClient(cs) }
}
