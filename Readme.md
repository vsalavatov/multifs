# multifs

This is a Kotlin Multiplatform library that brings a universal platform-independent 
way of working with different storages (e.g. local filesystem or Google Drive) through
a conventional tree-structured directory/file interface. It gives the developer an 
ability to write logic tied with persistent data storages in the common module eliminating 
the need to write repetitive platform-specific code for each target platform.

### Currently available backends

| Name           | Description                                                | JVM Desktop | Android | JS browser |
|----------------|------------------------------------------------------------|-------------|---------|------------|
| `SystemFS`     | a proxy for `java.nio.Path`                                | +           | +       |            |
| `Google Drive` | a wrapper around Google API, based on Ktor 2.0             | +           | +       | +          |
| `SQLite`       | emulates a tree-structured filesystem in a SQLite database |             | +       |            | 

### Usage examples

* [multieditor](https://github.com/vsalavatov/multieditor) - basic multiplatform text editor built
  to illustrate usage of this library