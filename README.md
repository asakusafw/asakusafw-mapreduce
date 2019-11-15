# Asakusa on MapReduce

Asakusa on MapReduce provides faciities that make [Asakusa](https://github.com/asakusafw/asakusafw) batch applications run on [Hadoop](https://hadoop.apache.org/) MapReduce framework.


This project includes the followings:

* Asakusa on MapReduce Compiler
* Asakusa on MapReduce Runtime
* Asakusa on MapReduce [Gradle](http://gradle.org/) plug-in

## How to build

* requirements
  * Java SE Development Kit (>= 1.8)

### Maven artifacts

```sh
cd ..
./mvnw clean install [-DskipTests]
```

### Gradle plug-ins

```sh
cd gradle
./gradlew clean build [install] [-PmavenLocal]
```

## How to import projects into Eclipse

* Run `./mvnw process-test-resources eclipse:eclipse`
* And then import projects from Eclipse

If you run tests in Eclipse, please activate `Preferences > Java > Debug > 'Only include exported classpath entries when launching'`.

## Referred Projects
* [Asakusa Framework Core](https://github.com/asakusafw/asakusafw)
* [Asakusa Framework Documentation](https://github.com/asakusafw/asakusafw-documentation)

## License
* [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
