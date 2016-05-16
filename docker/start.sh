#!/bin/bash 

IS_TERMINATED=false

_term() {  
  /etc/init.d/easyminer-miner stop
  /etc/init.d/easyminer-preprocessing stop
  /etc/init.d/easyminer-data stop
  sleep 1
  echo "Services have been stopped!"
  IS_TERMINATED=true 
}

trap _term SIGTERM SIGINT

/etc/init.d/easyminer-data start
/etc/init.d/easyminer-preprocessing start
/etc/init.d/easyminer-miner start

if [[ $1 == "-d" ]]; then
  while ! $IS_TERMINATED; do sleep 5; done
fi

if [[ $1 == "-bash" ]]; then
  /bin/bash
  _term
fi