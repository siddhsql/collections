package hungama;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.*;

import hungama.collections.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AppTest 
{
    private static Path path;
    private static List<byte[]> data;
    private static int N = 10;

    @BeforeAll
    public static void setup() {
        String tempDir = System.getProperty("java.io.tmpdir");
        String tempFilename = StringUtils.generate_random_string(8, new Random(System.currentTimeMillis()));
        path = Path.of(tempDir, tempFilename + ".bin");
        data = generateData(N);
    }

    @AfterAll
    public static void teardown() throws IOException {
        if (path.toFile().exists()) {
            Files.delete(path);
        }
    }    

    private static List<byte[]> generateData(int n) {
        List<byte[]> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(StringUtils.generate_random_string(100).getBytes());
        }
        return list;
    }

    @Test
    @Order(1)
    public void insertRecords() throws IOException {
        int capacity = data.size();
        try (ImmutableList immutableList = new ImmutableList(path, capacity)) {
            for (int i = 0; i < capacity; i++) {
                assertTrue(immutableList.size() == i);
                immutableList.add(data.get(i));
                assertTrue(immutableList.size() == i + 1);
            }
        }
    }

    @Test
    @Order(2)
    public void queryRecords() throws IOException {
        int n = data.size();
        int[] indices = PermutationUtils.permutation(n);
        try (ImmutableList list = ImmutableList.open(path)) {
            for (int i = 0; i < n; i++) {
                int index = indices[i];
                byte[] observed = list.get(index);
                byte[] expected = data.get(index);
                assertArrayEquals(expected, observed);
            }
        }
    }

    @Test
    @Order(3)
    public void enumerateRecords() throws IOException {
        try (ImmutableList list = ImmutableList.open(path)) {
            assertEquals(list.size(), data.size());
            int i = 0;
            for (byte[] observed : list) {
                byte[] expected = data.get(i++);
                assertArrayEquals(expected, observed);
            }
        }
    }
}
