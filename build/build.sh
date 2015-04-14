#!/bin/sh

# export JAVA_HOME=/usr/lib/j2sdk1.4.0

# on OSX use older JVMs, even if current java is set. Java 1.8 does not work
if [ -z "$JAVACMD" -a "$(uname -s)" == "Darwin" ]; then
    for version in 7 6 5 4; do
       [ -z "$JAVACMD" -a -e "/System/Library/Frameworks/JavaVM.framework/Versions/1.$version/Home/bin/java" ] && JAVACMD="/System/Library/Frameworks/JavaVM.framework/Versions/1.$version/Home/bin/java"
    done
fi
[ -z "$JAVACMD" -a "$(uname -s)" == "Darwin" ] && JAVACMD="`whereis java`"
[ -z "$JAVACMD" ] && JAVACMD="`whereis -b -B /usr/bin  -f java | sed -e 's#java: ##'`"
[ -z "$JAVA_HOME" -a ! -z "$JAVACMD" -a "$(uname -s)" == "Darwin" ] && JAVA_HOME=$(ruby -e "puts File.expand_path('$JAVACMD')" | sed "s:bin/java::")
[ -z "$JAVA_HOME" -a ! -z "$JAVACMD" ] && JAVA_HOME=$(readlink -f $JAVACMD | sed "s:bin/java::")


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

