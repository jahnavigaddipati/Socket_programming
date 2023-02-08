#!/bin/bash

# Change this to your netid
netid=rxk220018

# Root directory of your project
PROJDIR=$HOME/CS6378Project2

# Directory where the config file is located on your local system
CONFIGLOCAL=$HOME/CS6378Project2/config.txt

# Directory your java classes are in
BINDIR=$PROJDIR/bin

# Your main project class
PROG=TCPProject2Node

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
    	p=$( echo $line | awk '{ print $1 }' )
        host=$( echo $line | awk '{ print $2 }' )
	
	gnome-terminal -- ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host java -cp $BINDIR $PROG $p; exec bash &

        n=$(( n + 1 ))
    done
)
