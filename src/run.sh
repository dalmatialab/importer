#!/bin/bash

exec scala -J-Xmx4g -classpath $(echo *.jar /jars/*.jar | tr ' ' ':') /importer/script.scala
