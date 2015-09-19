# Makefile for edu.washington.apl.aganse.SpheRayDemo (yes I prefer "make" to "ant"!)
# (note JAVABASEDIR & CODEDIR must be changed for different platforms)

JAVABASEDIR = /Users/aganse/APLUW/src/java
CODEDIR = ${JAVABASEDIR}/edu/washington/apl/aganse/SpheRayDemo

all: SpheRayDemo.java Makefile
	javac -sourcepath ${JAVABASEDIR} -classpath ${CODEDIR}/classes \
	      ${CODEDIR}/SpheRayDemo.java -d ${CODEDIR}/classes
	cd ${CODEDIR}/classes; jar cmf ../mainclass.mf ../spheraydemo.jar *

doc: SpheRayDemo.java
	javadoc -d doc -author -version SpheRayDemo.java

srcjar:
	make clean
	jar cf spheraydemo.src.jar SpheRayDemo.java Makefile SpheRayDemo.html \
		mainclass.mf classes

clean:
	\rm -rf ${CODEDIR}/classes/edu
	\rm -rf ${CODEDIR}/classes/ptolemyUpdates
	\rm -rf ${CODEDIR}/classes/*.class
	\rm -rf ${CODEDIR}/spheraydemo.jar
