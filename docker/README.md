# EasyMiner-Docker #

This is an installation package of the easyminer backend for a docker environment. For the building a docker image, please follow these instructions:


## Additional information ##

REST API endpoints are accessible on:

* http://localhost:8891/easyminer-data/index.html - data service
* http://localhost:8892/easyminer-preprocessing/index.html - preprocessing service
* http://localhost:8890/easyminer-miner/index.html - miner service

Possible docker RUN modes:

* ```docker run -d easyminer-backend``` - background
* ```docker run -i -t easyminer-backend -bash``` - foreground with bash in stdin

Required environment variables:

* ```EM_USER_ENDPOINT``` - URL to the easyminercenter endpoint