# v0.1.1 shared catalog catch-up

All three locks now pin lmcode-cpp53b9bdf851875ab904518e92347934b83874c706.
The canonical standard schema remains byte-identical with72 signatures (SHA256
cfe31e8b34fdcc33a13e456e80fa9d05a5e6c8fed5b98988abd24ac8570e65e0).
Core remains51 cases; standard grows from77 to118 with41 seeded corpus/property
cases for seeds7,42,20261024. Fixtures are copied unchanged via git archive.

The first expanded run passed115/118. Each seeded trace exposed two numeric edges:
Java Formatter used the shortest decimal for the smallest subnormal, and the zero
parser rejected +.0. Formatting now rounds the exact binary64 BigDecimal to15
significant digits with ties-even; decimal parsing detects nonzero significands
before rejecting underflow, preserving signed zero and subnormals.

Managed-runtime behavior follows the newer C# contract: byte strings, stable map
update/remove/reinsert order, signed-zero/NaN list comparisons and run-bound handles.
Java LinkedHashMap already met the order contract, so no custom map was introduced.
File failures preserve function, original path and nonempty OS reason. Unix paths
use public file: URI APIs to preserve raw filename bytes; Windows validates UTF8
and rejects DOS devices. Linux can round-trip invalid UTF8 filenames; macOS rejects
them, which is verified as RUN_HOST rather than replacing bytes or skipping tests.
Memory-budget exceptions retain RUN_ERROR.

The runner now supports reference-compatible LMCODE_TRACE=1 records and reports
only the first mismatching trace entry by default. runtime_error_file expectations
check the full expanded path prefix and nonempty reason, matching the C++ runner.

Local JVM and GraalVM CE21 native greeter pass51/51 core,118/118 standard and eight
regression groups. Main CI https://github.com/lmctl-ai/lmcode-java/actions/runs/40147670642
is green across all5 platforms, including Windows native vector execution.
