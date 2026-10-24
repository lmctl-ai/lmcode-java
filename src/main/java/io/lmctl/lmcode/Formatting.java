package io.lmctl.lmcode;

import java.util.Locale;

/** Stable, locale-independent rendering used by the language's str(float). */
public final class Formatting {
  private Formatting() {}

  public static String formatG15(double x) {
    if (Double.isNaN(x)) return "nan";
    if (Double.isInfinite(x)) return x < 0 ? "-inf" : "inf";
    if (x == 0.0) return Double.doubleToRawLongBits(x) < 0 ? "-0" : "0";
    String sci = String.format(Locale.ROOT, "%.14e", x);
    int e = sci.indexOf('e');
    String mant = trim(sci.substring(0, e));
    int exp = Integer.parseInt(sci.substring(e + 1));
    if (exp < -4 || exp >= 15)
      return mant + "e" + (exp < 0 ? "-" : "+") + String.format(Locale.ROOT, "%02d", Math.abs(exp));
    int decimals = Math.max(0, 14 - exp);
    String fixed = String.format(Locale.ROOT, "% ." + decimals + "f", x).trim();
    return trim(fixed);
  }

  private static String trim(String s) {
    if (!s.contains(".")) return s;
    int n = s.length();
    while (n > 0 && s.charAt(n - 1) == '0') n--;
    if (n > 0 && s.charAt(n - 1) == '.') n--;
    return s.substring(0, n);
  }
}
