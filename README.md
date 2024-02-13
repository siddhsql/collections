# README

This project aims to provide Java collections that are backed by off-heap (i.e., disk) storage.
The emphasis is on stability and reliability over performance.
The project has no dependencies other than JUnit for testing.

Currently, it provides an `ImmutableList` collection with O(1) insertions and lookups as well O(1) main memory usage.
The use-case is for scenarios where the list is so large that it cannot fit in memory (or when you cannot fit all lists in memory) and where you need persistence.
Because the data in the list is backed by disk, elements in the list have to be serialized and deserialized by your application and read and written as byte buffers.
According to [Martin Kleppmann](https://www.amazon.com/Designing-Data-Intensive-Applications-Reliable-Maintainable/dp/1449373321) this serialization and deserialization is the performance bottleneck when comparing the performance to in-memory collections.
He writes (p.89):

>Counterintuitively, the performance advantage of in-memory databases is not due to the fact that they don't need to read from disk. Rather they can be faster because they can avoid the overheads of encoding in-memory data structures in a form that can be written to disk

Limitations of `ImmutableList`:

- size has to be declared in advance
- elements cannot be deleted
- elements cannot be changed

Refer to tests for how to use `ImmutableList` in your own project.

## Usage

Clone the repo. Run

```
mvn install
```

then add dependency to your project:

```
<dependency>
    <groupId>hungama</groupId>
    <artifactId>collections</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## License

[APACHE LICENSE, VERSION 2.0](https://www.apache.org/licenses/LICENSE-2.0)