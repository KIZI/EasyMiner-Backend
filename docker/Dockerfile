# building: docker build -t easyminer-backend --build-arg EM_USER_ENDPOINT=https://easyminer-demo.lmcloud.vse.cz/easyminercenter https://github.com/KIZI/EasyMiner-Backend.git#:docker
# run background  mode: docker run -d -p 8890:8890 -p 8891:8890 -p 8892:8890 --name easyminer-backend easyminer-backend
# run interactive mode: docker run -t -i -p 8890:8890 -p 8891:8890 -p 8892:8890 --name easyminer-backend easyminer-backend -bash
# ports: 8890 - easyminer-miner
#        8891 - easyminer-data
#        8892 - easyminer-preprocessing 
FROM debian:jessie

MAINTAINER kizi "prozeman@gmail.com"

ARG EM_USER_ENDPOINT

WORKDIR /root

# set repositories
RUN echo 'deb http://http.debian.net/debian jessie-backports main' >> /etc/apt/sources.list && \
    echo 'deb http://dl.bintray.com/sbt/debian /' >> /etc/apt/sources.list && \
    echo 'deb http://cran.r-project.org/bin/linux/debian jessie-cran3/' >> /etc/apt/sources.list && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823 && \
    apt-key adv --keyserver keys.gnupg.net --recv-key 381BA480 && \
    apt-get update

# install tools needed for compilation of the easyminer backend
RUN apt-get install -y openjdk-8-jdk git sbt curl

# install R environment
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ADD r-install.R /root
RUN apt-get install -y r-base r-base-dev libcurl4-openssl-dev libssl-dev && \
    R CMD javareconf && \ 
    Rscript r-install.R

# pull easyminer backend code from github
RUN git clone https://github.com/KIZI/EasyMiner-Backend.git
WORKDIR EasyMiner-Backend

# compile easyminer backend to jar files
RUN sbt "project data" "pack" && \
    sbt "project preprocessing" "pack" && \
    sbt "project miner" "pack"

# copy all setting files and compiled files to easyminer service directories
ADD easyminer-data /root/easyminer-data
ADD easyminer-preprocessing /root/easyminer-preprocessing
ADD easyminer-miner /root/easyminer-miner
ADD etc/init.d/* /etc/init.d/
RUN mv EasyMiner-Data/target/pack/lib /root/easyminer-data && \
    mv EasyMiner-Preprocessing/target/pack/lib /root/easyminer-preprocessing && \
    mv EasyMiner-Miner/target/pack/lib /root/easyminer-miner
WORKDIR /root 
RUN mv easyminer-preprocessing/lib/preprocessing_2.11-1.0.jar easyminer-preprocessing/bin && \
    mv easyminer-miner/lib/miner_2.11-1.0.jar easyminer-miner/bin && \ 
    chmod 755 /etc/init.d/easyminer-data && \
    chmod 755 /etc/init.d/easyminer-preprocessing && \
    chmod 755 /etc/init.d/easyminer-miner && \
    chmod 755 easyminer-data/run && \
    chmod 755 easyminer-data/stop && \
    chmod 755 easyminer-data/bin/main && \
    chmod 755 easyminer-preprocessing/run && \
    chmod 755 easyminer-preprocessing/stop && \
    chmod 755 easyminer-preprocessing/bin/main && \
    chmod 755 easyminer-miner/run && \
    chmod 755 easyminer-miner/check && \
    chmod 755 easyminer-miner/bin/main && \
    tar -xzf easyminer-data/webapp.tar.gz -C easyminer-data && \
    tar -xzf easyminer-preprocessing/webapp.tar.gz -C easyminer-preprocessing && \
    tar -xzf easyminer-miner/webapp.tar.gz -C easyminer-miner && \
    rm easyminer-data/webapp.tar.gz && \
    rm easyminer-preprocessing/webapp.tar.gz && \
    rm easyminer-miner/webapp.tar.gz

ENV EM_USER_ENDPOINT $EM_USER_ENDPOINT

ADD start.sh /root
RUN chmod 755 start.sh

EXPOSE 8890 8891 8892
ENTRYPOINT ["./start.sh"]
CMD ["-d"]