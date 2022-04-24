# Server


## Authors

Group T09


## About

This is a gRPC server defined by the protobuf specification.

The server runs in a stand-alone process.


## Instructions for using Maven

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

When running, the server await connections from clients.


## To configure the Maven project in Eclipse

'File', 'Import...', 'Maven'-'Existing Maven Projects'

'Select root directory' and 'Browse' to the project base folder.

Check that the desired POM is selected and 'Finish'.


----

