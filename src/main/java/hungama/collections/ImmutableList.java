package hungama.collections;

import java.io.*;
import java.nio.file.*;

record OffsetLengthPair(long offset, int length) {
    public OffsetLengthPair(long offset, int length) {
        if (offset <= 0) {
            throw new IllegalArgumentException("offset cannot be less than or equal to zero");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length cannot le less than or equal to zero.");
        }
        this.offset = offset;
        this.length = length;
    }
}

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

public class ImmutableList implements Closeable {

    private static final int HEADER_LENGTH = Long.BYTES + Integer.BYTES * 2;
    private static final int OFFSET_LENGTH_PAIR_LENGTH = Long.BYTES + Integer.BYTES;

    private RandomAccessFile raf;
    private int size;
    private final int capacity;
    private final boolean readOnly;    

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
        byte[] b = new byte[capacity * OFFSET_LENGTH_PAIR_LENGTH];
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
        this.size = header.size();
        this.capacity = header.capacity();        
        this.readOnly = true;
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
        this.raf.writeLong(0L);
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
        this.raf.seek(Long.BYTES + Integer.BYTES);
        this.raf.writeInt(size);
    }

    public static ImmutableList open(Path path) throws IOException {
        return new ImmutableList(path); 
    }

    public int size() {
        return this.size;
    }

    public int capacity() {
        return this.capacity;
    }

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
        raf.writeInt(data.length);
        raf.seek(currOffset);
        raf.write(data);
        this.size++;        
    }    

    public synchronized byte[] get(int index) throws IOException {
        if (index < 0) {
            throw new IllegalArgumentException("index cannot be less than zero");
        }
        if (index >= this.size) {
            throw new IllegalArgumentException("index cannot be greater than size");
        }
        OffsetLengthPair pair = readOffsetLengthPair(index);
        if (pair.offset() <= 0) {
            throw new RuntimeException("invalid offset. possible data corruption");
        }
        if (pair.length() <= 0) {
            throw new RuntimeException("invalid length. possible data corruption");
        }
        raf.seek(pair.offset());
        byte[] buf = new byte[pair.length()];
        raf.readFully(buf);
        return buf;
    }

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
        return HEADER_LENGTH + index * OFFSET_LENGTH_PAIR_LENGTH;
    }

    private OffsetLengthPair readOffsetLengthPair(int index) throws IOException {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("index");
        }
        raf.seek(offsetPos(index));
        long offset = raf.readLong();
        int length = raf.readInt();
        return new OffsetLengthPair(offset, length);
    }
}