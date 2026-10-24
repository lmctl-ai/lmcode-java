# API

Package io.lmctl.lmcode: Program.load, Registry.function/method/context, Context, Value, VmString and VmException. Schema hash binds exact bytes; callbacks bind canonical typed signatures. Registries default to72 standard signatures; Registry(false) opts out. SchemaSignatures normalizes declarations; PinVerifier checks exact canonical std/registry parity.

VmString is immutable bytes, including NUL and invalid UTF8. Only explicit text conversion uses Java Strings. Arithmetic wraps signed64-bit integers, float output follows15-significant-digit reference formatting. Ordered maps preserve insertion order. Stdlib.collectionValues/mapEntries expose read-only host snapshots. Error codes and source locations mirror the reference.
