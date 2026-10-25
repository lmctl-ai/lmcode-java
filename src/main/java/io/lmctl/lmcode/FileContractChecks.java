package io.lmctl.lmcode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Direct file-catalog contract checks, callable by the launcher without JUnit. */
public final class FileContractChecks {
  private FileContractChecks() {}
  public static void main(String[] args) { int n=run(); System.out.println("PASS file contract checks ("+n+")"); }
  public static int run() {
    Registry r=new Registry(false); Stdlib.register(r); Context c=new Context();
    Registry.Binding read=r.resolve("fn file_read(string) -> string");
    Registry.Binding exists=r.resolve("fn file_exists(string) -> bool");
    Path dir;
    try { dir=Files.createTempDirectory("lmcode-file-contract-"); } catch(Exception e){throw new AssertionError(e);}
    try {
      Path file=dir.resolve("raw.bin"); Files.write(file,new byte[]{'a',0,(byte)0xff});
      Value got=read.callback.call(c,Value.none(),new Value[]{Value.ofString(file.toString())});
      byte[] bytes=got.asString().bytes(); require(bytes.length==3&&bytes[0]=='a'&&bytes[1]==0&&(bytes[2]&255)==255,"raw file bytes");
      require(exists.callback.call(c,Value.none(),new Value[]{Value.ofString(file.toString())}).asBool(),"regular file exists");
      require(!exists.callback.call(c,Value.none(),new Value[]{Value.ofString(dir.toString())}).asBool(),"directory is not a file");
      if(!StdFile.isWindows()) { byte[] rawPath=new byte[dir.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+6];byte[] prefix=dir.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);System.arraycopy(prefix,0,rawPath,0,prefix.length);rawPath[prefix.length]='/';rawPath[prefix.length+1]=(byte)0xff;rawPath[prefix.length+2]='.';rawPath[prefix.length+3]='b';rawPath[prefix.length+4]='i';rawPath[prefix.length+5]='n';Path raw=Path.of(java.net.URI.create(StdFile.fileUri(rawPath)));Files.write(raw,new byte[]{9,0,8});Value rawValue=read.callback.call(c,Value.none(),new Value[]{Value.ofString(new VmString(rawPath))});require(Arrays.equals(rawValue.asString().bytes(),new byte[]{9,0,8}),"invalid UTF-8 filename round trip");require(raw.toUri().getRawPath().contains("%FF"),"raw filename URI remains percent encoded");Files.deleteIfExists(raw); }
      boolean nul=false;try{read.callback.call(c,Value.none(),new Value[]{Value.ofString(new VmString(new byte[]{'a',0,(byte)0xff}))});}catch(VmException e){nul=e.code.equals("RUN_HOST")&&e.getMessage().startsWith("file_read: ")&&e.getMessage().indexOf(':',11)>0;}require(nul,"NUL path has stable function:path:reason error");
      return 6;
    } catch(Exception e) { throw new AssertionError(e); }
    finally { try(var paths=Files.walk(dir)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){} }
  }
  private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
}
