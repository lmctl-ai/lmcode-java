package io.lmctl.lmcode;
/** The first slice-2 control runs against the untouched Java registry. */
public final class ReturnRegistrationChecks {
 public static void main(String[] args) {
  long actual=new Registry().stdSignatures().stream().filter(s->s.startsWith("fn lmcode_return_")).count();
  if(actual!=24)throw new AssertionError("return standard overloads: expected 24, got "+actual);
  System.out.println("PASS 24 return standard overloads");
 }
}
