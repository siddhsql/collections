package hungama.collections;

import java.io.*;
import java.nio.file.*;
import java.util.*;

record Header(long version, int capacity, int size) {
    public Header(long version, int capacity, int size) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity cannot be less than or equal to zero.");            
        }
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be less than zero.");
        }
        this.version = version;
        this.capacity = capacity;
        this.size = size;
    }
}

/**
 * Provides a fixed-size list backed by disk storage.
 * Insertions and lookups are O(1) as is the main memory consumption.
 */
public class ImmutableList implements Closeable, Iterable<byte[]> {

    private static final int VERSION = 1;
    private static final int HEADER_LENGTH = Long.BYTES + Integer.BYTES * 2;
    private static final int OFFSET_LENGTH = Long.BYTES;

    private RandomAccessFile raf;
    private int size;
    private final int capacity;
    private final boolean readOnly;    

    /**
     * Creates a new immutable list with given {@code capacity}
     * @param path name of file that will be used to store this list
     * @param capacity the capacity of the list (number of elements it can contain)
     * @throws IOException
     */
    public ImmutableList(Path path, int capacity) throws IOException {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity");
        }
        File f = path.toFile();
        if (f.exists()) {
            throw new FileAlreadyExistsException(path.toString());
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.raf = new RandomAccessFile(f, "rw");
        this.capacity = capacity;
        this.size = 0;
        this.readOnly = false;
        this.writeHeader(capacity);
        byte[] b = new byte[capacity * OFFSET_LENGTH];
        this.raf.write(b);
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
        this.capacity = header.capacity();        
        this.readOnly = true;
    }

    private static void checkVersion(long version) {
        if (version != VERSION) {
            throw new DBException("wrong version. expected: " + VERSION + " found: " + version);
        }
    }

    private synchronized Header readHeader() throws IOException {
        this.raf.seek(0);
        long version = this.raf.readLong();
        int capacity = this.raf.readInt();
        int size = this.raf.readInt();
        if (this.size > this.capacity) {
            throw new RuntimeException("size cannot exceed capacity. possible data corruption");
        }
        return new Header(version, capacity, size);
    }

    private synchronized void writeHeader(int capacity) throws IOException {
        this.raf.seek(0);
        this.raf.writeLong(VERSION);
        this.raf.writeInt(capacity);
        this.raf.writeInt(0);
    }

    private synchronized void writeSize(int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("size");
        }
        if (size > capacity) {
            throw new IllegalArgumentException("size");
        }
        this.raf.seek(Long.BYTES + Integer.BYTES); // skip version and capacity fields in the header
        this.raf.writeInt(size);
    }

    /**
     * Opens a pre-existing list from given file location
     * @param path file containing the list
     * @return
     * @throws IOException
     */
    public static ImmutableList open(Path path) throws IOException {
        return new ImmutableList(path); 
    }

    /**
     * Number of elements in the list. It can be less than the capacity if the list was not "filled to the top" when it was created.
     * @return
     */
    public int size() {
        return this.size;
    }

    /**
     * The maximum number of elements this list can contain.
     * @return
     */
    public int capacity() {
        return this.capacity;
    }

    /**
     * Returns the size of underlying file in bytes.
     * @return
     */
    public long fileSize() throws IOException {
        return this.raf.length();
    }

    /**
     * Adds element to the list.
     * @param data the element to add to the list. must not be empty.
     * @throws IOException
     */
    public synchronized void add(byte[] data) throws IOException {
        checkCanWrite();
        if (this.size >= this.capacity) {
            throw new RuntimeException("list is full");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("data cannot be empty");
        }
        long currOffset = raf.length();
        assert currOffset > 0;
        raf.seek(offsetPos(size));
        raf.writeLong(currOffset);        
        raf.seek(currOffset);
        raf.writeInt(data.length);
        raf.write(data);
        this.size++;
        this.writeSize(size);
    }    

    /**
     * Gets element at given position in the list
     * @param index the index of the element you want to fetch. should be between 0 and {@code size} of the list.
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
     * Releases all resources consumed by this class.
     * Once an instance of the class is closed, it should not be used. 
     * A new instance must be created.
     */
    @Override
    public synchronized void close() throws IOException {
        if (raf != null) {
            if (!this.readOnly) {
                this.writeSize(this.size);
            }
            raf.close();
            raf = null;
        }
    }

    private void checkCanWrite() {
        if (this.readOnly) {
            throw new RuntimeException("collection is read-only");
        }
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

    @Override
    public Iterator<byte[]> iterator() {
        return new MyIterator();       
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