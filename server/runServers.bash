#!/bin/bash
nByzantineServers=$1
ServerPort=8080

if [ "$#" -ne 1 ]; then
    echo "Insert Byzantine Servers"
    exit 2
fi

for ((i = 1; i <= 3 * nByzantineServers + 1; i++)); do

  ServerName="Server${i}"

  cd storage/${ServerName} || exit
  # Cleaning & Starting DB
  rm data.txt
  cd ../.. || exit

  CMD="mvn exec:java -DserverName=\"${ServerName}\" -DserverPort=\"${ServerPort}\" -DnByzantineServers=\"${nByzantineServers}\" 2>/dev/null"

  gnome-terminal -- bash -c "${CMD}"
  ((ServerPort++))

done
