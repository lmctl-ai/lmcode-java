// ASCII source, deliberately non-ASCII native argv. Do not replace with an ASCII fixture.
class LauncherUtf8Probe {
    public static void main(String[] args) {
        String expected = "\u6c34\u00e9\ud83d\ude00";
        String actual = args.length == 1 ? args[0] : "";
        if (!expected.equals(actual)) {
            System.err.println("UTF-8 argv mismatch: expected 6c34,e9,1f600; got "
                    + actual.codePoints().mapToObj(Integer::toHexString).toList());
            System.exit(1);
        }
        System.out.println("PASS JVM Unicode argv");
    }
}
