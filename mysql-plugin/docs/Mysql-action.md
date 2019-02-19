# MySQL Action


Description
-----------
Action that runs a MySQL command.


Use Case
--------
The action can be used whenever you want to run a Mysql command before or after a data pipeline.
For example, you may want to run a sql update command on a database before the pipeline source pulls data from tables.


Properties
----------
**Driver Name:** Name of the JDBC driver to use.

**Database Command:** Database command to execute.

**Host:** Host that MySQL is running on.

**Port:** Port that MySQL is running on.

**Database:** MySQL database name.

**Username:** User identity for connecting to the specified database.

**Password:** Password to use to connect to the specified database.

**Connection Arguments:** A list of arbitrary string tag/value pairs as connection arguments. These arguments
will be passed to the JDBC driver, as connection arguments, for JDBC drivers that may need additional configurations.

**Auto Reconnect** Should the driver try to re-establish stale and/or dead connections.

Example
-------
Suppose you want to execute a query against a MySQL database named "prod" that is running on "localhost" 
port 3306 (Ensure that the driver for MySQL is installed. You can also provide driver name for some specific driver, 
otherwise "mysql" will be used), then configure the plugin with:

```
Driver Name: "mariadb"
Database Command: "UPDATE table_name SET price = 20 WHERE ID = 6"
Host: "localhost"
Port: 3306
Database: "prod"
```