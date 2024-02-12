package hungama;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class PermutationUtils {
  public static <T> void permute(T[] input) {
    int n = input.length;
    int[] x = permutation(n);
    List<T> list = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      list.add(i, input[x[i]]);
    }
    list.toArray(input);
  }

  public static <T> List<T> permute(Collection<T> input) {
    List<T> list = new ArrayList<>(input);
    int n = list.size();
    int[] x = permutation(n);
    List<T> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      out.add(i, list.get(x[i]));
    }
    return out;
  }

  public static int[] permutation(int N) {
    int[] x = new int[N];
    for (int i = 0; i < N; i++) {
      x[i] = i;
    }
    Random random = new Random();
    // CLRS p. 103
    for (int i = 0; i < N; i++) {
      int j = random.nextInt(i, N); // select a random element from remaining collection
      // now put that random element at i-th place
      int temp = x[i];
      x[i] = x[j];
      x[j] = temp;
    }
    return x;
  }

  public static int[] permutation(int N, long seed) {
    int[] x = new int[N];
    for (int i = 0; i < N; i++) {
      x[i] = i;
    }
    Random random = new Random(seed);
    // CLRS p. 103
    for (int i = 0; i < N; i++) {
      int j = random.nextInt(i, N); // select a random element from remaining collection
      // now put that random element at i-th place
      int temp = x[i];
      x[i] = x[j];
      x[j] = temp;
    }
    return x;
  }
}
