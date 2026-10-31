# Native greeter example host

The complete Java host is [Greeter.java](../../src/main/java/io/lmctl/lmcode/examples/Greeter.java).
It binds typed callbacks to the embedded schema, executes bytecode and emits the standard
JSON result/error protocol. The root Maven build includes this example entry point.

From the repository root:

```sh
mvn -q verify
scripts/build-native.sh
./target/greeter --lmcode examples/greeter/hello.dslb
```

Windows uses target/greeter.exe and scripts/build-native.ps1. To regenerate hello.dslb,
put the built greeter on PATH as lmcode-java and run lmscript examples/greeter/hello.lms.
