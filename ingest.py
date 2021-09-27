import pandas as pd
import paho.mqtt.client as mqtt
from shapely.geometry import Point
from tqdm.auto import tqdm
tqdm.pandas()


def insert(item, catalog, schema, client):
    info = client.publish("geomesa/" + str(catalog) + "/" + str(schema), payload=item)

def ingest(data, indices=None, catalog="catalog-name", schema="schema-name", mqtt_ip="mqtt-ip", mqtt_port=1883):
    
    if isinstance(data, str):
        print("INGESTING FILE: " + str(data))
        csv = pd.read_csv(data)
    elif isinstance(data, pd.DataFrame):
        csv = data
    else:
        print("Unknown data type: " + str(type(data)))
    
    if indices is None:
        indices = csv.columns.to_list()
    else:
        # Check if all indices exist in csv
        pass
    
    csv = csv.drop_duplicates(subset=indices)
    
    print("Creating index:")
    csv["INDEX"] = csv.progress_apply(lambda row: indices, axis=1) #add indices
    
    client = mqtt.Client()
    client.connect(mqtt_ip, mqtt_port)
    
    print("Inserting data to Geomesa:")
    csv.progress_apply(lambda row: insert(row.to_json(), catalog, schema, client), axis=1)

    print("************ FINISHED INGESTING ************")