package io.lmctl.lmcode;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Mutations compile against production classes; only semantic assertion failures count. */
public final class InvocationMutationChecks {
 private record Fault(String name,String file,String from,String to,String group,String assertion){}
 public static void main(String[] args)throws Exception{
  InvocationChecks.run("");
  List<Fault> faults=List.of(
   new Fault("missing seal","ResultSink.java","sealed=true;","sealed=false;","sealing","missing refusal: result sink is sealed"),
   new Fault("reused sink","InvocationResults.java","slot.sink=new ResultSink(contract);","slot.sink=slots[0].sink==null?new ResultSink(contract):slots[0].sink;","sink isolation","reused sink across parts"),
   new Fault("wrong part","InvocationResults.java","new Failure(builder?index:null,phase,copy)","new Failure(builder?0:null,phase,copy)","first failure","wrong part or lost first failure"),
   new Fault("missing flush","Reports.java","output.flush();",";","flush and single shot","flush was omitted"),
   new Fault("missing completion","InvocationResults.java","if(!slots[i].completed)","if(false)","missing completion","envelope exit"),
   new Fault("swap add order","InvocationResults.java","ResultJson.encode(slots[i].result)","ResultJson.encode(slots[slots.length-1-i].result)","sink isolation","add order independent of completion"),
   new Fault("partial result after error","InvocationResults.java","if(failure!=null){json=","if(false){json=","first failure","envelope exit"),
   new Fault("dropped field contract","ResultSink.java","contract.validate(next);root=next;","root=next;","contracts atomic","missing refusal: at /bad~1key~0: expected int; got null")
  );
  Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
  Path javac=java.resolveSibling(System.getProperty("os.name").startsWith("Windows")?"javac.exe":"javac");
  for(Fault fault:faults){Path scratch=Files.createTempDirectory("lmcode-java-invocation-mutant-");
   try{
    String source=Files.readString(Path.of("src/main/java/io/lmctl/lmcode",fault.file));if(!source.contains(fault.from))throw new AssertionError("missing mutation anchor "+fault.name);
    Path copy=scratch.resolve(fault.file);Files.writeString(copy,source.replace(fault.from,fault.to));Path compile=scratch.resolve("compile.log");
    Process compiler=new ProcessBuilder(javac.toString(),"--release","21","-cp","target/classes","-d",scratch.toString(),copy.toString()).redirectErrorStream(true).redirectOutput(compile.toFile()).start();
    if(compiler.waitFor()!=0)throw new AssertionError("invalid mutant "+fault.name+": "+Files.readString(compile));
    Path log=scratch.resolve("control.log");Process control=new ProcessBuilder(java.toString(),"-cp",scratch+System.getProperty("path.separator")+"target/classes","io.lmctl.lmcode.InvocationChecks",fault.group).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    int code=control.waitFor();String output=Files.readString(log,StandardCharsets.UTF_8);
    if(code!=1||!output.contains("AssertionError")||!output.contains(fault.assertion))throw new AssertionError("SURVIVED or wrong oracle "+fault.name+": "+output);
    System.out.println("CAUGHT invocation "+fault.name+" at "+fault.assertion);
   }finally{try(var walk=Files.walk(scratch)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  }
 }
}
