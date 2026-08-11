package tsembed

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	urlpkg "net/url"
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

func TestHttpGetRequiresRunningNode(t *testing.T) {
	if _, err := HttpGet("http://frigate:5000/api/version", 1000); err == nil {
		t.Fatal("HttpGet must fail when the node is not running")
	}
}

func TestHttpGetBytesRequiresRunningNode(t *testing.T) {
	if _, err := HttpGetBytes("http://frigate:5000/api/version", 1000); err == nil {
		t.Fatal("HttpGetBytes must fail when the node is not running")
	}
}

func TestHTTPResultFromResponse(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/ok", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("#EXTM3U\n"))
	})
	mux.HandleFunc("/missing", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte("nope"))
	})
	mux.HandleFunc("/redirect", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/ok", http.StatusFound)
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	client := newClient(func(ctx context.Context, network, addr string) (net.Conn, error) {
		return (&net.Dialer{}).DialContext(ctx, network, addr)
	})

	t.Run("captures status content type and body", func(t *testing.T) {
		u, err := urlpkg.Parse(srv.URL + "/ok")
		if err != nil {
			t.Fatal(err)
		}
		resp, err := doGet(context.Background(), client, u, u.Hostname())
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		res, err := httpResultFromResponse(resp)
		if err != nil {
			t.Fatal(err)
		}
		if res.StatusCode != http.StatusOK {
			t.Fatalf("StatusCode = %d, want 200", res.StatusCode)
		}
		if res.ContentType != "application/vnd.apple.mpegurl" {
			t.Fatalf("ContentType = %q, want application/vnd.apple.mpegurl", res.ContentType)
		}
		if string(res.Body) != "#EXTM3U\n" {
			t.Fatalf("Body = %q, want %q", string(res.Body), "#EXTM3U\n")
		}
		if res.FinalURL != srv.URL+"/ok" {
			t.Fatalf("FinalURL = %q, want %q", res.FinalURL, srv.URL+"/ok")
		}
	})

	t.Run("non-2xx is not an error and preserves the status", func(t *testing.T) {
		u, err := urlpkg.Parse(srv.URL + "/missing")
		if err != nil {
			t.Fatal(err)
		}
		resp, err := doGet(context.Background(), client, u, u.Hostname())
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		res, err := httpResultFromResponse(resp)
		if err != nil {
			t.Fatalf("non-2xx must not be an error: %v", err)
		}
		if res.StatusCode != http.StatusNotFound {
			t.Fatalf("StatusCode = %d, want 404", res.StatusCode)
		}
	})

	t.Run("redirects are followed and FinalURL is the final location", func(t *testing.T) {
		u, err := urlpkg.Parse(srv.URL + "/redirect")
		if err != nil {
			t.Fatal(err)
		}
		resp, err := doGet(context.Background(), client, u, u.Hostname())
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		res, err := httpResultFromResponse(resp)
		if err != nil {
			t.Fatal(err)
		}
		if res.StatusCode != http.StatusOK {
			t.Fatalf("StatusCode after redirect = %d, want 200", res.StatusCode)
		}
		if res.FinalURL != srv.URL+"/ok" {
			t.Fatalf("FinalURL after redirect = %q, want %q", res.FinalURL, srv.URL+"/ok")
		}
	})
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
