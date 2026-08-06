# 02 — Abort migration overwrite when backup fails

**What to build:** When `FileUtils.createBackup`/`copyFile` fails partway through a data-changing migration (config
V1→V2, sign-file V1/V2→V3) — disk full, permissions, whatever — the migration stops before overwriting the original
file. Today the failure is only logged as a warning and the caller proceeds to overwrite anyway, so a failed backup
can mean the user's only copy of their pre-migration data is gone with no recoverable backup and no visible signal
beyond a log line.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] A failed backup (simulate via an unwritable target) prevents the subsequent overwrite of the original file
- [ ] The migration surfaces the failure clearly (not just a log line) so the user/operator knows the migration didn't complete
- [ ] A successful backup still lets the migration proceed as before
