# Metrics

## Dialogue Apache Hc5 Client

`com.palantir.dialogue:dialogue-apache-hc5-client`

### dialogue.client
Dialogue client response metrics provided by the Apache client channel.
- `dialogue.client.response.leak` (meter): Rate that responses are garbage collected without being closed. This should only occur in the case of a programming error.
  - `client-name`
  - `client-type` values (`apache-hc5`)
  - `service-name`
  - `endpoint`
- `dialogue.client.server.timing.overhead` (timer): Difference in request time reported by the client and by the server. This metric is only reported when the remote server provides a `Server-Timing` response header with a timing value for `server`.
  - `client-name`
  - `client-type` values (`apache-hc5`)
- `dialogue.client.create` (meter): Marked every time a new client is created.
  - `client-name`
  - `client-type` values (`apache-hc5`)
- `dialogue.client.close` (meter): Marked every time an Apache client is successfully closed and any underlying resources released (e.g. connections and background threads).
  - `client-name`
  - `client-type` values (`apache-hc5`)
- `dialogue.client.connection.create` (timer): Reports the time spent creating a new connection. This includes both connecting the socket and the full TLS handshake.
  - `client-name`
  - `client-type` values (`apache-hc5`)
  - `result` values (`success`,`failure`): Describes whether or not a connection was successfully established.
- `dialogue.client.connection.connect` (timer): Reports the time spent within `socket.connect`. This does not include TLS.
  - `client-name`
  - `client-type` values (`apache-hc5`)
  - `result` values (`success`,`failure`): Describes whether or not a connection was successfully established.
- `dialogue.client.connection.closed.partially-consumed-response` (meter): Reports the rate that connections are closed due to response closure prior to response data being fully exhausted. When this occurs, subsequent requests must create new handshakes, incurring latency and CPU overhead due to handshakes.
  - `client-name`
  - `client-type` values (`apache-hc5`)
- `dialogue.client.connection.create.error` (meter): Rate that connections have failed to be created (e.g. connection timeout, no route to host).
  - `client-name`
  - `client-type` values (`apache-hc5`)
  - `cause`
- `dialogue.client.connection.resolution.error` (meter): Rate that connections have failed due to host resolution.
  - `client-name`
  - `client-type` values (`apache-hc5`)
- `dialogue.client.connection.insecure.cipher` (meter): Meter describing the use of insecure ciphers to connect to this server.
  - `client-name`
  - `client-type` values (`apache-hc5`)
  - `cipher`: The insecure cipher used to connect to this server

### dialogue.client.pool
Connection pool metrics from the dialogue Apache client.
- `dialogue.client.pool.size` tagged `client-name`, `state` (gauge): Number of connections in the client connection pool in states `idle`, `pending`, and `leased`.

## Dialogue Clients

`com.palantir.dialogue:dialogue-clients`

### client.dns
Dialogue DNS metrics.
- `client.dns.tasks` (counter): Number of active Dialogue DNS update background tasks currently scheduled.
  - `kind`: Describes the type of component polling for DNS updates.

## Dialogue Core

`com.palantir.dialogue:dialogue-core`

### client
General client metrics produced by dialogue. These metrics are meant to be applicable to all conjure clients without being implementation-specific.
- `client.response` tagged `channel-name`, `service-name`, `endpoint`, `status` (timer): Request time split by status and endpoint. Possible status values are:
* success: 2xx requests, always excludes time spent reading the response body.
* failure:
  - QoS failures (429, 308, 503)
  - 500 requests
  - IOExceptions

- `client.deprecations` tagged `service-name` (meter): Rate of deprecated endpoints being invoked.

### dialogue.balanced
Instrumentation for BalancedChannel internals.
- `dialogue.balanced.score` tagged `channel-name`, `hostIndex` (gauge): The score that the BalancedChannel currently assigns to each host (computed based on inflight requests and recent failures). Requests are routed to the channel with the lowest score. (Note if there are >10 nodes this metric will not be recorded).

### dialogue.client
Dialogue-specific metrics that are not necessarily applicable to other client implementations.
- `dialogue.client.response.leak` tagged `client-name`, `service-name`, `endpoint` (meter): Rate that responses are garbage collected without being closed. This should only occur in the case of a programming error.
- `dialogue.client.request.retry` tagged `channel-name`, `reason` (meter): Rate at which the RetryingChannel retries requests (across all endpoints).
- `dialogue.client.requests.queued` tagged `channel-name` (counter): Number of queued requests waiting to execute.
- `dialogue.client.requests.endpoint.queued` tagged `channel-name`, `service-name`, `endpoint` (counter): Number of queued requests waiting to execute for a specific endpoint due to server QoS.
- `dialogue.client.requests.sticky.queued` tagged `channel-name` (counter): Number of sticky queued requests waiting to try to be executed.
- `dialogue.client.request.queued.time` tagged `channel-name` (timer): Time spent waiting in the queue before execution.
- `dialogue.client.request.endpoint.queued.time` tagged `channel-name`, `service-name`, `endpoint` (timer): Time spent waiting in the queue before execution on a specific endpoint due to server QoS.
- `dialogue.client.request.sticky.queued.time` tagged `channel-name` (timer): Time spent waiting in the sticky queue before execution attempt.
- `dialogue.client.requests.size` (histogram): Histogram of the sizes of requests larger than a threshold (1 MiB).
  - `repeatable` values (`true`,`false`)
  - `channel-name`
  - `service-name`
  - `endpoint`
- `dialogue.client.create` tagged `client-name`, `client-type` (meter): Marked every time a new client is created.

### dialogue.concurrencylimiter
Instrumentation for the ConcurrencyLimitedChannel
- `dialogue.concurrencylimiter.max` tagged `channel-name`, `hostIndex` (gauge): The maximum number of concurrent requests which are currently permitted. Additively increases with successes and multiplicatively decreases with failures.
- `dialogue.concurrencylimiter.in-flight` tagged `channel-name`, `hostIndex` (gauge): The number of concurrent requests which are currently running.

### dialogue.nodeselection
Instrumentation for which node selection strategy is used
- `dialogue.nodeselection.strategy` tagged `channel-name`, `strategy` (meter): Marked every time the node selection strategy changes

### dialogue.pinuntilerror
Instrumentation for the PIN_UNTIL_ERROR node selection strategy.
- `dialogue.pinuntilerror.success` tagged `channel-name`, `hostIndex` (meter): Meter of the requests that were successfully made, tagged by the index of the inner channel. (Note if there are >10 nodes this metric will not be recorded).
- `dialogue.pinuntilerror.nextNode` tagged `channel-name`, `reason` (meter): Marked every time we switch to a new node, includes the reason why we switched (limited, responseCode, throwable).
- `dialogue.pinuntilerror.reshuffle` tagged `channel-name` (meter): Marked every time we reshuffle all the nodes.

### dialogue.roundrobin
Instrumentation for the ROUND_ROBIN node selection strategy (currently implemented by BalancedChannel).
- `dialogue.roundrobin.success` tagged `channel-name`, `hostIndex` (meter): Meter of the requests that were successfully made, tagged by the index of the host. (Note if there are >10 nodes this metric will not be recorded).

## Conjure Java Jackson Serialization

`com.palantir.conjure.java.runtime:conjure-java-jackson-serialization`

### json.parser
Metrics produced instrumented Jackson components.
- `json.parser.string.length` tagged `format` (histogram): Histogram describing the length of strings parsed from input.

## Tritium Metrics

`com.palantir.tritium:tritium-metrics`

### executor
Executor metrics.
- `executor.submitted` tagged `executor` (meter): A meter of the number of submitted tasks.
- `executor.running` tagged `executor` (counter): The number of running tasks.
- `executor.duration` tagged `executor` (timer): A timer of the time it took to run a task.
- `executor.queued-duration` tagged `executor` (timer): A timer of the time it took a task to start running after it was submitted.
- `executor.scheduled.overrun` tagged `executor` (counter): A gauge of the number of fixed-rate scheduled tasks that overran the scheduled rate. Applies only to scheduled executors.
- `executor.threads.created` (meter): Rate that new threads are created for this executor.
  - `executor`
  - `thread-type` values (`platform`,`virtual`)
- `executor.threads.running` (counter): Number of live threads created by this executor.
  - `executor`
  - `thread-type` values (`platform`,`virtual`)

### jvm.gc
Java virtual machine garbage collection metrics.
- `jvm.gc.count` tagged `collector` (gauge): The total number of collections that have occurred since the JVM started.
- `jvm.gc.time` tagged `collector` (gauge): The accumulated collection elapsed time in milliseconds.
- `jvm.gc.finalizer.queue.size` (gauge): Estimate of the number of objects pending finalization. Finalizers are executed in serial on a single thread shared across the entire JVM. When a finalizer is slow and blocks execution, or objects which override `Object.finalize` are allocated more quickly than they can be freed, the JVM will run out of memory. Cleaners are recommended over implementing finalize in most scenarios.

### jvm.memory.pools
Java virtual machine memory usage metrics by memory pool.
- `jvm.memory.pools.max` tagged `memoryPool` (gauge): Gauge of the maximum number of bytes that can be used by the corresponding pool.
- `jvm.memory.pools.used` tagged `memoryPool` (gauge): Gauge of the number of bytes used by the corresponding pool.
- `jvm.memory.pools.committed` tagged `memoryPool` (gauge): Gauge of the number of bytes that the jvm has committed to use by the corresponding pool.
- `jvm.memory.pools.init` tagged `memoryPool` (gauge): Gauge of the number of bytes that the jvm initially requested to the os by the corresponding pool.
- `jvm.memory.pools.usage` tagged `memoryPool` (gauge): Gauge of the ratio of the number of bytes used to the maximum number of bytes that can be used by the corresponding pool.
- `jvm.memory.pools.used-after-gc` tagged `memoryPool` (gauge): Gauge of the number of bytes used after the last garbage collection by the corresponding pool. Note that this metrics is not supported by all implementations.

### tls
Transport layer security metrics.
- `tls.handshake` tagged `context`, `cipher`, `protocol` (meter): Measures the rate of TLS handshake by SSLContext, cipher suite, and TLS protocol. A high rate of handshake suggests that clients are not properly reusing connections, which results in additional CPU overhead and round trips.