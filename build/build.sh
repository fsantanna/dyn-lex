#!/bin/sh

# 1. Edit version:
#   - README.md
#   - Main.kt
#   - install.sh
#   - build.sh
# 2. Build:
#   - XCEU = true
#   - Build artifacts...
#   - ./build.sh
# 3. Upload:
#   - https://github.com/fsantanna/dlex/releases/new
#   - tag    = <version>
#   - title  = <version>
#   - Attach = { .zip, install.sh }

VER=v0.2.1
DIR=/tmp/dlex-build/

rm -Rf $DIR
rm -f  /tmp/dlex-$VER.zip
mkdir -p $DIR

cp dlex.sh $DIR/dlex
cp prelude.ceu $DIR/prelude.ceu
cp hello-world.ceu $DIR/
cp ../out/artifacts/dlex_jar/dlex.jar $DIR/dlex.jar

cd /tmp/
zip dlex-$VER.zip -j dlex-build/*

echo "-=-=-=-"

cd -

cd $DIR/
./dlex --version
echo "-=-=-=-"
./dlex hello-world.ceu
echo "-=-=-=-"

cd -
cp install.sh install-$VER.sh
cp /tmp/dlex-$VER.zip .

ls -l install-*.sh dlex-*.zip
echo "-=-=-=-"
unzip -t dlex-$VER.zip
