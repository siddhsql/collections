package hungama;

import java.util.Random;

public class StringUtils {
  public static String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  public static Random random = new Random(0);

  public static String generate_random_string(int length) {
    StringBuilder stringBuilder = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int randomIndex = random.nextInt(CHARSET.length());
      char randomChar = CHARSET.charAt(randomIndex);
      stringBuilder.append(randomChar);
    }

    return stringBuilder.toString();
  }

  public static boolean some_arg_is_set(String... args) {
    for (String s : args) {
      if (s != null && !s.isBlank()) {
        return true;
      }
    }
    return false;
  }
}
