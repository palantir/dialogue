1) Implementation ideas and tradeoffs

  The problem: Requests waiting in QueuedChannel's ConcurrentLinkedDeque have no timeout. The read timeout only applies on the wire (socket-level per-read
  timeout). Deadlines are only set at DeadlineAdvertisementChannel (after the queue), so for requests without an upstream deadline, time in the queue is
  invisible. Blocking callers are stuck in CallingThreadExecutor.getWork() → queue.take() with no way to time out.

  Proposed change: Add enqueueTimeNanos to DeferredCall. In scheduleNextTask(), after the existing isDone() check, compare elapsed time against a queue
  timeout. If expired, fail the SettableFuture with a queue timeout exception, clean up span/timer, and continue draining.

  Lazy vs proactive eviction:
  - Lazy (check at dequeue): Follows the same pattern as existing cancellation. No new threads or synchronization. Timed-out entries sit in the deque until
  schedule() runs, which happens on every submission and completion — frequent in practice. Only fails in a total stall (nothing completing, nothing arriving),
   which is already broken.
  - Proactive (scheduled eviction): Either per-entry ScheduledFuture (memory/scheduler overhead per enqueued request) or periodic sweep (fights with
  scheduleNextTask() over the deque, complex concurrency). Better promptness for blocking callers but significantly more complexity.
  - Recommendation: Start with lazy. If blocking caller latency becomes an issue, add per-entry scheduled response.setException() as an incremental improvement
   — the existing isDone() check handles deque cleanup.

  Retry interaction: DeadlineExpiredException (and presumably a new queue timeout exception) is a RuntimeException, not IOException.
  RetryingChannel.shouldAttemptToRetry() gates on instanceof IOException, so queue timeouts will not be retried.

  2) Configuration point and justification

  Per-client, not per-call. Queue behavior is a client-level concern; individual call sites shouldn't need to think about it.

  Derived from readTimeout initially. Add Duration queueTimeout() to dialogue's internal Config with a @Value.Default returning clientConf().readTimeout().
  This stays within the dialogue repo — no cross-repo change to ClientConfiguration in conjure-java-runtime. Later, if a dedicated queueTimeout field is added
  to ClientConfiguration, Config can switch to reading it.

  Prior art: Carter Kozak commented on PR #628 (2020): "Long term I'd like to bound the queue based on time." PR #517 (non-configurable 10-minute timeout) was
  closed because a hardcoded value was too risky — configurable per-client avoids that concern.

