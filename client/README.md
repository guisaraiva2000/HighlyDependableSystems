# Client


## Authors

Group T09


## About

This is a CLI (Command-Line Interface) application.


## Instructions for using Maven

To compile and run using _exec_ plugin:

```
mvn compile exec:java 
```

When prompted, the bank system will ask you for your username and password.
You can use the default ones that are listed in **_CLIENTS/users.txt_** file. 

This board shows how to perform the bank operations:

|         | Bank Operations                              |
|---------|----------------------------------------------|
| open    | %accountName%                                |    
| send    | %sender_account% %receiver_account% %amount% |
| check   | %account_name% %client_account_name%         |
| receive | %account_name%                               |
| audit   | %account_name% %client_account_name%         |

Note: "accountName" corresponds to the ID that references the bank account.


## To configure the Maven project in Eclipse

'File', 'Import...', 'Maven'-'Existing Maven Projects'

'Select root directory' and 'Browse' to the project base folder.

Check that the desired POM is selected and 'Finish'.


----

