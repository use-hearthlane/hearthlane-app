// Command hostcheck validates the tsembed wrapper on a desktop host, without
// requiring an Android device. It is a developer tool only and is not part of
// the Android application.
//
// It starts a real tsnet node in a temporary state directory and prints the
// Status JSON for a short period. The node is never authenticated: without an
// auth key it only reaches the interactive login state.
package main

import (
	"flag"
	"fmt"
	"os"
	"time"

	"homelab/poc/tsembed"
)

func main() {
	hostname := flag.String("hostname", "poc-hostcheck", "tailnet hostname")
	dir := flag.String("dir", "", "state dir (defaults to a temp dir)")
	run := flag.Duration("run", 15*time.Second, "how long to keep the node running")
	flag.Parse()

	if *dir == "" {
		*dir, _ = os.MkdirTemp("", "tsembed-hostcheck-")
	}
	fmt.Printf("starting tsembed hostname=%s dir=%s\n", *hostname, *dir)
	if err := tsembed.Start(*hostname, "", *dir); err != nil {
		fmt.Printf("Start error: %v\n", err)
		os.Exit(1)
	}
	defer func() {
		if err := tsembed.Stop(); err != nil {
			fmt.Printf("Stop error: %v\n", err)
		}
	}()

	deadline := time.Now().Add(*run)
	for time.Now().Before(deadline) {
		fmt.Println(tsembed.Status())
		time.Sleep(2 * time.Second)
	}
}
