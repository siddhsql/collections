package hungama;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hungama.collections.*;

public class AppTest 
{
    private static Path path;

    // see https://stackoverflow.com/questions/29671796/will-an-assertion-error-be-caught-by-in-a-catch-block-for-java-exception
    // for why we must use Before/After hooks and not try-catch block.
    // you cannot catch errors. you can only catch exceptions.

    @BeforeAll
    public static void setup() {
        String tempDir = System.getProperty("java.io.tmpdir");
        String tempFilename = StringUtils.generate_random_string(8);
        path = Path.of(tempDir, tempFilename + ".bin");
    }

    @AfterAll
    public static void teardown() throws IOException {
        if (path.toFile().exists()) {
            Files.delete(path);
        }
    }

    @Test
    public void test1() throws IOException {
        int n = 10;        
        var data = generateData(n);
        insertRecords(path, data);
        queryRecords(path, data);        
    }

    private List<byte[]> generateData(int n) {
        List<byte[]> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(StringUtils.generate_random_string(100).getBytes());
        }
        return list;
    }

    private void insertRecords(Path path, List<byte[]> list) throws IOException {
        int capacity = list.size();
        try (ImmutableList immutableList = new ImmutableList(path, capacity)) {
            for (int i = 0; i < capacity; i++) {
                byte[] data = list.get(i);
                assertTrue(immutableList.size() == i);
                immutableList.add(data);
                assertTrue(immutableList.size() == i + 1);
            }
        }
    }

    private void queryRecords(Path path, List<byte[]> data) throws IOException {
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
}
