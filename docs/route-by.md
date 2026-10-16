# Routing by endpoint arguments

Conjure endpoints can declare ordered selectors:

```yaml
getMetadataHistoryInfo:
  http: POST /tables/{tableRid}/metadata-history
  route-by: [tableRid, options.enrollmentRid]
  args:
    tableRid:
      type: types.TableRid
      param-type: path
    options:
      type: MetadataOptions
      param-type: body
  returns: MetadataHistoryInfo
```

Generated blocking and asynchronous Dialogue clients extract these values using typed getters and compute a
composite routing hash before executing the request. The hash is stored only in local request metadata. It is
never added to HTTP headers, the URL, or the body. Witchcraft and Undertow need no routing support.

A request with a routing key automatically uses rendezvous hashing in Dialogue. Requests without one keep the
configured node-selection strategy. Explicit sticky sessions take precedence. Configure discovery with individual
node URIs (or a hostname resolving to individual node IPs): an opaque load balancer or proxy remains responsible
for any selection behind its address, so Dialogue cannot provide per-node affinity through it.

The same key and target set select the same preferred node across clients, regardless of discovery order.
Target identity includes the service URI and resolved IP address, when available. Live discovery updates rebuild
the candidate set. Adding a node only moves keys that select that node; removing one only moves its keys.
Use matching URI spellings and discovery configuration across clients for matching target identities.

Affinity is best effort. If the preferred node is concurrency limited, another eligible node can serve the request.
On a transport failure, retryable server failure, or ordinary unavailable/retry-other response, an eligible retry
tries the next ranked target. Custom QoS and 429 responses retain affinity. Existing Dialogue retry policy controls
whether a retry occurs, including retry limits, backoff, timeouts, non-repeatable bodies, and DO_NOT_RETRY signals.
When all current targets have failed, subsequent permitted retries may try them again. Each new logical call starts
with its preferred node; this is affinity, not exclusive ownership or a circuit breaker.

Selectors start at path or body arguments and traverse object fields with dots. Multiple selectors are combined in
list order, including mixtures of path and structured-body fields. Aliases and optional intermediate fields are
supported. Missing optionals contribute an explicit absent component. Supported leaves are strings, integers,
doubles, booleans, RIDs, UUIDs, datetimes, safe longs, enums, bearer tokens, and binary fields within structured bodies.
Entire binary request bodies, header/query selectors, collections, objects, unions, external types and `any` are
not supported as key components. An unselected binary request body is unaffected and is not read for hashing.

## Hash encoding

Version 1 is a big-endian 32-bit version (`1`), 32-bit component count, and for each component a 32-bit type ID,
32-bit UTF-8 byte length, and value bytes. Explicit lengths avoid concatenation ambiguities. Type IDs are:
absent=0, string=1, integer=2, double=3, boolean=4, RID=5, UUID=6, datetime=7, enum=8, safelong=9, binary=10,
bearertoken=11. Numbers use their canonical decimal representation, except doubles use the unsigned decimal
representation of `Double.doubleToLongBits`. Datetimes are normalized to an instant. Binary fields use padded
standard Base64 of the remaining bytes without changing buffer state; bearer tokens use their token value.
Absent values have an empty payload. Endpoint and selector names do not contribute, allowing matching keys on
read/write endpoints to select the same node.

The request retains the first 64 bits of Murmur3-128 over this encoding. Values and hashes have redacted string
representations. Rendezvous scores use Murmur3-128 over the key hash and stable target hash, compare the first
64 bits as unsigned, and break ties using the target URI/address. Hashing supplies deterministic distribution;
it does not provide encryption. No routing material is transmitted beyond the endpoint's ordinary arguments.

## Building unreleased changes together

This feature requires the Conjure compiler/IR with `route-by`, the Conjure Java Dialogue generator, and the updated
Dialogue target/core APIs. Update Dialogue before compiling newly generated clients. Existing endpoints and clients
without routing metadata retain their behavior. Generated clients need the new `dialogue-target` at runtime.

For a local build before publication, from `conjure-java` run:

```sh
./gradlew --include-build ../conjure -I ../dialogue/gradle/route-by-local.gradle :conjure-java-core:test
```

The helper substitutes only `dialogue-target` and retains Conjure Java's existing dependency locks. Test Dialogue
itself with its own Gradle wrapper (`:dialogue-target:test :dialogue-core:test`). For an uncommitted checkout, a valid
`CIRCLE_TAG` build version avoids the existing user-agent tests rejecting Gradle's `.dirty` version suffix.
