package hungama.collections;

import java.io.*;
import java.nio.file.*;
import java.util.*;

record Header(long version, int size) {
  public Header(long version, int size) {
    if (size < 0) {
      throw new IllegalArgumentException("size cannot be less than zero.");
    }
    this.version = version;
    this.size = size;
  }
}

/**
 * Provides a fixed-size list backed by disk storage. Insertions and lookups are O(1) as is the main
 * memory consumption.
 */
public class ImmutableList implements Closeable, Iterable<byte[]> {

  private static final int VERSION = 1;
  
  // one long to store the version and one int to store the size
  private static final int HEADER_LENGTH = Long.BYTES + Integer.BYTES;
  
  private static final int OFFSET_LENGTH = Long.BYTES;

  private RandomAccessFile raf;
  private final int size;

  /**
   * Creates a new immutable list with given {@code capacity}
   *
   * @param path name of file that will be used to store this list
   * @param data the elements to be added to the list. Since the list is immutable, the elements
   *     have to be given at time of construction and is frozen (unchangeable) after that.
   * @throws IOException
   */
  public ImmutableList(Path path, Collection<byte[]> data) throws IOException {
    this.size = data.size();
    File f = path.toFile();
    if (f.exists()) {
      throw new FileAlreadyExistsException(path.toString());
    }
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    this.raf = new RandomAccessFile(f, "rw");
    try {
      this.writeHeader(size);
      if (size == 0) {
        return;
      }
      byte[] b = new byte[size * OFFSET_LENGTH];
      this.raf.write(b);
      long[] offsets = new long[size];
      int i = 0;
      for (var e : data) {
        checkNotNull(e);
        checkArgument(e.length > 0);
        offsets[i++] = raf.length();
        raf.writeInt(e.length);
        raf.write(e);
      }
      assert i == size;
      raf.seek(offsetPos(0));
      for (i = 0; i < size; i++) {
        raf.writeLong(offsets[i]);
      }
    } catch (Exception e) {
      try {
        Files.delete(path);
      } catch (IOException inner) {
        e.addSuppressed(inner);
      }
      throw e;
    }
  }

  /**
   * Opens a pre-existing list from given file location
   *
   * @param path file containing the list
   * @return
   * @throws IOException
   */
  public static ImmutableList open(Path path) throws IOException {
    return new ImmutableList(path);
  }

  /**
   * Number of elements in the list.
   *
   * @return
   */
  public int size() {
    return this.size;
  }

  /**
   * Returns the size of underlying file in bytes.
   *
   * @return
   */
  public long fileSize() throws IOException {
    return this.raf.length();
  }

  /**
   * Gets element at given position in the list
   *
   * @param index the index of the element you want to fetch. should be between 0 and {@code size}
   *     of the list.
   * @return
   * @throws IOException
   */
  public synchronized byte[] get(int index) throws IOException {
    if (index < 0) {
      throw new IllegalArgumentException("index cannot be less than zero");
    }
    if (index >= this.size) {
      throw new IllegalArgumentException("index cannot be greater than size");
    }
    long offset = getDataOffset(index);
    raf.seek(offset);
    int length = raf.readInt();
    if (length <= 0) {
      throw new DBException("bad length.");
    }
    if (length > raf.length() - offset) {
      throw new DBException("bad length.");
    }
    byte[] buf = new byte[length];
    raf.readFully(buf);
    return buf;
  }

  /**
   * Load entire collection into memory.
   *
   * @return
   * @throws IOException
   */
  public synchronized List<byte[]> getAll() throws IOException {
    if (this.size == 0) {
      return new ArrayList<>();
    }
    long offset = getDataOffset(0);
    assert offset > 0;
    assert offset < raf.length();
    raf.seek(offset);
    List<byte[]> list = new ArrayList<>(this.size);
    for (int i = 0; i < this.size; i++) {
      int length = raf.readInt();
      if (length <= 0) {
        throw new DBException("bad length.");
      }
      if (length > raf.length() - offset) {
        throw new DBException("bad length.");
      }
      byte[] buf = new byte[length];
      raf.readFully(buf);
      list.add(buf);
    }
    return list;
  }

  /**
   * Releases all resources consumed by this class. Once an instance of the class is closed, it
   * should not be used. A new instance must be created.
   */
  @Override
  public synchronized void close() throws IOException {
    if (raf != null) {
      raf.close();
      raf = null;
    }
  }

  @Override
  public Iterator<byte[]> iterator() {
    return new MyIterator();
  }

  private long offsetPos(int index) {
    return HEADER_LENGTH + index * OFFSET_LENGTH;
  }

  private synchronized long getDataOffset(int index) throws IOException {
    if (index < 0 || index >= size) {
      throw new IllegalArgumentException("index");
    }
    raf.seek(offsetPos(index));
    long offset = raf.readLong();
    if (offset <= 0 || offset > raf.length()) {
      throw new DBException("bad offset.");
    }
    return offset;
  }

  private ImmutableList(Path path) throws IOException {
    File f = path.toFile();
    if (!f.exists()) {
      throw new FileNotFoundException(path.toString());
    }
    if (f.isDirectory()) {
      throw new IllegalArgumentException(path.toString() + " is a directory. expected file.");
    }
    if (!f.isFile()) {
      throw new IllegalArgumentException(path.toString() + " is not a file.");
    }
    assert f.isFile();
    RandomAccessFile raf = new RandomAccessFile(f, "r");
    this.raf = raf;
    Header header = readHeader();
    checkVersion(header.version());
    this.size = header.size();
  }

  private static void checkVersion(long version) {
    if (version != VERSION) {
      throw new DBException("wrong version. expected: " + VERSION + " found: " + version);
    }
  }

  private synchronized Header readHeader() throws IOException {
    this.raf.seek(0);
    long version = this.raf.readLong();
    int size = this.raf.readInt();
    return new Header(version, size);
  }

  private synchronized void writeHeader(int size) throws IOException {
    this.raf.seek(0);
    this.raf.writeLong(VERSION);
    this.raf.writeInt(size);    
  }

  private static void checkNotNull(Object arg) {
    if (arg == null) {
        throw new NullPointerException();
    }
  }

  private static void checkArgument(boolean arg) {
    if (!arg) {
        throw new RuntimeException();
    }
  }

  private class MyIterator implements Iterator<byte[]> {

    private int index;

    public MyIterator() {
      if (hasNext()) {
        try {
          raf.seek(getDataOffset(0));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    public boolean hasNext() {
      return index < ImmutableList.this.size;
    }

    @Override
    public byte[] next() {
      try {
        int length = raf.readInt();
        if (length <= 0) {
          throw new DBException("bad length.");
        }
        if (length > raf.length() - raf.getFilePointer()) {
          throw new DBException("bad length.");
        }
        byte[] buf = new byte[length];
        ImmutableList.this.raf.readFully(buf);
        index++;
        return buf;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
