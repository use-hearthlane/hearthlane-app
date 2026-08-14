package tsembed

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	urlpkg "net/url"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/miekg/dns"
	"tailscale.com/tsnet"
	"tailscale.com/types/dnstype"
	"tailscale.com/util/dnsname"
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

// --- DNS cache and transport policy tests ---

const testUpstreamAddr = "192.168.10.2"

func testUpstream() *dnstype.Resolver { return &dnstype.Resolver{Addr: testUpstreamAddr} }

// resetDNSCache replaces the shared cache with a fresh one so tests do not leak
// entries into each other.
func resetDNSCache(t *testing.T) {
	t.Helper()
	prev := dnsCacheInstance
	dnsCacheInstance = &dnsCache{entries: make(map[string]dnsCacheEntry)}
	t.Cleanup(func() { dnsCacheInstance = prev })
}

// installDNSStubs replaces the resolveHost dependencies with stubs and restores
// the production functions when the test ends. The nil *tsnet.Server is never
// touched because both stubs ignore it.
func installDNSStubs(
	t *testing.T,
	upstreams func(fqdn dnsname.FQDN) ([]*dnstype.Resolver, error),
	query func(ctx context.Context, upstream netip.AddrPort) (dnsAnswer, error),
) {
	t.Helper()
	prevUp := dnsUpstreamsFor
	prevQuery := dnsQuery
	dnsUpstreamsFor = func(_ *tsnet.Server, fqdn dnsname.FQDN) ([]*dnstype.Resolver, error) {
		return upstreams(fqdn)
	}
	dnsQuery = func(ctx context.Context, _ *tsnet.Server, upstream netip.AddrPort, _ []byte) (dnsAnswer, error) {
		return query(ctx, upstream)
	}
	t.Cleanup(func() {
		dnsUpstreamsFor = prevUp
		dnsQuery = prevQuery
	})
}

// captureLogs redirects the standard logger used by logf to a buffer.
func captureLogs(t *testing.T) *bytes.Buffer {
	t.Helper()
	var buf bytes.Buffer
	prev := log.Writer()
	log.SetOutput(&buf)
	t.Cleanup(func() { log.SetOutput(prev) })
	return &buf
}

func mustAddr(s string) netip.Addr { return netip.MustParseAddr(s) }

func TestResolveHostFirstLookupQueriesAndCaches(t *testing.T) {
	resetDNSCache(t)
	var queries int
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			queries++
			return dnsAnswer{ips: []netip.Addr{mustAddr("192.168.10.13")}, ttl: 60}, nil
		},
	)

	ip, err := resolveHost(context.Background(), nil, "site.omni.corp")
	if err != nil {
		t.Fatalf("first resolution failed: %v", err)
	}
	if ip != mustAddr("192.168.10.13") {
		t.Fatalf("first resolution IP = %v, want 192.168.10.13", ip)
	}
	if queries != 1 {
		t.Fatalf("first resolution performed %d queries, want 1", queries)
	}

	ip2, err := resolveHost(context.Background(), nil, "site.omni.corp")
	if err != nil {
		t.Fatalf("cached resolution failed: %v", err)
	}
	if ip2 != ip {
		t.Fatalf("cached resolution IP = %v, want %v", ip2, ip)
	}
	if queries != 1 {
		t.Fatalf("cached resolution must not query again, performed %d queries", queries)
	}
}

func TestResolveHostLogsCacheMissHitAndSuccess(t *testing.T) {
	resetDNSCache(t)
	SetDebugDNS(true)
	t.Cleanup(func() { SetDebugDNS(false) })
	buf := captureLogs(t)
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			return dnsAnswer{ips: []netip.Addr{mustAddr("192.168.10.13")}, ttl: 60}, nil
		},
	)

	if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err != nil {
		t.Fatal(err)
	}
	if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err != nil {
		t.Fatal(err)
	}

	got := buf.String()
	for _, want := range []string{
		`dns cache miss for "site.omni.corp"`,
		`dns cache hit for "site.omni.corp"`,
		`dns resolution succeeded: "site.omni.corp" -> [192.168.10.13] via 192.168.10.2:53 (ttl=60s)`,
	} {
		if !strings.Contains(got, want) {
			t.Errorf("logs missing %q; got:\n%s", want, got)
		}
	}
}

func TestResolveHostExpiredEntryQueriesAgain(t *testing.T) {
	resetDNSCache(t)
	var queries int
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			queries++
			return dnsAnswer{ips: []netip.Addr{mustAddr("192.168.10.13")}, ttl: 1}, nil
		},
	)

	for i := 0; i < 2; i++ {
		if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err != nil {
			t.Fatal(err)
		}
	}
	if queries != 1 {
		t.Fatalf("resolution inside the TTL performed %d queries, want 1", queries)
	}

	time.Sleep(1100 * time.Millisecond)
	if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err != nil {
		t.Fatal(err)
	}
	if queries != 2 {
		t.Fatalf("resolution after TTL expiry performed %d queries, want 2", queries)
	}
}

func TestResolveHostFailureIsNotCached(t *testing.T) {
	resetDNSCache(t)
	var queries int
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			queries++
			return dnsAnswer{}, errors.New("dial dns upstream over tcp: timeout")
		},
	)

	for i := 0; i < 2; i++ {
		if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err == nil {
			t.Fatal("failed resolution must return an error")
		}
	}
	if queries != 2 {
		t.Fatalf("failed resolutions must not be cached, performed %d queries, want 2", queries)
	}
}

func TestResolveHostEmptyAnswerIsNotCached(t *testing.T) {
	resetDNSCache(t)
	var queries int
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			queries++
			return dnsAnswer{}, nil
		},
	)

	for i := 0; i < 2; i++ {
		if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err == nil {
			t.Fatal("empty answer must surface an error")
		}
	}
	if queries != 2 {
		t.Fatalf("empty answers must not be cached, performed %d queries, want 2", queries)
	}
}

func TestResolveHostZeroTTLUsesFallbackTTL(t *testing.T) {
	resetDNSCache(t)
	var queries int
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			queries++
			return dnsAnswer{ips: []netip.Addr{mustAddr("192.168.10.13")}, ttl: 0}, nil
		},
	)

	for i := 0; i < 2; i++ {
		if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err != nil {
			t.Fatal(err)
		}
	}
	// The homelab resolver advertises TTL 0; the fallback must cache with the
	// conservative fixed TTL instead of disabling the cache.
	if queries != 1 {
		t.Fatalf("a zero-TTL answer must fall back to the fixed cache TTL, performed %d queries, want 1", queries)
	}
}

func TestResolveHostConcurrentMissesQueryOnce(t *testing.T) {
	resetDNSCache(t)
	var queries int
	installDNSStubs(t,
		func(dnsname.FQDN) ([]*dnstype.Resolver, error) { return []*dnstype.Resolver{testUpstream()}, nil },
		func(context.Context, netip.AddrPort) (dnsAnswer, error) {
			queries++
			return dnsAnswer{ips: []netip.Addr{mustAddr("192.168.10.13")}, ttl: 60}, nil
		},
	)

	start := make(chan struct{})
	const n = 8
	errCh := make(chan error, n)
	var wg sync.WaitGroup
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			if _, err := resolveHost(context.Background(), nil, "site.omni.corp"); err != nil {
				errCh <- err
			}
		}()
	}
	close(start)
	wg.Wait()
	close(errCh)
	for err := range errCh {
		t.Fatal(err)
	}
	if queries != 1 {
		t.Fatalf("concurrent misses for the same hostname performed %d queries, want 1 (collapsed)", queries)
	}
}

func TestResolveHostCacheEntriesArePerHostname(t *testing.T) {
	resetDNSCache(t)
	queries := map[string]int{}
	var mu sync.Mutex
	answers := map[string]dnsAnswer{
		"192.168.10.2:53": {ips: []netip.Addr{mustAddr("192.168.10.11")}, ttl: 60},
		"192.168.10.3:53": {ips: []netip.Addr{mustAddr("192.168.10.12")}, ttl: 60},
	}
	upstreamsForHost := map[string]string{
		"a.omni.corp.": "192.168.10.2",
		"b.omni.corp.": "192.168.10.3",
	}
	installDNSStubs(t,
		func(fqdn dnsname.FQDN) ([]*dnstype.Resolver, error) {
			addr, ok := upstreamsForHost[string(fqdn)]
			if !ok {
				return nil, fmt.Errorf("no upstream for %q", fqdn)
			}
			return []*dnstype.Resolver{{Addr: addr}}, nil
		},
		func(_ context.Context, up netip.AddrPort) (dnsAnswer, error) {
			mu.Lock()
			queries[up.String()]++
			mu.Unlock()
			ans, ok := answers[up.String()]
			if !ok {
				return dnsAnswer{}, fmt.Errorf("no answer for %s", up)
			}
			return ans, nil
		},
	)

	ipA, err := resolveHost(context.Background(), nil, "a.omni.corp")
	if err != nil {
		t.Fatal(err)
	}
	ipB, err := resolveHost(context.Background(), nil, "b.omni.corp")
	if err != nil {
		t.Fatal(err)
	}
	if ipA != mustAddr("192.168.10.11") || ipB != mustAddr("192.168.10.12") {
		t.Fatalf("unexpected IPs: a=%v b=%v", ipA, ipB)
	}

	// Case-insensitive, trailing-dot variants share a.omni.corp's entry.
	ipA2, err := resolveHost(context.Background(), nil, "A.Omni.Corp.")
	if err != nil {
		t.Fatal(err)
	}
	if ipA2 != ipA {
		t.Fatalf("normalized hostname returned %v, want %v", ipA2, ipA)
	}

	mu.Lock()
	defer mu.Unlock()
	if queries["192.168.10.2:53"] != 1 {
		t.Fatalf("hostname a resolved %d times, want 1 (cached)", queries["192.168.10.2:53"])
	}
	if queries["192.168.10.3:53"] != 1 {
		t.Fatalf("hostname b resolved %d times, want 1", queries["192.168.10.3:53"])
	}
}

func TestResolveHostLiteralIPSkipsResolver(t *testing.T) {
	resetDNSCache(t)
	prevUp := dnsUpstreamsFor
	prevQuery := dnsQuery
	dnsUpstreamsFor = func(*tsnet.Server, dnsname.FQDN) ([]*dnstype.Resolver, error) {
		t.Fatal("literal IP must not consult the upstream resolver")
		return nil, nil
	}
	dnsQuery = func(context.Context, *tsnet.Server, netip.AddrPort, []byte) (dnsAnswer, error) {
		t.Fatal("literal IP must not query DNS")
		return dnsAnswer{}, nil
	}
	t.Cleanup(func() {
		dnsUpstreamsFor = prevUp
		dnsQuery = prevQuery
	})

	ip, err := resolveHost(context.Background(), nil, "192.168.10.13")
	if err != nil {
		t.Fatal(err)
	}
	if ip != mustAddr("192.168.10.13") {
		t.Fatalf("IP = %v, want 192.168.10.13", ip)
	}
}

func TestDNSCacheConcurrentAccess(t *testing.T) {
	resetDNSCache(t)
	ip := mustAddr("192.168.10.13")
	c := dnsCacheInstance
	c.put("site.omni.corp", []netip.Addr{ip}, time.Minute)

	var wg sync.WaitGroup
	errCh := make(chan error, 8)
	for i := 0; i < 4; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 500; j++ {
				got, ok := c.get("site.omni.corp")
				if !ok || got != ip {
					errCh <- fmt.Errorf("corrupted read: ok=%v got=%v", ok, got)
					return
				}
			}
		}()
	}
	wg.Add(1)
	go func() {
		defer wg.Done()
		for j := 0; j < 500; j++ {
			c.put(fmt.Sprintf("host-%d.omni.corp", j%16), []netip.Addr{mustAddr("192.168.10.20")}, time.Minute)
		}
	}()
	wg.Wait()
	close(errCh)
	for err := range errCh {
		t.Fatal(err)
	}
}

func TestQueryTunnelDNSTransportPolicy(t *testing.T) {
	ctx := context.Background()
	query := buildQuery(t, "site.omni.corp")
	resp := cannedAAnswer(t, query, "192.168.10.13", 60)

	replaceTransports := func(t *testing.T, udp, tcp func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error)) {
		t.Helper()
		prevU, prevT := udpQueryFn, tcpQueryFn
		udpQueryFn = udp
		tcpQueryFn = tcp
		t.Cleanup(func() { udpQueryFn, tcpQueryFn = prevU, prevT })
	}

	t.Run("private upstream uses tcp first and does not touch udp when it succeeds", func(t *testing.T) {
		var order []string
		replaceTransports(t,
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "udp")
				t.Fatal("udp must not be attempted when tcp succeeds")
				return nil, nil
			},
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "tcp")
				return resp, nil
			},
		)
		got, err := queryTunnelDNS(ctx, nil, mustAddrPort("192.168.10.2:53"), query)
		if err != nil {
			t.Fatal(err)
		}
		if len(got.ips) != 1 || got.ips[0] != mustAddr("192.168.10.13") {
			t.Fatalf("answer IPs = %v", got.ips)
		}
		if strings.Join(order, ",") != "tcp" {
			t.Fatalf("transport order = %v, want [tcp]", order)
		}
	})

	t.Run("private upstream falls back to udp when tcp fails", func(t *testing.T) {
		var order []string
		replaceTransports(t,
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "udp")
				return resp, nil
			},
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "tcp")
				return nil, errors.New("tcp failed")
			},
		)
		got, err := queryTunnelDNS(ctx, nil, mustAddrPort("192.168.10.2:53"), query)
		if err != nil {
			t.Fatal(err)
		}
		if got.ips[0] != mustAddr("192.168.10.13") {
			t.Fatalf("answer IPs = %v", got.ips)
		}
		if strings.Join(order, ",") != "tcp,udp" {
			t.Fatalf("transport order = %v, want [tcp udp]", order)
		}
	})

	t.Run("public upstream keeps udp first", func(t *testing.T) {
		var order []string
		replaceTransports(t,
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "udp")
				return resp, nil
			},
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "tcp")
				t.Fatal("tcp must not be attempted when udp succeeds")
				return nil, nil
			},
		)
		if _, err := queryTunnelDNS(ctx, nil, mustAddrPort("8.8.8.8:53"), query); err != nil {
			t.Fatal(err)
		}
		if strings.Join(order, ",") != "udp" {
			t.Fatalf("transport order = %v, want [udp]", order)
		}
	})

	t.Run("public upstream falls back to tcp when udp fails", func(t *testing.T) {
		var order []string
		replaceTransports(t,
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "udp")
				return nil, errors.New("udp failed")
			},
			func(context.Context, *tsnet.Server, netip.AddrPort, []byte) ([]byte, error) {
				order = append(order, "tcp")
				return resp, nil
			},
		)
		if _, err := queryTunnelDNS(ctx, nil, mustAddrPort("8.8.8.8:53"), query); err != nil {
			t.Fatal(err)
		}
		if strings.Join(order, ",") != "udp,tcp" {
			t.Fatalf("transport order = %v, want [udp tcp]", order)
		}
	})
}

// buildQuery packs an A query for host.
func buildQuery(t *testing.T, host string) []byte {
	t.Helper()
	m := new(dns.Msg)
	m.SetQuestion(dns.Fqdn(host), dns.TypeA)
	bs, err := m.Pack()
	if err != nil {
		t.Fatalf("pack query: %v", err)
	}
	return bs
}

// cannedAAnswer builds a successful DNS response matching the given query with
// one A record at ip with ttl.
func cannedAAnswer(t *testing.T, query []byte, ip string, ttl uint32) []byte {
	t.Helper()
	var req dns.Msg
	if err := req.Unpack(query); err != nil {
		t.Fatalf("unpack query: %v", err)
	}
	resp := new(dns.Msg)
	resp.SetReply(&req)
	resp.Answer = append(resp.Answer, &dns.A{
		Hdr: dns.RR_Header{Name: req.Question[0].Name, Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: ttl},
		A:   net.ParseIP(ip).To4(),
	})
	bs, err := resp.Pack()
	if err != nil {
		t.Fatalf("pack answer: %v", err)
	}
	return bs
}

func mustAddrPort(s string) netip.AddrPort { return netip.MustParseAddrPort(s) }

func TestIsPrivateUpstream(t *testing.T) {
	cases := []struct {
		addr string
		want bool
	}{
		{"192.168.10.2", true},
		{"10.0.0.1", true},
		{"172.16.0.1", true},
		{"172.31.255.254", true},
		{"127.0.0.1", true},
		{"100.64.0.1", true},
		{"100.127.255.254", true},
		{"fd7a:115c:a1e0::1", true},
		{"100.63.0.1", false},
		{"100.128.0.1", false},
		{"8.8.8.8", false},
		{"2001:4860:4860::8888", false},
	}
	for _, tc := range cases {
		got := isPrivateUpstream(mustAddr(tc.addr))
		if got != tc.want {
			t.Errorf("isPrivateUpstream(%s) = %v, want %v", tc.addr, got, tc.want)
		}
	}
}

func TestParseDNSAnswerCollectsAllARecordsAndMinTTL(t *testing.T) {
	var req dns.Msg
	req.SetQuestion(dns.Fqdn("site.omni.corp"), dns.TypeA)
	query, err := req.Pack()
	if err != nil {
		t.Fatal(err)
	}
	resp := new(dns.Msg)
	resp.SetReply(&req)
	resp.Answer = []dns.RR{
		&dns.A{Hdr: dns.RR_Header{Name: "site.omni.corp.", Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 30}, A: net.ParseIP("192.168.10.11").To4()},
		&dns.A{Hdr: dns.RR_Header{Name: "site.omni.corp.", Rrtype: dns.TypeA, Class: dns.ClassINET, Ttl: 60}, A: net.ParseIP("192.168.10.12").To4()},
	}
	out, err := resp.Pack()
	if err != nil {
		t.Fatal(err)
	}
	ans, err := parseDNSAnswer(query, out, mustAddrPort("192.168.10.2:53"))
	if err != nil {
		t.Fatal(err)
	}
	if len(ans.ips) != 2 {
		t.Fatalf("answer IPs = %v, want 2 records", ans.ips)
	}
	if ans.ips[0] != mustAddr("192.168.10.11") || ans.ips[1] != mustAddr("192.168.10.12") {
		t.Fatalf("answer IPs = %v", ans.ips)
	}
	if ans.ttl != 30 {
		t.Fatalf("answer TTL = %d, want 30 (minimum across records)", ans.ttl)
	}
}

func TestCacheTTLCap(t *testing.T) {
	if got := (dnsAnswer{ttl: 3600}).cacheTTL(); got != dnsCacheTTL {
		t.Fatalf("cacheTTL(3600) = %v, want capped %v", got, dnsCacheTTL)
	}
	if got := (dnsAnswer{ttl: 30}).cacheTTL(); got != 30*time.Second {
		t.Fatalf("cacheTTL(30) = %v, want 30s", got)
	}
	if got := (dnsAnswer{ttl: 0}).cacheTTL(); got != dnsCacheTTL {
		t.Fatalf("cacheTTL(0) = %v, want %v (fallback to fixed TTL)", got, dnsCacheTTL)
	}
}

func TestCacheKeyNormalization(t *testing.T) {
	if cacheKey("SITE.OMNI.CORP.") != "site.omni.corp" {
		t.Fatalf("cacheKey uppercase = %q", cacheKey("SITE.OMNI.CORP."))
	}
	if cacheKey("site.omni.corp") != "site.omni.corp" {
		t.Fatalf("cacheKey plain = %q", cacheKey("site.omni.corp"))
	}
}
