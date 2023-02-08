#!/bin/bash


# Change this to your netid
netid=rxk220018

#
# Root directory of your project
PROJDIR=$HOME/CS6378Project2

#
# Directory where the config file is located on your local system
CONFIGLOCAL=$PROJDIR/config.txt

KEY=$HOME

n=0

cat $CONFIGLOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read line
    i=$( echo $line | awk '{print $1}')
    echo $i
    while [[ $n -lt $i ]]
    do
    	read line
        host=$( echo $line | awk '{ print $2 }' )

        echo $host
        gnome-terminal -- ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host killall -u $netid &
        sleep 1

        n=$(( n + 1 ))
    done
   
)


echo "Cleanup complete"
