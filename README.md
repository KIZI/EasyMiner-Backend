# EasyMiner-Backend

This is a restricted easyminer backend repository which contains three basic backend services for:

1. Data source uploading - easyminer-data
2. Dataset creation and attributes preprocessing - easyminer-preprocessing
3. Association rules mining from a dataset - easyminer-miner

This public version contains only the 'limited' part of the easyminer backend.
It means that you can use only the mysql database as the main warehouse system for data sources and datasets,
and use only the R environment for the association rules mining with the 'arules' library.

## Installation

The easiest way for the easyminer backend installation is to use a docker environment.
In the [docker](docker) directory, which is placed in this repository, there is a build script and a complete manual how to build and
run a docker image containing all easyminer backend services including all dependencies.

## EasyMiner-Data

This service is responsible for data source uploading (in CSV format) to the easyminer warehouse.
It also provides operations for data source reading like: columns and instances browsing, histogram display, stats about numeric fields etc.
All operations are described within a [swagger document](EasyMiner-Data/src/main/resources/swagger.json).

* Endpoint: `http://<host>/<base-path>/api/v1`
* Swagger doc: `http://<host>/<base-path>/api/v1/doc.json`
* Swagger frontend: `http://<host>/<base-path>/index.html`

## EasyMiner-Preprocessing

This service uses uploaded data sources and creates datasets from them.
The dataset creation includes a field selection from which dataset attributes are making.
Datasets are saved in a specfic form which is adapted for the association rules mining.
This service provides same reading operations as the EasyMiner-Data module.
All operations are described within a [swagger document](EasyMiner-Preprocessing/src/main/resources/swagger.json).

* Endpoint: `http://<host>/<base-path>/api/v1`
* Swagger doc: `http://<host>/<base-path>/api/v1/doc.json`
* Swagger frontend: `http://<host>/<base-path>/index.html`

## EasyMiner-Miner

All datasets saved in the easyminer warehouse may be used for the association rules mining by this service.
The mining itself is processed in the R environment by 'arules' package.
This service is only a REST wrapper of this R library,
that performs the complete mining workflow from a dataset loading, to the mining and a result interpretation.
All operations of this service are described in a [swagger document](EasyMiner-Miner/src/main/resources/swagger.json).

* Endpoint: `http://<host>/<base-path>/api/v1`
* Swagger doc: `http://<host>/<base-path>/api/v1/doc.json`
* Swagger frontend: `http://<host>/<base-path>/index.html`
