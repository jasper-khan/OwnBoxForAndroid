package libcore

import (
	"fmt"
	"libcore/device"
	"os"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"strings"
	_ "unsafe"

	"log"

	"github.com/sagernet/sing-box/option"
	"golang.org/x/sys/unix"
)

//go:linkname resourcePaths github.com/sagernet/sing-box/constant.resourcePaths
var (
	resourcePaths     []string
	protectSocketPath string
)

func GetProtectSocketPath() string {
	if protectSocketPath != "" {
		return protectSocketPath
	}
	return "protect_path"
}

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	platformLog.Truncate()
}

func ForceGc() {
	go func() {
		runtime.GC()
		debug.FreeOSMemory()
	}()
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	if1 NB4AInterface, if2 BoxPlatformInterface, if3 LocalDNSTransport,
) {
	defer device.DeferPanicToError("InitCore", func(err error) { log.Println(err) })
	isBgProcess = strings.HasSuffix(process, ":bg")

	// Apply memory profile based on user preference flag file written by Android before initCore.
	// File noBackup/perf_mode exists → high-performance mode; absent → extreme low-memory mode.
	// This avoids needing a new JNI binding for runtime memory tuning.
	tmp := filepath.Join(cachePath, "../no_backup")
	os.MkdirAll(tmp, 0755)
	os.Chdir(tmp)
	protectSocketPath = filepath.Join(tmp, "protect_path")

	perfModeFile := filepath.Join(tmp, "perf_mode")
	if _, err := os.Stat(perfModeFile); err == nil {
		SetMemoryProfile(true)
	} else {
		SetMemoryProfile(false)
	}

	intfNB4A = if1
	intfBox = if2
	useProcfs = intfBox.UseProcFS()
	gLocalDNSTransport = newPlatformTransport(if3, "", option.LocalDNSServerOptions{})

	// sing-box fs
	resourcePaths = append(resourcePaths, externalAssets)
	externalAssetsPath = externalAssets
	internalAssetsPath = internalAssets

	// Set up log
	if maxLogSizeKb < 50 {
		maxLogSizeKb = 50
	}
	setupLog(int(maxLogSizeKb)*1024, filepath.Join(cachePath, "neko.log"), isBgProcess, !logEnable)

	// 内置面板目录必须早于 box 启动就绪：官方内核 api 服务在目录缺失或为空时会去
	// GitHub 下载面板覆盖内置文件，并转入每日自动更新（service/api/dashboard.go）。
	// extractAssets 跑在 goroutine 且排在 geoip/geosite 之后，赶不上 box 启动。
	if isBgProcess {
		if err := extractAssetName(dashboardDstFolder, intfNB4A.UseOfficialAssets()); err != nil {
			log.Println("Extract", dashboardDstFolder, "failed:", err)
		}
	}

	// Set up some component
	go func() {
		defer device.DeferPanicToError("InitCore-go", func(err error) { log.Println(err) })
		device.GoDebug(process)

		// certs
		pem, err := os.ReadFile(externalAssetsPath + "ca.pem")
		if err == nil {
			updateRootCACerts(pem)
		}

		// bg
		if isBgProcess {
			extractAssets()
		}
	}()
}

func sendFdToProtect(fd int, path string) error {
	socketFd, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM, 0)
	if err != nil {
		return fmt.Errorf("failed to create unix socket: %w", err)
	}
	defer unix.Close(socketFd)

	var timeout unix.Timeval
	timeout.Sec = 2
	timeout.Usec = 0

	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeout)
	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_SNDTIMEO, &timeout)

	err = unix.Connect(socketFd, &unix.SockaddrUnix{Name: path})
	if err != nil {
		return fmt.Errorf("failed to connect: %w", err)
	}

	err = unix.Sendmsg(socketFd, nil, unix.UnixRights(fd), nil, 0)
	if err != nil {
		return fmt.Errorf("failed to send: %w", err)
	}

	dummy := []byte{1}
	n, err := unix.Read(socketFd, dummy)
	if err != nil {
		return fmt.Errorf("failed to receive: %w", err)
	}
	if n != 1 {
		return fmt.Errorf("socket closed unexpectedly")
	}
	return nil
}

// SetMemoryProfile dynamically switches the Go runtime GC profile.
// Called from Android at VPN service start based on user's "性能优先模式" toggle.
//   - performancePriority=false (default): extreme low-memory mode — GOGC=20, limit=128MB.
//     Go GC runs aggressively; RSS stays minimal in background. Safe for most users.
//   - performancePriority=true: high-performance mode — GOGC=100, no memory limit.
//     Maximises throughput for power users at the cost of higher background RAM.
//
// NOTE: interrupt_exist_connections and tolerance for leastPing are NOT affected by this switch.
func SetMemoryProfile(performancePriority bool) {
	if performancePriority {
		// High-perf: let Go runtime grow freely (same as upstream default)
		debug.SetGCPercent(100)
		debug.SetMemoryLimit(-1) // -1 = math.MaxInt64, disables the soft limit
	} else {
		// Low-mem: aggressively reclaim; cap at 128 MiB to prevent GC thrash while staying lean
		debug.SetGCPercent(20)
		debug.SetMemoryLimit(128 * 1024 * 1024)
	}
}
