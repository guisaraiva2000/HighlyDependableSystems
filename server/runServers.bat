IF %1.==. GOTO No1

set nByzantineServers=%1
set serverPort=8080
set /a N=3*%nByzantineServers%+1
set serverName=Server

setlocal ENABLEDELAYEDEXPANSION

for /L %%i in (1,1, %N%) do (

    SET "serverID=%%i"
    set serverName=%serverName%!serverID!

    cd storage || goto End1
    del /f data.txt
    cd ..

    set CMD="mvn exec:java -DserverName=!serverName! -DserverPort=!serverPort! -DnByzantineServers=%nByzantineServers%"
    start cmd.exe /k !CMD!

    set /a serverPort=serverPort+1
)

goto End1

:No1
  @echo Insert Byzantine Servers
  goto End1

:End1
endlocal
