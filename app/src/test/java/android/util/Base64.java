package android.util;

/**
 * JVM unit-test mock for android.util.Base64.
 * This class exists ONLY in src/test to allow local JVM unit tests to execute
 * without requiring the Android OS runtime or Robolectric.
 * It is NEVER packaged into the application APK / DEX.
 */
public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    public static byte[] decode(String str, int flags) {
        if (str == null) return null;
        try {
            return java.util.Base64.getDecoder().decode(str.trim());
        } catch (IllegalArgumentException e) {
            String padded = str.trim();
            while (padded.length() % 4 != 0) {
                padded += "=";
            }
            return java.util.Base64.getUrlDecoder().decode(padded);
        }
    }

    public static String encodeToString(byte[] input, int flags) {
        if (input == null) return null;
        return java.util.Base64.getEncoder().encodeToString(input);
    }
}
