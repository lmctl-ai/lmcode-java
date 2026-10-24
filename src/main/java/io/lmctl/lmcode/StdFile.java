package io.lmctl.lmcode;
import java.nio.file.*;import java.io.*;import java.util.*;
final class StdFile {
 static void register(Registry r){
  r.function("file_read",List.of("string"),"string",(c,s,a)->Value.ofString(read(c,"file_read",a[0].asString())));
  r.function("file_write",List.of("string","string"),"void",(c,s,a)->{write("file_write",a[0].asString(),a[1].asString().bytes(),false);return Value.none();});
  r.function("file_append",List.of("string","string"),"void",(c,s,a)->{write("file_append",a[0].asString(),a[1].asString().bytes(),true);return Value.none();});
  r.function("file_exists",List.of("string"),"bool",(c,s,a)->Value.ofBool(exists("file_exists",a[0].asString())));
  r.function("file_lines",List.of("string"),"StrList",(c,s,a)->{byte[]b=read(c,"file_lines",a[0].asString()).bytes();ArrayList<VmString>x=new ArrayList<>();int at=0;for(int i=0;i<b.length;i++)if(b[i]=='\n'){x.add(new VmString(Arrays.copyOfRange(b,at,i)));at=i+1;}x.add(new VmString(Arrays.copyOfRange(b,at,b.length)));return StdCollections.obj(c,"StrList",x);});
 }
 static Path path(String f,VmString v){byte[]b=v.bytes();for(byte x:b)if(x==0)throw VmException.host(f+": invalid argument");return Path.of(new String(b,java.nio.charset.StandardCharsets.UTF_8));}
 static VmString read(Context c,String f,VmString v){try{Path p=path(f,v);if(!Files.isRegularFile(p))throw new IOException("not a regular file");long size=Files.size(p);if(size>Integer.MAX_VALUE)throw new IOException("file is too large");c.charge(size);return new VmString(Files.readAllBytes(p));}catch(Exception e){throw VmException.host(f+": "+new String(v.bytes(),java.nio.charset.StandardCharsets.UTF_8)+": "+e.getMessage());}}
 static boolean exists(String f,VmString v){try{return Files.isRegularFile(path(f,v));}catch(Exception e){throw VmException.host(f+": invalid argument");}}
 static void write(String f,VmString v,byte[]d,boolean app){try{Path p=path(f,v);if(Files.exists(p)&&!Files.isRegularFile(p))throw new IOException("not a regular file");if(app)Files.write(p,d,StandardOpenOption.CREATE,StandardOpenOption.APPEND);else Files.write(p,d,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try(OutputStream o=Files.newOutputStream(p,StandardOpenOption.APPEND)){o.flush();}}catch(Exception e){throw VmException.host(f+": "+new String(v.bytes(),java.nio.charset.StandardCharsets.UTF_8)+": "+e.getMessage());}}
}
