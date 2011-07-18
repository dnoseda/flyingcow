
import grails.util.GrailsUtil

/**
 * In 1.0.x this is called after the staging dir is prepared but before the war is packaged.
 */
eventWarStart = { name ->
	if (name instanceof String || name instanceof GString) {
		versionResources name, stagingDir
	}
}

/**
 * In 1.1 this is called after the staging dir is prepared but before the war is packaged.
 */
eventCreateWarStart = { name, stagingDir ->	
	versionResources name, stagingDir
}

void versionResources(name, stagingDir) {
	
	def classLoader = Thread.currentThread().contextClassLoader
	classLoader.addURL(new File(classesDirPath).toURL())

	def config = new ConfigSlurper(GrailsUtil.environment).parse(classLoader.loadClass('Config')).uiperformance
	def enabled = config.enabled
	enabled = enabled instanceof Boolean ? enabled : true

	if (!enabled) {
		println "\nUiPerformance not enabled, not processing resources\n"
		return
	}

	println "\nUiPerformance: versioning resources ....\n"

	String className = 'com.studentsonly.grails.plugins.uiperformance.ResourceVersionHelper'
	def helper = Class.forName(className, true, classLoader).newInstance()
	helper.version stagingDir, basedir
	println "checking for older wars ${(boolean)config.olderwars}"
	if( config.olderwars){
		config.olderwars.each { war ->
			// if is file
			println "visiting $war exists? ${(new File(war)).exists()}"
			if(isURLValid(war)){
				def tempName = "/tmp/uiperf-temp-"+war.replaceAll(/(.*\/|\.war)/,"")
				if(!(new File(tempName)).exists()){
					// is url- add a curl to /temp
					execCmd("curl -vs -o $tempName $war")
				}else{
					println "file $tempName already exists; don't going to download"
				}
				execCmd("unzip -u -n $tempName js* css* images* -d $stagingDir")
			}else if((new File(war)).exists()){
				execCmd("unzip -u -n $war js* css* images* -d $stagingDir")
			}
		}
	}
}
void execCmd(String s){
	println "executing '$s'..."
	println s.execute().getText()
}

Boolean isURLValid(input) { 
	return input ==~ /\b(https?|ftp|file):\/\/[-A-Za-z0-9+&@#\/%?=~_|!:,.;]*[-A-Za-z0-9+&@#\/%=~_|]/ 
} 
