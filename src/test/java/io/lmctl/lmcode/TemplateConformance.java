package io.lmctl.lmcode;
import java.nio.file.*;
import java.util.*;
public final class TemplateConformance {
 public static void main(String[] args) throws Exception {
  Registry r=new Registry();Context c=new Context();r.enterRun(c);
  try {
   Value vectors=call(r,c,"fn json_parse(string) -> Json",Value.ofString(Files.readString(Path.of("std/template-cases.json"))));
   long size=method(r,c,"method Json.len() -> int",vectors).asInt();
   for(int i=0;i<size;i++){
    Value v=method(r,c,"method Json.at(int) -> Json",vectors,Value.ofInt(i));
    String name=text(r,c,get(r,c,v,"name"));
    boolean fails=method(r,c,"method Json.has(string) -> bool",v,Value.ofString("error")).asBool();
    Value context=get(r,c,v,"context");String before=textJson(r,c,context);
    try{
     Value got=call(r,c,"fn template_render(string, Json) -> string",Value.ofString(text(r,c,get(r,c,v,"template"))),context);
     if(fails || !got.asString().equals(Value.ofString(text(r,c,get(r,c,v,"result"))).asString()))throw new AssertionError(name+": output mismatch");
    }catch(VmException e){if(!fails || !e.getMessage().equals(text(r,c,get(r,c,v,"error"))))throw new AssertionError(name,e);}
    if(!before.equals(textJson(r,c,context)))throw new AssertionError("context mutated");
   }
   byte[] raw={(byte)255,0,'<','n','>'};
   Value context=call(r,c,"fn json_parse(string) -> Json",Value.ofString("{\"n\":9223372036854775807}"));
   byte[] got=call(r,c,"fn template_render(string, Json) -> string",Value.ofString(new VmString(raw)),context).asString().bytes();
   if(got[0]!=(byte)255 || got[1]!=0)throw new AssertionError("raw bytes changed");
   System.out.println("PASS "+size+" template vectors and raw bytes");
  }finally{r.exitRun(c);}
 }
 static Value call(Registry r,Context c,String sig,Value...a){return r.resolve(sig).callback.call(c,Value.none(),a);}
 static Value method(Registry r,Context c,String sig,Value self,Value...a){return r.resolve(sig).callback.call(c,self,a);}
 static Value get(Registry r,Context c,Value v,String k){return method(r,c,"method Json.get(string) -> Json",v,Value.ofString(k));}
 static String text(Registry r,Context c,Value v){return method(r,c,"method Json.as_string() -> string",v).asString().toString();}
 static String textJson(Registry r,Context c,Value v){return method(r,c,"method Json.to_json() -> string",v).asString().toString();}
}
