# Limits

Context defaults:10million VM instructions and4GiB conservative cumulative VM/stdlib allocation budget per run. This is not a JVM RSS cap; host-owned allocations and callback I/O require host policies. Strings repeat/replace output cap64MiB; glob work2^27; collection arena65536objects. Collections expire after every run including exceptional exit; registry prevents overlapping runs. Shared vectors plus regression tests verify these paths, byte ranges across0x7f/0x80, and malformed loop stack invariants.
