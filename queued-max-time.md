 PR #517 — "Implement a non-configurable ten minute timeout" (closed, not merged)

  This is the most directly relevant discussion. Key quotes:

  - @carterkozak: "Happy to punt on this for now. Long term I think a time-based queue limit will scale better than a size-based limit, though ideal may lie in the middle."
  - @markelliot: "This appears to limit request duration, not just time-to-schedule. I'd be in favor of never starting requests past a fixed deadline, but think more work needs to be put in to understanding
  durations of active requests if the limit is going to include full request duration."
  - @ellisjoe: Raised the concern about whether requests should be retried when the deadline is hit, since there's no server-side support to cancel in-flight work — you'd end up "pegging upstream services with
  duplicative work that's never expected to complete within the deadline."
  - @ferozco: Hesitant because there could be services where 10 minutes is too low.

  The PR was closed without merging.

  PR #628 — "maxQueueSize 1k → 100k" (merged)

  - @carterkozak on review: "lgtm, we haven't seen many requests get queued in our dashboards, so there shouldn't be much risk of memory pressure. Long term I'd like to bound the queue based on time."

  This confirms Carter has wanted time-based queue bounds since 2020.

  PR #2485 — "set deadlines on outbound requests" (merged, v5.1.0)

  This added DeadlineAdvertisementChannel, which encodes the readTimeout as an Expect-Within header. However, as we discussed, this only checks the deadline at the point of execution — it doesn't enforce
  anything while the request is queued.

  Issue #615 — "No good option for black holed server nodes"

  Related but more about node selection strategy than queue timeouts. The fix was the balanced node selection strategy.

  Summary of prior consensus

  There's agreement on the direction:
  1. Time-based queue bound is desirable (Carter, twice)
  2. Don't start requests past a deadline (Mark) — i.e., check at dequeue time, not just on the wire
  3. Be careful about retry interaction (Joe) — if a request times out in the queue, should it be retried? That could amplify load
  4. Configurable, not hardcoded (Ferozco) — a fixed 10-minute timeout was deemed too risky

  The deadlines infrastructure (PR #2485) partially addresses this now — if deadline enforcement is enabled, DeadlineAdvertisementChannel will fail a request with DeadlineExpiredException if the deadline has
  already expired before executing. But that check happens after the queue, at the per-host channel level, so the request still occupies a queue slot until it's dequeued.

