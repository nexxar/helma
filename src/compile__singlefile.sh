#!/bin/sh

# get all library files
LIB_FILES=`ls ../lib/*.jar  ../lib/ext/*jar  | grep -v 'helma' | perl -e 'while (<STDIN>) {chomp; s/\\s+//g; print; print ":";}'`
javac -g:none -target 1.4 -source 1.4 -verbose -classpath .:$LIB_FILES:../classes/FESI:../classes/Acme:../classes/helma -d ../classes/ "$@"
pushd ./ > /dev/null
cd ../classes/
jar cMf ../lib/ext/nexxar-helma.jar *
popd > /dev/null
