# Shared safe-call utility for crash-safety at game-thread entry points

Several game-thread entry points (mod lifecycle hooks, both mixins, `SignManager`'s static entry points) call into
mod logic with no exception handling, violating the "mod must never crash the server" invariant. We considered
wrapping each entry point individually with a bespoke try/catch tailored to its own concerns, but chose one shared
safe-call utility (catch `Throwable`, log with context, swallow) used at every entry point instead — one pattern to
get right and audit, rather than ~6-8 independent try/catch blocks that can drift or be missed on a future addition.
