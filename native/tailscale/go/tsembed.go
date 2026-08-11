// Package tsembed embeds a single Tailscale node (tsnet) behind a minimal
// API that can be exposed to Android through gomobile bind.
//
// The public surface is intentionally tiny: Start, Stop, Status. Android/Kotlin
// code must not depend on Tailscale internals, and Tailscale code must not be
// mixed with Frigate or playback concerns.
package tsembed

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/netip"
	urlpkg "net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/miekg/dns"
	"tailscale.com/tsnet"
	"tailscale.com/util/dnsname"
	netstack "tailscale.com/wgengine/netstack"
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

// HttpGet performs an HTTP GET over the embedded tailnet. The request is made
// exclusively with a client whose dialer routes through the tailscale engine
// (tsdial.UserDial / netstack), so it can never fall back to the OS network.
// The node must already be Running (see Start/Status).
//
// DNS resolution for hostnames is performed over the tunnel against the
// nameservers configured in the Tailscale admin console (see resolveHost):
// tsnet's dialer only understands MagicDNS names and otherwise falls back to
// the system resolver, which on Android does not know split-DNS domains such
// as a homelab "omni.corp".
func HttpGet(url string, timeoutMs int64) (string, error) {
	mu.Lock()
	s, running := server, started
	mu.Unlock()
	if s == nil || !running {
		return "", errors.New("tsembed: node not running")
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	u, err := urlpkg.Parse(url)
	if err != nil {
		return "", fmt.Errorf("tsembed: parse url: %w", err)
	}
	host := u.Hostname()
	port := u.Port()
	if port == "" {
		if u.Scheme == "https" {
			port = "443"
		} else {
			port = "80"
		}
	}

	ip, err := resolveHost(ctx, s, host)
	if err != nil {
		return "", err
	}

	portNum, err := strconv.Atoi(port)
	if err != nil || portNum < 1 || portNum > 65535 {
		return "", fmt.Errorf("tsembed: invalid port %q", port)
	}

	client := &http.Client{
		Transport: &http.Transport{
			DialContext: func(dctx context.Context, network, _ string) (net.Conn, error) {
				return dialNetstackTCP(dctx, s, ip, portNum)
			},
		},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return "", fmt.Errorf("tsembed: build request: %w", err)
	}
	req.Host = host
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("tsembed: GET %s: %w", url, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("tsembed: GET %s: HTTP %s", url, resp.Status)
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("tsembed: read response: %w", err)
	}
	return string(body), nil
}

// resolveHost resolves host to an IP. Literal IPs pass through; hostnames are
// resolved against the nameservers configured in the Tailscale admin console
// so that split-DNS domains served by a homelab DNS (such as "omni.corp") work
// over the tunnel.
//
// The query is sent over a netstack socket ([tsnet.Server.ListenPacket]) that
// routes through the tunnel, rather than through the tailnet resolver's
// forwarder: on Android the forwarder reaches upstream resolvers with OS
// sockets (SystemDial), which cannot reach a LAN DNS IP from outside the home
// network unless the OS routing table sends it through a TUN.
func resolveHost(ctx context.Context, s *tsnet.Server, host string) (netip.Addr, error) {
	if ip, err := netip.ParseAddr(host); err == nil {
		return ip, nil
	}
	mgr := s.Sys().DNSManager.Get()
	if mgr == nil {
		return netip.Addr{}, errors.New("tsembed: tailnet DNS manager unavailable")
	}
	res := mgr.Resolver()
	if res == nil {
		return netip.Addr{}, errors.New("tsembed: tailnet DNS resolver unavailable")
	}

	fqdn, err := dnsname.ToFQDN(host)
	if err != nil {
		return netip.Addr{}, fmt.Errorf("tsembed: invalid hostname %q: %w", host, err)
	}
	upstreams := res.GetUpstreamResolvers(fqdn)
	if len(upstreams) == 0 {
		return resolveHostSystem(ctx, host)
	}

	q := new(dns.Msg)
	q.SetQuestion(dns.Fqdn(host), dns.TypeA)
	bs, err := q.Pack()
	if err != nil {
		return netip.Addr{}, fmt.Errorf("tsembed: pack dns query: %w", err)
	}

	var lastErr error
	for _, up := range upstreams {
		if err := ctx.Err(); err != nil {
			return netip.Addr{}, fmt.Errorf("tsembed: tailnet DNS lookup for %q: %w", host, err)
		}
		ipp, err := parseUpstream(up.Addr)
		if err != nil {
			lastErr = err
			continue
		}
		addr, err := queryTunnelDNS(ctx, s, ipp, bs)
		if err != nil {
			lastErr = err
			continue
		}
		logf("resolved %q -> %s via upstream %s", host, addr, ipp)
		return addr, nil
	}
	if lastErr == nil {
		lastErr = errors.New("no usable upstream resolvers")
	}
	return netip.Addr{}, fmt.Errorf("tsembed: tailnet DNS lookup for %q: %w", host, lastErr)
}

// parseUpstream converts a tailnet DNS resolver address to an IP:port. Plain
// IPs imply port 53; DoH/DoH-over-WireGuard schemes are not supported here.
func parseUpstream(addr string) (netip.AddrPort, error) {
	if strings.Contains(addr, "://") {
		return netip.AddrPort{}, fmt.Errorf("unsupported DNS upstream %q", addr)
	}
	host, port := addr, "53"
	if h, p, err := net.SplitHostPort(addr); err == nil {
		host, port = h, p
	}
	ip, err := netip.ParseAddr(host)
	if err != nil {
		return netip.AddrPort{}, fmt.Errorf("parse DNS upstream %q: %w", addr, err)
	}
	portNum, err := strconv.Atoi(port)
	if err != nil || portNum < 1 || portNum > 65535 {
		return netip.AddrPort{}, fmt.Errorf("invalid DNS upstream port %q", port)
	}
	return netip.AddrPortFrom(ip, uint16(portNum)), nil
}

// queryTunnelDNS sends an A query to upstream over the tunnel and returns the
// first A record. The netstack socket routes subnet destinations through the
// tailnet, so a homelab DNS reachable only inside the LAN still answers. A TCP
// DNS fallback mirrors the official client's behavior: some homelab resolvers
// do not answer plain UDP queries coming through a subnet router.
func queryTunnelDNS(ctx context.Context, s *tsnet.Server, upstream netip.AddrPort, query []byte) (netip.Addr, error) {
	out, err := udpDNSQuery(ctx, s, upstream, query)
	if err != nil {
		out, err = tcpDNSQuery(ctx, s, upstream, query)
		if err != nil {
			return netip.Addr{}, err
		}
	}
	return parseDNSAnswer(query, out, upstream)
}

// udpDNSQuery sends a raw DNS query over a netstack UDP socket bound to the
// node's tailnet IP, so the packet travels through the tunnel.
func udpDNSQuery(ctx context.Context, s *tsnet.Server, upstream netip.AddrPort, query []byte) ([]byte, error) {
	network := "udp4"
	ip4, ip6 := s.TailscaleIPs()
	bindAddr := ip4
	if upstream.Addr().Is6() {
		network = "udp6"
		bindAddr = ip6
	}
	if !bindAddr.IsValid() || bindAddr.IsUnspecified() {
		return nil, errors.New("tsembed: node has no tailnet IP to bind the dns socket")
	}
	bindHost := net.JoinHostPort(bindAddr.String(), "0")
	pc, err := s.ListenPacket(network, bindHost)
	if err != nil {
		return nil, fmt.Errorf("tsembed: open tunnel dns socket: %w", err)
	}
	defer pc.Close()

	queryCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	if dl, ok := queryCtx.Deadline(); ok {
		if err := pc.SetDeadline(dl); err != nil {
			return nil, fmt.Errorf("tsembed: set dns deadline: %w", err)
		}
	}

	if _, err := pc.WriteTo(query, net.UDPAddrFromAddrPort(upstream)); err != nil {
		return nil, fmt.Errorf("tsembed: write dns query to %s: %w", upstream, err)
	}
	buf := make([]byte, 4096)
	n, _, err := pc.ReadFrom(buf)
	if err != nil {
		return nil, fmt.Errorf("tsembed: read dns answer from %s over udp: %w", upstream, err)
	}
	return buf[:n], nil
}

// tcpDNSQuery sends the query over DNS-over-TCP through the tunnel (2-byte
// length prefix framing), using the same netstack dial path proven for HTTP.
func tcpDNSQuery(ctx context.Context, s *tsnet.Server, upstream netip.AddrPort, query []byte) ([]byte, error) {
	queryCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	conn, err := dialNetstackTCP(queryCtx, s, upstream.Addr(), int(upstream.Port()))
	if err != nil {
		return nil, fmt.Errorf("tsembed: dial dns upstream %s over tcp: %w", upstream, err)
	}
	defer conn.Close()

	framed := make([]byte, 2+len(query))
	binary.BigEndian.PutUint16(framed, uint16(len(query)))
	copy(framed[2:], query)
	if _, err := conn.Write(framed); err != nil {
		return nil, fmt.Errorf("tsembed: write dns query to %s over tcp: %w", upstream, err)
	}
	var length uint16
	if err := binary.Read(conn, binary.BigEndian, &length); err != nil {
		return nil, fmt.Errorf("tsembed: read dns length from %s over tcp: %w", upstream, err)
	}
	out := make([]byte, length)
	if _, err := io.ReadFull(conn, out); err != nil {
		return nil, fmt.Errorf("tsembed: read dns answer from %s over tcp: %w", upstream, err)
	}
	return out, nil
}

// parseDNSAnswer extracts the first A record from a DNS response, verifying
// that the response matches the query id.
func parseDNSAnswer(query, out []byte, upstream netip.AddrPort) (netip.Addr, error) {
	var resp dns.Msg
	if err := resp.Unpack(out); err != nil {
		return netip.Addr{}, fmt.Errorf("tsembed: parse dns response: %w", err)
	}
	if resp.Id != binary.BigEndian.Uint16(query[:2]) {
		return netip.Addr{}, fmt.Errorf("tsembed: dns response id mismatch")
	}
	if resp.Rcode != dns.RcodeSuccess {
		return netip.Addr{}, fmt.Errorf("tsembed: dns response from %s: %s", upstream, dns.RcodeToString[resp.Rcode])
	}
	for _, rr := range resp.Answer {
		if a, ok := rr.(*dns.A); ok {
			if ip, ok := netip.AddrFromSlice(a.A); ok {
				return ip.Unmap(), nil
			}
		}
	}
	return netip.Addr{}, errors.New("tsembed: no A record in dns answer")
}

// dialNetstackTCP dials a TCP connection through the tunnel. The netstack
// dialer routes both tailnet IPs and subnet-routed destinations through the
// tunnel, unlike tsdial.UserDial which on Android falls back to the system
// dialer for non-tailnet IPs unless the node has the "user-dial-routes"
// attribute.
func dialNetstackTCP(ctx context.Context, s *tsnet.Server, ip netip.Addr, port int) (net.Conn, error) {
	ns, ok := s.Sys().Netstack.Get().(*netstack.Impl)
	if !ok || ns == nil {
		return nil, errors.New("tsembed: netstack unavailable")
	}
	return ns.DialContextTCP(ctx, netip.AddrPortFrom(ip, uint16(port)))
}

// resolveHostSystem is a fallback for hostnames not covered by the tailnet DNS
// configuration.
func resolveHostSystem(ctx context.Context, host string) (netip.Addr, error) {
	ips, err := net.DefaultResolver.LookupNetIP(ctx, "ip4", host)
	if err != nil {
		return netip.Addr{}, fmt.Errorf("tsembed: system DNS lookup for %q: %w", host, err)
	}
	if len(ips) == 0 {
		return netip.Addr{}, fmt.Errorf("tsembed: system DNS lookup for %q: no A record", host)
	}
	return ips[0].Unmap(), nil
}

func mustJSON(st status) string {
	b, err := json.Marshal(st)
	if err != nil {
		return fmt.Sprintf(`{"state":%q,"error":%q}`, stateFailed, err.Error())
	}
	return string(b)
}
