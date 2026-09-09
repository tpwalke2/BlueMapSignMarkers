# ReDoS mitigation: sign-line length cap, no match timeout

`REGEX` marker-group matching runs admin-configured regexes against player-controlled sign text synchronously on the
server thread, with no protection against catastrophic backtracking. We considered adding a time-bounded match (a
timeout via a separate thread/executor) so even a malicious regex+input pair couldn't hang the tick thread
indefinitely, but chose to enforce Minecraft's existing sign-line length limit server-side instead and reject/
truncate oversized text before it reaches regex matching. Catastrophic backtracking needs a sufficiently long
adversarial input to blow up; capping input length closes the practical attack surface without adding threading
machinery for a risk that's largely bounded by admin-configured (trusted) regex patterns rather than fully
adversarial ones. If a future finding shows short-input regexes can still blow up meaningfully, revisit the timeout
approach.
