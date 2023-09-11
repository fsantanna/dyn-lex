#!/bin/sh

VER=v0.2.0

if [ -z "$1" ]
then
    echo "invalid target directory : missing argument"
    exit 1
fi

if [ -f "$1" ]
then
    echo "invalid target directory : target already exists"
    exit 1
fi

if [ -f "dlex-$VER.zip" ]
then
    echo "invalid source file : file \"dlex-$VER.zip\" already exists"
    exit 1
fi

echo Downloading...
wget -nv https://github.com/fsantanna/dyn-lex/releases/download/$VER/dlex-$VER.zip
# --show-progress --progress=bar:force

echo Unziping...
mkdir $1/
unzip dlex-$VER.zip -d $1/
