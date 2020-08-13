#!/usr/bin/env bash
CP="."

CP="\${CP}<%items.each {k, v -> v.each {item -> %>:$item<%}}%>"

FILES=""
for f in \$(find source -name "*.scala");
do
    FILES="\$FILES \$f"
done;

compile() {
    if [[ ! -d "out" ]];
    then
        mkdir out;
    fi;
    scalac -classpath \${CP} -d out \${FILES}
}

execute() {
    java -Xms1024m -Xmx2048m -classpath \${CP}:out \$1
}

cmd=\$1
args=\$2
case \$cmd in
    build)
    compile
    ;;
    run)
    execute \$2
    ;;
    *)
    echo "unknown command"
    ;;
esac
