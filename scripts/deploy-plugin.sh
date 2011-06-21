#!/bin/bash

#Comando que ejecuta los test, empaqueta el plugin y lo sube a un repo maven

echo "[DEPLOY] Ejecutando el comando grails test-app"
test_output=`grails test-app --non-interactive`
#Busco el resultado de los tests en el output del comando
exito1=`echo "$test_output" | grep Tests.PASSED`

if [ -n "$exito1" ]; then
	#Los tests dieron exito
	echo "[DEPLOY] Tests PASSED - view reports in target/test-reports"

	echo "[DEPLOY] Ejecutando el comando grails package-plugin"
	package_output=`grails package-plugin --non-interactive`
	exito2=`echo "$package_output" | grep .zip..Building.zip`
	if [ -n "$exito2" ]; then
		#Se obtiene el nombre y la version depl plugin
		echo "[DEPLOY] Buscando el nombre y la version del plugin..."
		plugin_line=`grep plugin.name=\'.*\'.version=\'.*\'.grailsVersion plugin.xml`
		plugin_name=`echo "$plugin_line" | awk -F "'" '{print $2}'`
		version=`echo "$plugin_line" | awk -F "'" '{print $4}'`
		echo "[DEPLOY] Nombre del plugin: $plugin_name"
		echo "[DEPLOY] Version del plugin: $version"

		echo "[DEPLOY] Generando -jar file"
		mv grails-$plugin_name-$version.zip $plugin_name-$version.zip
		cp $plugin_name-$version.zip $plugin_name-$version-jar.zip
		
		echo "[DEPLOY] Subiendo plugin a maven"
		mvn1_output=`mvn deploy:deploy-file -DgroupId=org.grails.plugins -DartifactId=$plugin_name -Dversion=$version -Dpackaging=zip -Dfile=$plugin_name-$version.zip -DrepositoryId=MLGrailsPlugins -Durl=http://git.ml.com:8081/nexus/content/repositories/MLGrailsPlugins`

		exito3=`echo "$mvn1_output" | grep BUILD.SUCCESSFUL`
		if [ -n "$exito3" ]; then
			echo "[DEPLOY] BUILD SUCCESSFUL $plugin_name-$version.zip"
			mvn2_output=`mvn deploy:deploy-file -DgroupId=org.grails.plugins -DartifactId=$plugin_name -Dversion=$version -Dpackaging=zip -Dfile=$plugin_name-$version-jar.zip -Dclassifier=jar -DrepositoryId=MLGrailsPlugins -Durl=http://git.ml.com:8081/nexus/content/repositories/MLGrailsPlugins`

			exito4=`echo "$mvn2_output" | grep uploaded`
			if [ -n "$exito4" ]; then
				echo "[DEPLOY] BUILD SUCCESSFUL $plugin_name-$version-jar.zip"

				echo "[DEPLOY] Se termino la ejecucion."
			else
				echo "$mvn2_output"
				echo "[DEPLOY] BUILD ERROR $plugin_name-$version-jar.zip"
			fi
		else
			echo "$mvn1_output"
			echo "[DEPLOY] BUILD ERROR $plugin_name-$version.zip"
		fi
	else
		echo "$package_output"
		echo "[DEPLOY] Package-plugin FAILED"
	fi
	
else
	#Los tests fallaron
	echo "$test_output"
	echo "[DEPLOY] Tests FAILED - view reports in target/test-reports"
fi

