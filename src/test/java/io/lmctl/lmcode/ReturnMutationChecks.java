package io.lmctl.lmcode;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Isolated production-source copies; real javac and the shipped semantic assertions. */
public final class ReturnMutationChecks {
 private record Fault(String name,String file,String from,String to,String group,String assertion){}
 public static void main(String[] args)throws Exception{
  List<Fault> faults=List.of(
   new Fault("implicit library output","StdReturn.java","registry.resultSink().set(a[0]);","System.out.println(\"unexpected native return output\");registry.resultSink().set(a[0]);","library silence","native library printed stdout"),
   new Fault("shared global sink","Registry.java","private ResultSink resultSink;","private static ResultSink resultSink;","concurrent","sink isolation"),
   new Fault("owned list copy","ResultJson.java","    return out;\n  }\n  private static Object copyElement", "    return (List<Object>) raw;\n  }\n  private static Object copyElement","owned list","arena-alias/source-mutation"),
   new Fault("entry reset","Registry.java","active=true;try{if(defaultCollections)","active=true;try{if(resultSink!=null)resultSink.reset();if(defaultCollections)","owned list","reset-between-calls"),
   new Fault("retained raw number","ResultJson.java","if(decimalUnsafeInteger(raw,path))","if(false)","raw numeric","expected refusal: lmcode return: invalid JSON value at : unsafe_integer"),
   new Fault("legacy float formatter","ResultJson.java","Double.toString(v)","Formatting.formatG15(v)","shortest float","shortest float frame, not str(float)"),
   new Fault("silent default sink","Registry.java","if(resultSink==null)throw VmException.host(\"lmcode return: no result sink\");","if(resultSink==null)resultSink=new ResultSink();","registration","expected refusal: lmcode return: no result sink")
  );
  Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
  Path javac=java.resolveSibling(System.getProperty("os.name").startsWith("Windows")?"javac.exe":"javac");
  for(Fault fault:faults){
   Path scratch=Files.createTempDirectory("lmcode-java-return-mutant-");
   try{
    String source=Files.readString(Path.of("src/main/java/io/lmctl/lmcode",fault.file));
    if(!source.contains(fault.from))throw new AssertionError("missing mutation anchor "+fault.name);
    Path copy=scratch.resolve(fault.file);Files.writeString(copy,source.replace(fault.from,fault.to));
    Path compile=scratch.resolve("compile.log");
    Process compiler=new ProcessBuilder(javac.toString(),"--release","21","-cp","target/classes","-d",scratch.toString(),copy.toString()).redirectErrorStream(true).redirectOutput(compile.toFile()).start();
    if(compiler.waitFor()!=0)throw new AssertionError("invalid mutant "+fault.name+": "+Files.readString(compile));
    Path log=scratch.resolve("control.log");
    Process control=new ProcessBuilder(java.toString(),"-cp",scratch+System.getProperty("path.separator")+"target/classes","io.lmctl.lmcode.ResultSinkChecks",fault.group).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    int code=control.waitFor();String output=Files.readString(log,StandardCharsets.UTF_8);
    if(code==0||!output.contains("AssertionError")||!output.contains(fault.assertion))throw new AssertionError("SURVIVED or wrong oracle "+fault.name+": "+output);
    System.out.println("CAUGHT "+fault.name+" at "+fault.assertion);
   }finally{
    try(var walk=Files.walk(scratch)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
   }
  }
 }
}
