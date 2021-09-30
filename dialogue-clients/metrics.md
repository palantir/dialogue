# Metrics

## Dialogue Apache Hc5 Client

`com.palantir.dialogue:dialogue-apache-hc5-client`

### dialogue.client
Dialogue client response metrics provided by the Apache client channel.
- `dialogue.client.response.leak` tagged `client-name`, `service-name`, `endpoint` (meter): Rate that responses are garbage collected without being closed. This should only occur in the case of a programming error.
- `dialogue.client.server.timing.overhead` tagged `client-name` (timer): Difference in request time reported by the client and by the server. This metric is only reported when the remote server provides a `Server-Timing` response header with a timing value for `server`.
- `dialogue.client.create` tagged `client-name`, `client-type` (meter): Marked every time a new client is created.
- `dialogue.client.close` tagged `client-name`, `client-type` (meter): Marked every time an Apache client is successfully closed and any underlying resources released (e.g. connections and background threads).
- `dialogue.client.connection.create` tagged `client-name`, `client-type` (timer): Reports the time spent creating a new connection. This includes both connecting the socket and the full TLS handshake.

### dialogue.client.pool
Connection pool metrics from the dialogue Apache client.
- `dialogue.client.pool.size` tagged `client-name`, `state` (gauge): Number of connections in the client connection pool in states `idle`, `pending`, and `leased`.

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

## Tritium Metrics

`com.palantir.tritium:tritium-metrics`

### executor
Executor metrics.
- `executor.submitted` tagged `executor` (meter): A meter of the number of submitted tasks.
- `executor.running` tagged `executor` (counter): The number of running tasks.
- `executor.duration` tagged `executor` (timer): A timer of the time it took to run a task.
- `executor.queued-duration` tagged `executor` (timer): A timer of the time it took a task to start running after it was submitted.
- `executor.scheduled.overrun` tagged `executor` (counter): A gauge of the number of fixed-rate scheduled tasks that overran the scheduled rate. Applies only to scheduled executors.
- `executor.threads.created` tagged `executor` (meter): Rate that new threads are created for this executor.
- `executor.threads.running` tagged `executor` (counter): Number of live threads created by this executor.

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