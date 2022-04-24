# T09-BFTB

Highly Dependable Systems 2021-2022, 2nd semester project


## Authors

**Group T09**

Maria Gomes 102203

Guilherme Saraiva 93717

Bruno Silva 102172


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

```bash
mvn clean install -DskipTests
```


### Server

To run the servers, access the **_server_** folder and type:
```bash
.\runServers.bat <nByzantineServers>  
```
if you are on Windows, and if you are on Linux type:
```bash
chmod +777 runServers.bash 
./runServers.bash <nByzantineServers>
```
where _nByzantineServers_ is the number of byzantine faults that you want the system to support.
We recommend a maximum of 2 faults.
### Client

To perform the bank operations, access the **_client_** folder and run:
```bash
mvn exec:java -DnByzantineServers=<nByzantineServers>
```
where _nByzantineServers_ is the same number of faults that you put on server command.

Check Client's **_README.md_** for more information.

### Testing

To run the integrations tests, start the server, then access the **_tester_** folder and execute:
```
mvn verify
```


## Built With

* [Maven](https://maven.apache.org/) - Build Tool and Dependency Management
* [gRPC](https://grpc.io/) - RPC framework


