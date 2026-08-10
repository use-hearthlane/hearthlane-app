// Package tsembed embeds a single Tailscale node (tsnet) behind a minimal
// API that can be exposed to Android through gomobile bind.
//
// The public surface is intentionally tiny: Start, Stop, Status. Android/Kotlin
// code must not depend on Tailscale internals, and Tailscale code must not be
// mixed with Frigate or playback concerns.
package tsembed

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"tailscale.com/tsnet"
)

// Connectivity states reported by Status, matching the application-oriented
// states described in docs/PLAN.md (core/connectivity).
const (
	stateDisconnected   = "Disconnected"
	stateAuthenticating = "Authenticating"
	stateConnecting     = "Connecting"
	stateConnected      = "Connected"
	stateFailed         = "Failed"
	stateStopped        = "Stopped"
)

// status is the JSON payload returned by Status.
type status struct {
	State   string `json:"state"`
	AuthURL string `json:"authUrl,omitempty"`
	Error   string `json:"error,omitempty"`
}

var (
	mu        sync.Mutex
	server    *tsnet.Server
	cancel    context.CancelFunc
	startDone chan struct{}
	started   bool
	lastErr   error

	// startServer is swapped in tests to simulate tsnet startup outcomes
	// without requiring a real tailnet connection.
	startServer = func(s *tsnet.Server) error { return s.Start() }
)

// Start launches an embedded Tailscale node in the background and returns
// immediately. hostname is the tailnet hostname, authKey may be empty to use
// the interactive login URL flow, and stateDir must be a persistent
// application directory that stores the node identity across launches.
//
// Start is only ever called by the user (via the Start/Retry button). It is
// never triggered by Status polling or by the UI loop. On a failed startup the
// partial instance is released and the node returns to the Failed state; a new
// attempt requires another explicit Start call.
func Start(hostname, authKey, stateDir string) error {
	mu.Lock()
	defer mu.Unlock()
	if server != nil {
		return errors.New("tsembed: already started")
	}
	if err := os.MkdirAll(stateDir, 0o700); err != nil {
		return fmt.Errorf("tsembed: create state dir: %w", err)
	}
	// On Android there is no usable default location for Tailscale's log
	// state: no $TS_LOGS_DIR/$HOME/cache, cwd is "/", and /tmp does not exist.
	// logpolicy.LogsDir panics in that situation, so point it at a writable
	// subdirectory of the node state. Respect an explicitly set value.
	logsDir := filepath.Join(stateDir, "logs")
	if os.Getenv("TS_LOGS_DIR") == "" {
		if err := os.MkdirAll(logsDir, 0o700); err != nil {
			return fmt.Errorf("tsembed: create logs dir: %w", err)
		}
		if err := os.Setenv("TS_LOGS_DIR", logsDir); err != nil {
			return fmt.Errorf("tsembed: set TS_LOGS_DIR: %w", err)
		}
		logf("log state dir set to %q", logsDir)
	}

	ctx, cancelFn := context.WithCancel(context.Background())
	s := &tsnet.Server{
		Hostname: hostname,
		AuthKey:  authKey,
		Dir:      stateDir,
		Logf:     logf,
	}
	server = s
	cancel = cancelFn
	startDone = make(chan struct{})
	started = false
	lastErr = nil
	logf("start requested: hostname=%q stateDir=%q", hostname, stateDir)
	go func() {
		defer close(startDone)
		if err := startServer(s); err != nil {
			// Release every reference to the partial instance so the node
			// reports Failed and a later Start can begin from a clean slate.
			mu.Lock()
			if server == s {
				server, cancel, startDone = nil, nil, nil
				started = false
				lastErr = err
			}
			mu.Unlock()
			cancelFn()
			logf("start failed: %v", err)
			logf("cleanup after failed start")
			cleanupFailedServer(s)
			return
		}
		mu.Lock()
		if server == s {
			started = true
		}
		mu.Unlock()
		logf("start succeeded")
		<-ctx.Done()
		logf("shutting down node")
		if err := s.Close(); err != nil {
			logf("close error: %v", err)
		}
		mu.Lock()
		if server == s {
			server, cancel, startDone = nil, nil, nil
			started = false
		}
		mu.Unlock()
	}()
	return nil
}

// cleanupFailedServer releases the partially-created tsnet instance after a
// failed Start. tsnet's Close is generally safe once Start has returned, but
// it is wrapped in a recover because very early failures can leave internal
// fields unset.
func cleanupFailedServer(s *tsnet.Server) {
	defer func() {
		if r := recover(); r != nil {
			logf("cleanup after failed start: close panicked: %v", r)
		}
	}()
	if err := s.Close(); err != nil {
		logf("cleanup after failed start: close error: %v", err)
	}
}

// Stop shuts the embedded node down and waits for the background goroutine to
// finish.
func Stop() error {
	mu.Lock()
	s, done := server, startDone
	mu.Unlock()
	if s == nil {
		return errors.New("tsembed: not started")
	}
	logf("stop requested")
	if cancel != nil {
		cancel()
	}
	if done != nil {
		<-done
	}
	mu.Lock()
	server, cancel, startDone = nil, nil, nil
	started = false
	lastErr = nil
	mu.Unlock()
	logf("stopped")
	return nil
}

// Status reports the current node state as a JSON string. Polling is cheap and
// is intended for a UI loop. It never starts the node: when no node is present
// it reports Failed/Disconnected/Stopped, and while startup is in progress it
// reports Connecting without touching the tsnet instance.
func Status() string {
	mu.Lock()
	s, last, isStarted := server, lastErr, started
	mu.Unlock()
	st := status{State: stateDisconnected}
	if s == nil {
		if last != nil {
			st.State = stateFailed
			st.Error = last.Error()
		}
		return mustJSON(st)
	}

	if !isStarted {
		st.State = stateConnecting
		return mustJSON(st)
	}

	lc, err := s.LocalClient()
	if err != nil {
		st.State = stateConnecting
		st.Error = fmt.Sprintf("local client unavailable: %v", err)
		return mustJSON(st)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	ipnStatus, err := lc.Status(ctx)
	if err != nil {
		st.State = stateConnecting
		st.Error = fmt.Sprintf("status unavailable: %v", err)
		return mustJSON(st)
	}
	switch ipnStatus.BackendState {
	case "NeedsLogin", "NeedsMachineAuth":
		st.State = stateAuthenticating
		st.AuthURL = ipnStatus.AuthURL
	case "Running":
		st.State = stateConnected
	case "Stopped":
		st.State = stateStopped
	default:
		st.State = stateConnecting
	}
	return mustJSON(st)
}

func logf(format string, args ...any) {
	log.Printf("tsembed: "+format, args...)
}

func mustJSON(st status) string {
	b, err := json.Marshal(st)
	if err != nil {
		return fmt.Sprintf(`{"state":%q,"error":%q}`, stateFailed, err.Error())
	}
	return string(b)
}
