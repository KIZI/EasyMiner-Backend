EasyMiner-Docker
======================

This is an installation package of the easyminer backend for a docker environment. For the building a docker image, please follow these instructions:

1. Build a docker image:

        docker build -t easyminer-backend --build-arg EM_USER_ENDPOINT=<easyminercenter-url> https://github.com/KIZI/EasyMiner-Backend.git#:docker

      Where `<easyminercenter-url>` is a valid URL to the easyminercenter service. All backend services uses this endpoint, therefore you need to install easyminercenter first.
2. After the image has been successfully built you can run it:

        docker run -d -p 8893:8893 -p 8891:8891 -p 8892:8892 --name easyminer-backend easyminer-backend
3. Finally, you can use all three easyminer backend services: data (exposed port 8891), preprocessing (exposed port 8892) and miner (exposed port 8893)


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

## Backend integration with the frontend ##

### Basic version ###

Requirements: Docker 1.12+

```bash
#!/bin/bash
#user inputs
HTTP_SERVER_ADDR=<docker-server>

#commands
docker network create easyminer
docker pull mysql:5.7
docker run --name easyminer-mysql -e MYSQL_ROOT_PASSWORD=root --network easyminer -d mysql:5.7
docker build -t easyminer-frontend https://github.com/KIZI/EasyMiner-EasyMinerCenter.git#:docker
docker run -d -p 8894:80 --name easyminer-frontend -e HTTP_SERVER_NAME="$HTTP_SERVER_ADDR:8894" --network easyminer easyminer-frontend
docker build -t easyminer-backend https://github.com/KIZI/EasyMiner-Backend.git#:docker
docker run -d -p 8893:8893 -p 8891:8891 -p 8892:8892 --name easyminer-backend -e EM_USER_ENDPOINT=http://easyminer-frontend/easyminercenter --network easyminer easyminer-backend
```

* Web GUI: http://\<docker-server\>:8894/easyminercenter
* Frontend re-install page: http://\<docker-server\>:8894/easyminercenter/install (password: 12345)

### Hadoop version ###

Requirements: Docker 1.12+

You should use absolute paths to a bitbucket private key file and kerberos keytab file!

```bash
#!/bin/bash
#user inputs
HTTP_SERVER_ADDR=<docker-server>
BITBUCKET_PRIVATE_KEY=<bitbucket-private-key-file>
EASYMINER_KERBEROS_KEYTAB=<easyminer-kerberos-keytab-file>

#commands
docker network create easyminer
docker pull mysql:5.7
docker run --name easyminer-mysql -e MYSQL_ROOT_PASSWORD=root --network easyminer -d mysql:5.7
docker build -t easyminer-frontend https://github.com/KIZI/EasyMiner-EasyMinerCenter.git#:docker
docker run -d -p 8894:80 --name easyminer-frontend -e HTTP_SERVER_NAME="$HTTP_SERVER_ADDR:8894" --network easyminer easyminer-frontend
git clone -b v2.0 https://bitbucket.org/easyminer/easyminer-docker.git
cd easyminer-docker
cp $BITBUCKET_PRIVATE_KEY ./bitbucket-private-key
cp $EASYMINER_KERBEROS_KEYTAB ./easyminer.keytab
docker build -t easyminer-backend .
docker run -d -p 8893:8893 -p 8891:8891 -p 8892:8892 --name easyminer-backend -e EM_USER_ENDPOINT=http://easyminer-frontend/easyminercenter --network easyminer easyminer-backend
```

* Web GUI: http://\<docker-server\>:8894/easyminercenter
* Frontend re-install page: http://\<docker-server\>:8894/easyminercenter/install (password: 12345)
