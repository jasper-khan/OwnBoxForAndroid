package diagnostics

// Run against the pinned core:
// cd ../sing-box-ref1nd
// go test -tags with_external_windivert ../ownbox_fork/tools/diagnostics/fakeip_persistence_test.go -v
import (
	"context"
	"net/netip"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/dns/transport/fakeip"
	"github.com/sagernet/sing-box/experimental/cachefile"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
	"github.com/sagernet/sing/service"
)

func openFakeIPStore(t *testing.T, path string, interval time.Duration) (*cachefile.CacheFile, *fakeip.Store) {
	t.Helper()
	ctx := service.ContextWithDefaultRegistry(context.Background())
	logger := log.NewNOPFactory().Logger()
	cache := cachefile.New(ctx, logger, option.CacheFileOptions{
		Enabled: true, Path: path, StoreFakeIP: true, FlushInterval: badoption.Duration(interval),
	})
	if err := cache.Start(adapter.StartStateInitialize); err != nil {
		t.Fatal(err)
	}
	service.MustRegister[adapter.CacheFile](ctx, cache)
	store := fakeip.NewStore(ctx, logger, netip.MustParsePrefix("198.18.0.0/15"), netip.Prefix{})
	if err := store.Start(); err != nil {
		t.Fatal(err)
	}
	return cache, store
}

// os.Exit intentionally bypasses Store.Close and CacheFile.Close, as a killed Android process does.
func TestFakeIPCrashWriter(t *testing.T) {
	path := os.Getenv("OWNBOX_FAKEIP_TEST_DB")
	if path == "" {
		t.Skip("subprocess only")
	}
	interval, err := time.ParseDuration(os.Getenv("OWNBOX_FAKEIP_TEST_INTERVAL"))
	if err != nil {
		t.Fatal(err)
	}
	_, store := openFakeIPStore(t, path, interval)
	if _, err := store.Create("persistence.example", false); err != nil {
		t.Fatal(err)
	}
	time.Sleep(2 * time.Second)
	os.Exit(0)
}

func TestFakeIPSurvivesProcessExit(t *testing.T) {
	for _, interval := range []time.Duration{0, time.Second} {
		t.Run(interval.String(), func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "cache.db")
			cmd := exec.Command(os.Args[0], "-test.run=^TestFakeIPCrashWriter$")
			cmd.Env = append(os.Environ(), "OWNBOX_FAKEIP_TEST_DB="+path,
				"OWNBOX_FAKEIP_TEST_INTERVAL="+interval.String())
			if output, err := cmd.CombinedOutput(); err != nil {
				t.Fatalf("writer: %v\n%s", err, output)
			}
			cache, store := openFakeIPStore(t, path, interval)
			defer cache.Close()
			defer store.Close()
			domain, found := store.Lookup(netip.MustParseAddr("198.18.0.2"))
			wantFound := interval > 0
			if found != wantFound || (found && domain != "persistence.example") {
				t.Fatalf("interval=%s: found=%v domain=%q, want found=%v", interval, found, domain, wantFound)
			}
			t.Logf("after process exit: interval=%s, mapping recovered=%v", interval, found)
		})
	}
}
