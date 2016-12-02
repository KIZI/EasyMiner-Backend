EasyMiner-Backend Docker
======================

Requirements: Docker 1.12+

For building of a docker image, please follow these instructions:

1. Build a docker image:

        docker build -t easyminer-backend https://github.com/KIZI/EasyMiner-Backend.git#v2.4:docker

      Or
      
        docker pull kizi/easyminer-backend:v2.4
      
2. After the image has been successfully built you can run it:

        docker run -d -p 8893:8893 -p 8891:8891 -p 8892:8892 --name easyminer-backend -e EM_USER_ENDPOINT=<easyminercenter-url> easyminer-backend
        
      Where `<easyminercenter-url>` is a valid URL to the easyminercenter service (for example: http://localhost:8894/easyminercenter). All backend services uses this endpoint, therefore you need to install easyminercenter first.
        
3. Finally, you can use all three easyminer backend services: data (exposed port 8891), preprocessing (exposed port 8892) and miner (exposed port 8893)

## License ##
EasyMiner with R backend is generally provided under open license. Most EasyMiner-specific code is licensed under the very permissive BSD license. EasyMiner relies on a number of third-party components which may use different license types. 

The software comes with absolutely no warranty.

## Additional information ##

REST API endpoints are accessible on:

* http://localhost:8891/easyminer-data/index.html - data service
* http://localhost:8892/easyminer-preprocessing/index.html - preprocessing service
* http://localhost:8893/easyminer-miner/index.html - miner service

Possible docker RUN modes:

* ```docker run -d easyminer-backend``` - background
* ```docker run -i -t easyminer-backend -bash``` - foreground with bash in stdin

Required environment variables:

* ```EM_USER_ENDPOINT``` - URL to the easyminercenter endpoint
