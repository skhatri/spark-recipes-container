#!/usr/bin/env bash

./compiler.sh build
./compiler.sh run $@
code=$?
echo "program return code ${code}"
if [[ ${SLEEP} -ne 0 ]];
then
  echo sleeping for ${SLEEP} seconds.
  sleep ${SLEEP}
fi;
exit ${code}
