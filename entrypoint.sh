#!/usr/bin/env bash

run_spark() {
  ./compiler.sh build
  shift;
  ./compiler.sh run $@
  code=$?
  echo "program return code ${code}"
  if [[ ${SLEEP} -ne 0 ]];
  then
    echo sleeping for ${SLEEP} seconds.
    sleep ${SLEEP}
  fi;
  exit ${code}
}


run_generate() {
 shift;
 project_name=$1
 if [[ -z ${project_name} ]];
 then
   project_name=recipe
 fi; 
 if [[ ! -d "/tmp/template" ]];
 then
   mkdir /tmp/template
 fi;
 cp -r /opt/app/create /tmp/template/${project_name}
 echo copied template to /tmp/template/${project_name}
 echo use -v pwd/project-name:/tmp/template to save it
}

usage() {
  echo here is a README
  cat /opt/app/create/README.md
  exit 1;
}

cmd=$1
case $cmd in
  run)
    run_spark $@
  ;;
  create)
    run_generate $@
  ;;
  *)
  usage
  ;;
esac

