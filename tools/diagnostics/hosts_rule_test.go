package diagnostics

// Run against the pinned core:
// cd ../sing-box-ref1nd
// go test -tags with_external_windivert ../ownbox_fork/tools/diagnostics/hosts_rule_test.go -v
import (
	"context"
	"fmt"
	"strings"
	"testing"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service"

	"github.com/miekg/dns"
)

// Both DNS servers are local hosts transports; this test makes no network queries.
// The first rule mirrors the app's query-type rule that enables the new DNS rule mode.
const hostsConfig = `{
  "log": {"disabled": true},
  "dns": {
    "servers": [
      {"type":"hosts", "tag":"dns-hosts", "predefined":{
        "example.com":["1.1.1.1","1.1.1.2","2001:db8::1"]}},
      {"type":"hosts", "tag":"fallback", "predefined":{
        "example.com":["192.0.2.99"], "www.example.com":["192.0.2.2"],
        "unlisted.example":["192.0.2.3"]}}
    ],
    "rules": [
      {"query_type":["HTTPS"], "action":"reject"},
      %s
    ],
    "final":"fallback"
  },
  "outbounds":[{"type":"direct", "tag":"direct"}]
}`

func TestHostsRuleWithCurrentCore(t *testing.T) {
	for _, fixed := range []bool{false, true} {
		t.Run(fmt.Sprintf("fixed=%v", fixed), func(t *testing.T) {
			rule := `{"server":"dns-hosts","ip_accept_any":true}`
			if fixed {
				rule = `{"server":"dns-hosts","domain":["example.com"],"query_type":["A","AAAA"]}`
			}
			ctx := include.Context(service.ContextWithDefaultRegistry(context.Background()))
			var options option.Options
			if err := json.UnmarshalContext(ctx, []byte(fmt.Sprintf(hostsConfig, rule)), &options); err != nil {
				t.Fatal(err)
			}
			instance, err := box.New(box.Options{Context: ctx, Options: options})
			if !fixed {
				if err == nil {
					instance.Close()
					t.Fatal("old hosts rule unexpectedly passed validation")
				}
				if !strings.Contains(err.Error(), "require match_response to be enabled") {
					t.Fatal(err)
				}
				t.Log("reproduced the reported DNS rule validation error")
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			defer instance.Close()
			if err = instance.Start(); err != nil {
				t.Fatal(err)
			}
			router := service.FromContext[adapter.DNSRouter](ctx)
			for _, test := range []struct {
				name  string
				qtype uint16
				want  string
			}{
				{"example.com.", dns.TypeA, "1.1.1.1,1.1.1.2"},
				{"EXAMPLE.COM.", dns.TypeAAAA, "2001:db8::1"},
				{"www.example.com.", dns.TypeA, "192.0.2.2"},
				{"unlisted.example.", dns.TypeA, "192.0.2.3"},
			} {
				message := new(dns.Msg).SetQuestion(test.name, test.qtype)
				response, err := router.Exchange(ctx, message, adapter.DNSQueryOptions{})
				if err != nil {
					t.Fatal(err)
				}
				var addresses []string
				for _, rr := range response.Answer {
					switch rr := rr.(type) {
					case *dns.A:
						addresses = append(addresses, rr.A.String())
					case *dns.AAAA:
						addresses = append(addresses, rr.AAAA.String())
					}
				}
				if got := strings.Join(addresses, ","); got != test.want {
					t.Fatalf("%s %d: got %q, want %q", test.name, test.qtype, got, test.want)
				}
			}
		})
	}
}
