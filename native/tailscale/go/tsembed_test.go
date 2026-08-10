package tsembed

import (
	"encoding/json"
	"errors"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"tailscale.com/tsnet"
)

func TestStartFailureAllowsRetry(t *testing.T) {
	var calls atomic.Int32
	startServer = func(*tsnet.Server) error {
		calls.Add(1)
		return errors.New("simulated start failure")
	}
	defer func() { startServer = func(s *tsnet.Server) error { return s.Start() } }()
	defer Stop()

	dir := t.TempDir()
	if err := Start("test-host", "", dir); err != nil {
		t.Fatalf("first Start returned unexpected error: %v", err)
	}

	if st := waitForState(t, stateFailed); !strings.Contains(st.Error, "simulated start failure") {
		t.Fatalf("Status did not surface the start error, got: %s", st.Error)
	}

	// Status polling must never re-invoke start.
	for i := 0; i < 10; i++ {
		waitForState(t, stateFailed)
	}
	if calls.Load() != 1 {
		t.Fatalf("Status polling re-triggered start: startServer called %d times, want 1", calls.Load())
	}

	if err := Start("test-host", "", dir); err != nil {
		t.Fatalf("retry Start returned error, lifecycle was not reset: %v", err)
	}
	if st := waitForState(t, stateFailed); !strings.Contains(st.Error, "simulated start failure") {
		t.Fatalf("Status did not surface the retried start error, got: %s", st.Error)
	}
	if calls.Load() != 2 {
		t.Fatalf("startServer called %d times, want 2", calls.Load())
	}
}

func waitForState(t *testing.T, want string) status {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		var st status
		if err := json.Unmarshal([]byte(Status()), &st); err == nil && st.State == want {
			return st
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for state %q, last Status: %s", want, Status())
	return status{}
}
