#!/usr/bin/make -f

# The Makefile for CS6378Project2

# the phony targets
.PHONY: all compile run clean

# all is the default phony target; it is placed here so 'make' and 'make all' do the same thing
all: compile run

# compile only generates the class files
compile:
	javac -d bin src/*.java

# run removes previous output files and runs the program via the launcher script
run:
	rm -f *.out
	./launcher.sh

# clean removes all emacs backup files, all output files, and everything in the bin directory; also launches the cleanup script
clean:
	rm -f *~ *\# *.out bin/*
	./cleanup.sh
