import org.geotools.data.DataStoreFinder;
import org.locationtech.geomesa.accumulo.data.AccumuloDataStoreParams;
import scala.collection.JavaConverters._
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.util.concurrent.PriorityBlockingQueue;

import java.security.MessageDigest;
import java.math.BigInteger;
import org.geotools.util.factory.Hints;

import java.lang.IllegalArgumentException;
import java.io.IOException;
import scala.util.control.NonFatal;
import java.lang.RuntimeException;

import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes

object Geomesa {

    val queue = new PriorityBlockingQueue[String];

    var writer: FeatureWriter[SimpleFeatureType,SimpleFeature] = null;
    var catalog: String = null;
    var schema: String = null;
    
    var counter: Int = 0;
    var errors: Int = 0;
    
    def createGeomesaStore(catalog: String): org.geotools.data.DataStore = 
    {
		val params = Map(AccumuloDataStoreParams.InstanceIdParam.key -> sys.env("instanceID"),
						AccumuloDataStoreParams.ZookeepersParam.key -> sys.env("zookeepers"),
         				AccumuloDataStoreParams.UserParam.key -> sys.env("username"),
         				AccumuloDataStoreParams.PasswordParam.key -> sys.env("password"),
         				AccumuloDataStoreParams.CatalogParam.key -> catalog);
       	val store = DataStoreFinder.getDataStore(params.asJava);
            	
       	return store
    }
    
    def connectToGeomesa(catalog: String, schema: String) =
    {
        if (this.writer == null || this.catalog != catalog || this.schema != schema)
        {
            if (writer != null)
            {
                writer.close
            }
            
            val store = createGeomesaStore(catalog);
            
            try
            {
                val writer = store.getFeatureWriterAppend(schema, Transaction.AUTO_COMMIT);
                this.writer = writer;
                this.catalog = catalog;
                this.schema = schema;
                
                println(java.time.LocalDateTime.now + " INFO: Writer created for catalog " + catalog + " and schema " + schema);
            }
            catch
            {
                case err: IOException => {
                    println(java.time.LocalDateTime.now + " ERROR: " + err.toString);
                    System.exit(1);
                }
            }
        }
    }

    def connectToMqtt(broker: String, topic: String, clientName: String) =
    {
        val persistence = new MemoryPersistence;
        val client = new MqttClient(broker, clientName, persistence);
        
        val connOpts = new MqttConnectOptions();
        connOpts.setConnectionTimeout(0);
        connOpts.setKeepAliveInterval(0);

        client.connect(connOpts);
        client.subscribe(topic);

		if (clientName == "Importer"){
			val callback = new MqttCallback
			{
				override def messageArrived(topic: String, message: MqttMessage): Unit = 
				{
		//                println("Message received in topic <%s>: %s".format(topic, message));
				    queue.add(topic + "=:=" + message.toString());
				}
				override def connectionLost(cause: Throwable): Unit = 
				{
				    println(java.time.LocalDateTime.now + " " + cause);
				}
				override def deliveryComplete(token: IMqttDeliveryToken): Unit = 
				{
				    
				}
			}
		    client.setCallback(callback);
		}
		else if  (clientName == "ImporterAdmin"){
			val callback = new MqttCallback
			{
				override def messageArrived(topic: String, message: MqttMessage): Unit = 
				{
				    parseMessage(message.toString())
				}
				override def connectionLost(cause: Throwable): Unit = 
				{
				    println(java.time.LocalDateTime.now + " " + cause);
				}
				override def deliveryComplete(token: IMqttDeliveryToken): Unit = 
				{
				    
				}
			}
		    client.setCallback(callback);
		}
    }

    def main(args: Array[String])
    {
        // Connect to MQTT
        val broker = sys.env("broker");
        val topic = sys.env("topic");
        this.connectToMqtt(broker, topic, "Importer");
        
        val topicAdmin = sys.env("topicAdmin");
        this.connectToMqtt(broker, topicAdmin, "ImporterAdmin");

        while(true)
        {
            val pop = this.queue.take();
            
            val topic = pop.split("=:=")(0);
            val message = pop.split("=:=")(1);
//            println("TOPIC: " + topic);
//            println("MESSAGE: " + message);
            
            val catalog = topic.split("/")(1);
            val schema = topic.split("/")(2);
//            println("CATALOG: " + catalog);
//            println("SCHEMA: " + schema);

            this.connectToGeomesa(catalog, schema);

            var data = ujson.read(message);

            // Get field names for creating index
            var indices: List[String] = data.obj.keys.toList;	//create list of index fields; default is all fields
//            println("INDICES: " + indices.toString + " -- " + indices.getClass.toString);
            if (data.obj.keys.exists(key => key=="INDEX"))	//if item INDEX exists, use it as index fields
            {
//                println("INDEX exists!!!")
                indices = data("INDEX").arr.toList.map(_.value.toString);
                data.obj.remove("INDEX");
//                println("INDICES: " + indices.toString);
            }

            // Create index based on list of fields
            var index = ""
            indices.foreach(item => 
            {
//                println("ITEM: " + item + "  -  " + item.getClass.toString);
                index = index.concat(data(item).value.toString + ",");
            });
            index = index.dropRight(1)	//remove the last character ","
//            println("INDEX: " + index);
            
            try 
            {
                val next = writer.next();
                next.getUserData.put(Hints.PROVIDED_FID, md5HashString(index));
                data.obj.keys.foreach(item => 
                {
                    next.setAttribute(item, data(item).value);
                });
                writer.write();
            }
            catch
            {
                case err: NullPointerException => {
                    println(java.time.LocalDateTime.now + " ERROR: Writer not initialized. Will try to initialize it...");
                    this.connectToGeomesa(this.catalog, this.schema);
                }
                //case argExp: IllegalArgumentException => println("ERROR: " + argExp.toString);
                case NonFatal(t) => {
                    if (this.errors == 0) {
                        println(java.time.LocalDateTime.now + " ERROR: Records failing... -- " + t.toString);
                    }
                    this.errors = this.errors + 1;
                }
                case runErr: RuntimeException => {
                    println(java.time.LocalDateTime.now + " ERROR: Something went wrong with the writer -- " + runErr.toString);
                    
                    this.writer.close;
                    this.writer = null;
                    this.connectToGeomesa(this.catalog, this.schema);
                }
            }
            
            this.counter = counter + 1;
            if (this.queue.isEmpty)
            {
                var info = " INFO: Records processed: " + this.counter.toString;
                if (this.errors > 0){
                    info = info + "(  Failed: " + this.errors.toString + ")"
                }
                println(java.time.LocalDateTime.now + info);
                
                this.counter = 0;
                this.errors = 0;
                
                this.writer.close;
                this.writer = null;
            }

        }
    }

    def md5HashString(s: String): String = 
    {
        val md = MessageDigest.getInstance("MD5");
        val digest = md.digest(s.getBytes);
        val bigInt = new BigInteger(1,digest);
        val hashedString = bigInt.toString(16);
        hashedString
    }
    
    
    def parseMessage(message: String) = 
    {
		println(java.time.LocalDateTime.now + " INFO " + message)
		var data = ujson.read(message);
    	
		data("action").str match {
			case "create" => create(data)
			case "delete" => delete(data)
			case "list" => list(data)
			case whoa  => println(java.time.LocalDateTime.now + " ERROR Unexpected case: " + whoa)
		}	
	}
	
	def create(message: ujson.Js.Value){

        println(java.time.LocalDateTime.now + " INFO - create schema");
	
		val catalog = message("catalog").str
		val store = createGeomesaStore(catalog)

		val schemaName = message("schemaname").str
		val schemaStructure = message("schema").str
		
		val schema = SimpleFeatureTypes.createType(schemaName , schemaStructure);
		
		var keys  = List[String]()
		try 
		{
			keys = message("user-data").obj.keys.toList
		} 
		catch 
		{
			case e: NoSuchElementException => println(java.time.LocalDateTime.now + " INFO - No user-data specified !")
			case e: ujson.Js$InvalidData => println(java.time.LocalDateTime.now + " ERROR - Wrong/non-specified user-data")
		}
		
		println(java.time.LocalDateTime.now + " INFO - Keys : %s ".format(keys));
		
		for(key <- keys) {schema.getUserData().put(key,message("user-data")(key).str);}
		
		store.createSchema(schema)
		store.dispose()
	
	}
	
	def delete(message: ujson.Js.Value){

        println(java.time.LocalDateTime.now + " INFO - Delete schema");
	
		val catalog = message("catalog").str
		val store = createGeomesaStore(catalog)

		val schemaName = message("schemaname").str
		store.removeSchema(schemaName)
		store.dispose()

	}

	def list(message: ujson.Js.Value){

        println(java.time.LocalDateTime.now + " INFO - List schemas");

		val catalog = message("catalog").str
		val store = createGeomesaStore(catalog)
		val schemas = store.getTypeNames()
		println(java.time.LocalDateTime.now + " LIST SCHEMAS RESULT - " + schemas.mkString(" "))

	}
}