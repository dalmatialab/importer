![example workflow](https://github.com/dalmatialab/importer/actions/workflows/main.yml/badge.svg) 

# Supported tags and respective Dockerfile links

 - [1.0-rc-1](https://github.com/dalmatialab/importer/blob/6d52ebe39108fabb6a851c473822ea206774e621/Dockerfile)

# What is Importer ? 

Importer is costum created script in scala to ingest data to Geomesa. Also it is possible to create, delete and list catalog schemas. List operation show results only in Importer container logs.

# How to use this image

    $ docker run -d --name some-name -e broker=some-broker -e username=some-username -e password=some-password -e instanceID=some-instance -e zookeepers=some-zookeeper -e topic="geomesa/#" -e topicAdmin="geomesaADMIN" image:tag

Where:

 - `some-name` is name you want to assign to your container
 - `some-broker` is URL of mqtt broker that Importer will connect to and listen on specified topics
 - `some-username` is Accumulo username used to access database
 - `some-password` is Accumulo password for specified username
 - `some-instance` is Accumulo instanceID (usually Accumulo)
 - `some-zookeeper` is Zookeeper endpoint that Accumulo use
 - `image` is Docker image name
 - `tag` is Docker image version

`Topic` and `topicAdmin` presents topic where Importer will listen and do actions based on incoming messages on them. It is **recommended** to use our topics.
`Topic` is used for ingesting data, while `topicAdmin` is used for operations with schemas.

## Environment variables

**broker**

This is *required* variable. It specifies URL of mqtt broker that Importer will connect to and listen on specified topics.

**username**

This is *required* variable. It specifies Accumulo username used to access database.

**password**

This is *required* variable. It specifies Accumulo password for specified username.

**instanceID**

This is *required* variable. It specifies instanceID name (usually Accumulo).

**zookeepers**

This is *required* variable. It specifies Zookeeper endpoint at which Importer will connect. It must be same Zookeeper that Accumulo use.

**TZ**

This is *optional* variable. It specifes timezone. Default value is `Europe/Zagreb`.

## NOTE

To create schema publish to mqtt `topicAdmin` or your costum topic following payload:

    '{"action":"create","catalog":"geomesa.catalog","schemaname":"schema1","schema":"device_id:String:index=true,*geometry:Point:srid=4326","user-data":{"geomesa.z3.interval":"week","geomesa.xz.precision":"12"}}'

where:
 - `action` - defines schema action (`create` for create, `list` for list, `delete` for delete)
 - `catalog` - defines catalog where schema will be created. It must be defined in form namespace.catalog (namespace where Geomesa jar was loaded)
 - `schemaname` - defines schema name
 - `schema` - defines schema structure to create. Check [Geomesa documentation](https://www.geomesa.org/documentation/stable/user/datastores/index_config.html) to learn how to define schema structure.
 - `user-data` - defines **non-required** costum user-data supported by Geomesa. Check [Geomesa documentation](https://www.geomesa.org/documentation/stable/user/datastores/index_config.html) to learn what user-data is available. If not passing user-data remove user-data section from payload.


To delete schema publish to mqtt `topicAdmin` or your costum topic following payload:

    '{"action":"delete","catalog":"geomesa.catalog","schemaname":"schema1"}'

where:
 - `action` - defines schema action (`create` for create, `list` for list, `delete` for delete)
 - `catalog` - defines catalog name where schema is created. It must be defined in form namespace.catalog
 - `schemaname` - defines schema name for delete

To list all catalog schema publish to mqtt `topicAdmin` following payload:

    '{"action":"list","catalog":"geomesa.catalog"}'

where:
 - `action` - defines schema action (`create` for create, `list` for list, `delete` for delete)
 - `catalog` - defines catalog name. It must be defined in form namespace.catalog

To ingest data check costum python [script](./ingest.py) inside this repo. Function arguments are:
 - `data` - csv file used for ingesting
 - `indices` - csv file fields used to create index to prevent ingesting same data multiple times. If None, all fields are used.
 - `catalog` - catalog name to ingest data
 - `schema` - schema name to ingest data
 - `mqtt_ip` - mqtt to publish data. Must be same mqtt where Importer listen.

Example payload to publish is:

    {"device_id":"device_id","type":"type","geometry":"POINT (X Y)","timestamp":"2019-12-31T22:00:00.000","value":4.2,"tags":"source=source,city=city,altitude=altitude","INDEX":["device_id","type","geometry","timestamp"]}


# License

