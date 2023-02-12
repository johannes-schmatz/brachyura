# print what's done, exit on errors
set -x -e

javac -Xlint:all -g -Xdiags:verbose Buildscript.java

java Buildscript build

java -cp build/out.jar -DprojectDir=. -Dtests=true io.github.coolcrabs.brachyura.bootstrap.Main $@
