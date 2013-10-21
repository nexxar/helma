#!/bin/sh

# export JAVA_HOME=/usr/lib/j2sdk1.4.0

JAVA_BIN="`whereis -b -B /usr/bin  -f javac | sed -e 's#javac: ##'`"
[ -z "$JAVA_HOME" -a ! -z "$JAVA_BIN" ] && JAVA_HOME=$(readlink -f $JAVA_BIN | sed "s:bin/javac::")

#--------------------------------------------
# No need to edit anything past here
#--------------------------------------------
if test -z "${JAVA_HOME}" ; then
    echo "ERROR: JAVA_HOME not found in your environment."
    echo "Please, set the JAVA_HOME variable in your environment to match the"
    echo "location of the Java Virtual Machine you want to use."
    exit
fi

if test -f ${JAVA_HOME}/lib/tools.jar ; then
    CLASSPATH=${CLASSPATH}:${JAVA_HOME}/lib/tools.jar
fi

if test -n "${2}" ; then
	APPNAME=-Dapplication=${2}
fi

CP=${CLASSPATH}:ant.jar:jaxp.jar:../lib/crimson.jar

echo "Classpath: ${CP}"
echo "JAVA_HOME: ${JAVA_HOME}"

BUILDFILE=build.xml

${JAVA_HOME}/bin/java -classpath ${CP} ${APPNAME} org.apache.tools.ant.Main -buildfile ${BUILDFILE} ${1}

