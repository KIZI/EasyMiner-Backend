EasyMiner-Docker
======================

This is an installation package of the easyminer backend for a docker environment. For the building a docker image, please follow these instructions:

1. Build a docker image:

        docker build -t easyminer-backend --build-arg EM_USER_ENDPOINT=<easyminercenter-url> https://github.com/KIZI/EasyMiner-Backend.git#:docker

      Where `<easyminercenter-url>` is a valid URL to the easyminercenter service. All backend services uses this endpoint, therefore you need to install easyminercenter first.
2. After the image has been successfully built you can run it:

        docker run -d -p 8890:8890 -p 8891:8891 -p 8892:8892 --name easyminer-backend easyminer-backend
3. Finally, you can use all three easyminer backend services: data (exposed port 8891), preprocessing (exposed port 8892) and miner (exposed port 8890)


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

## Backend integration with the frontend ##

### Free version ###



### Hadoop version ###

install password: 12345

```
> HTTP_SERVER_ADDR=<docker-server>
> docker network create easyminer
> docker pull mysql:5.7
> docker run --name easyminer-mysql -e MYSQL_ROOT_PASSWORD=root -d mysql:5.7
> docker network connect easyminer easyminer-mysql
> docker build -t easyminer-frontend https://github.com/KIZI/EasyMiner-EasyMinerCenter.git#:docker
> docker run -d -p 8894:80 --name easyminer-frontend -e HTTP_SERVER_NAME=$HTTP_SERVER_ADDR
> docker network connect easyminer easyminer-frontend
> git clone -b v2.0 git@bitbucket.org:easyminer/easyminer-docker.git
> cd easyminer-docker
> cp <bitbucket-private-key-file> ./bitbucket-private-key
> cp <easyminer-kerberos-keytab-file> ./easyminer.keytab
```
