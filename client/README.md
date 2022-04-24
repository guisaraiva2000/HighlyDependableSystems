# Client


## Authors

Group T09


## About

This is a CLI (Command-Line Interface) application.


## Instructions for using Maven

To compile and run using _exec_ plugin:

```bash
mvn exec:java -DnByzantineServers=<nByzantineServers>
```

When prompted, the bank system will ask you for your username and password.
You can use the default ones that are listed in **_CLIENTS/users.txt_** file. 

This board shows how to perform the bank operations:

|         | Bank Operations             |
|---------|-----------------------------|
| open    |                             |    
| send    | %receiver_account% %amount% |
| check   | %account_name%              |
| receive |                             |
| audit   | %account_name%              |
| quit    |                             |

Note: "accountName" corresponds to the ID that references the bank account.


## To configure the Maven project in Eclipse

'File', 'Import...', 'Maven'-'Existing Maven Projects'

'Select root directory' and 'Browse' to the project base folder.

Check that the desired POM is selected and 'Finish'.


----

