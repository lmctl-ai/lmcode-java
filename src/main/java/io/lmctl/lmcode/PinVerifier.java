package io.lmctl.lmcode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public final class PinVerifier {
 private PinVerifier(){}
 static String digest(Path root)throws IOException,NoSuchAlgorithmException {MessageDigest h=MessageDigest.getInstance("SHA-256");try(var files=Files.walk(root)){for(Path p:files.filter(Files::isRegularFile).sorted(Comparator.comparing(x->root.relativize(x).toString().replace('\\','/'))).toList()){h.update(root.relativize(p).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8));h.update((byte)0);h.update(Files.readAllBytes(p));h.update((byte)0);}}return HexFormat.of().formatHex(h.digest());}
 public static void main(String[] args)throws Exception {for(String suite:List.of("vectors","stdlib-vectors")){Properties lock=new Properties();try(var in=Files.newInputStream(Path.of(suite+".lock"))){lock.load(in);}if(!digest(Path.of("conformance",suite)).equals(lock.getProperty("sha256")))throw new AssertionError("modified pinned "+suite);System.out.println("pinned "+suite+" "+lock.getProperty("commit"));}Properties std=new Properties();try(var in=Files.newInputStream(Path.of("std.lock"))){std.load(in);}String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("std/lmcode-std.schema.json"))));if(!hash.equals(std.getProperty("sha256")))throw new AssertionError("modified canonical std schema");if(!new Registry().stdSignatures().equals(SchemaSignatures.from(Files.readAllBytes(Path.of("std/lmcode-std.schema.json")))))throw new AssertionError("canonical std/registry signatures differ");}
}
