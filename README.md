# T09-BFTB

High Reliability Systems 2021-2022, 2nd semester project


## Authors

**Group T09**

Maria Gomes 102203

Guilherme Saraiva 93717

Bruno Silva


### Prerequisites

Java Developer Kit 11 is required running on Linux, Windows or Mac.
Maven 3 is also required.

To confirm that you have them installed, open a terminal and type:

```
javac -version

mvn -version
```

### Installing

To compile and install all modules:

```
mvn clean install -DskipTests
```

The integration tests are skipped because they require the servers to be running.

### Server

To run the only replica, access the **_server_** folder and type:
```
mvn exec:java
```

### Client

To perform the bank operations, access the **_client_** folder and run:
```
mvn exec:java
```

### Testing

To run the integrations tests, start the server, then access the **_tester_** folder and execute:
```
mvn verify
```


## Built With

* [Maven](https://maven.apache.org/) - Build Tool and Dependency Management
* [gRPC](https://grpc.io/) - RPC framework


