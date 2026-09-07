# Atomic writes + content-verified migration for sign/config persistence

Region-sharded sign files and the core config file were written directly to their final path, so a crash or
disk-full mid-write left a truncated file that's silently treated as "no data" on next load — and legacy-to-sharded
migration verified success by checking file *existence* only, so a truncated-but-present region file could still
pass verification and cause the legacy backup to finalize over genuinely lost data. We chose to make every write in
this path go through the existing temp-file + `ATOMIC_MOVE` pattern (`FileUtils.copyFile` already established this
elsewhere) and to make migration verification round-trip *parse* each written file rather than just check that it
exists. The cost is one extra parse pass per region file during migration; we accepted that cost because the
alternative — file-existence-only verification — is exactly the gap that let a real data-loss bug through crash
testing.
