# Importer

This repo contains Importer Helm chart for deploying on Kubernetes.

## Installation

Nodes where Importer needs to be deployed must be labeled. To label node use:

    $ kubectl label nodes node-list importer=true

Where:

 - `node-list` are nodes to label

To disable node selector just set `nodeselector` in `values.yaml` to `false`.  
Before installation fill `values.yaml` and to install Helm chart run:

    $ helm install some-chart-name --namespace=some-namespace-name --create-namespace path-to-chart-directory

Where:

- `some-chart-name` is Helm chart name
- `some-namespace-name` is optional argument that defines Kubernetes and Helm namespace where chart will be installed
- `path-to-chart-directory` is chart directory

## Values

Possible values to configure inside `values.yaml` are: 

 - `image` - defines Importer image name
 - `imageTag` - defines Importer image tag
 - `broker` - defines mqtt broker URL
 - `username` - defines Accumulo username used to access database
 - `password` - defines Accumulo password for specified username
 - `nodeSelector` - defines node selector usage (boolean) and possible values are `true` and `false`
 - `topic` - defines topic where Importer will listen on mqtt and ingest incoming data. It is **recommended** to use our defined topic.
 - `topicAdmin` - defines topic where Importer will listen on mqtt and do operations with schemas. It is **recommended** to use our defined topic.

## Uninstallation

To full uninstall run:

    $ helm del some-chart-name -n some-namespace-name

Where:

- `some-chart-name` is Helm chart name for uninstall.
- `some-namespace-name` is optional argument that defines Helm namespace where chart is installed
