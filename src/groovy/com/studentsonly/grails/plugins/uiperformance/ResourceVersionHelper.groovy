package com.studentsonly.grails.plugins.uiperformance

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.plugin.utils.FileApplierUitl;

import org.apache.commons.lang.SystemUtils;
import java.util.zip.GZIPOutputStream

import com.yahoo.platform.yui.compressor.CssCompressor
import com.yahoo.platform.yui.compressor.JavaScriptCompressor

import org.carrot2.labs.smartsprites.SmartSpritesParameters
import org.carrot2.labs.smartsprites.SpriteBuilder
import org.carrot2.labs.smartsprites.message.MemoryMessageSink
import org.carrot2.labs.smartsprites.message.Message
import org.carrot2.labs.smartsprites.message.Message.MessageLevel
import org.carrot2.labs.smartsprites.message.MessageLog

import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH
import org.mozilla.javascript.EvaluatorException

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.FileCopyUtils

/**
 * Does the work of versioning files, creating bundles, gzipping, etc. Called from _Events.groovy.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class ResourceVersionHelper {

	private static final String SPRITE_CSS_SUFFIX = '___sprite_temp___'

	void version(stagingDir, basedir) {

		if (!(stagingDir instanceof File)) {
			stagingDir = new File(stagingDir)
		}

		if (basedir instanceof File) {
			basedir = basedir.path
		}

		try {
			String version = determineVersion(basedir)
			println "applying version $version"
			versionResources stagingDir, version, basedir
		}
		catch (EvaluatorException e) {
			throw e
		}
		catch (IOException e) {
			throw e
		}
		catch (e) {
			e.printStackTrace()
			throw e
		}
	}
	
	public String generateMD5(final File file) {
		MessageDigest digest = MessageDigest.getInstance("MD5")
		file.withInputStream(){is->
		byte[] buffer = new byte[8192]
		int read = 0
		   while( (read = is.read(buffer)) > 0) {
				  digest.update(buffer, 0, read);
			  }
		  }
		byte[] md5sum = digest.digest()
		BigInteger bigInt = new BigInteger(1, md5sum)
		return bigInt.toString(16)
	 }
	public String generateMd5String(String clave){
		byte[] password = [00];
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(clave.getBytes());
		password = md5.digest();
		BigInteger bigInt = new BigInteger(1, password)
		return bigInt.toString(16)
	}

	private String determineVersion(String basedir) {

		def determineVersionClosure = CH.config.uiperformance.determineVersion
		if (determineVersionClosure instanceof Closure) {
			println "Generating Version with configured closure in Config.groovy"
			return determineVersionClosure()
		}

		File entries = new File(basedir, '.svn/entries')
		if (entries.exists()) {
			return entries.text.split('\n')[3].trim()
		}

		try{
			println "Generating Version with acum md5 of all the files in \"web-app/images\", \"web-app/js\", \"web-app/css\""
			List md5s = FileApplierUitl.applyFunc(["web-app/images", "web-app/js", "web-app/css"],
					{File file ->
						return generateMD5(file)
					})
			String md5 = generateMd5String(md5s.join(""))
			println "md5 generated: ${md5}"
			return md5[0..Math.min(10, md5.length()-1)]
		}catch (Exception e){
			println "Generating Version with System.currentTimeInMillis() because error ${e} "
			return System.currentTimeMillis().toString()
		}
	}

	private void versionResources(File stagingDir, String version, String basedir) {

		String charset = /*System.getProperty('file.encoding') ?:*/ 'UTF-8'

		Map<String, String> spritePaths = createSprites(stagingDir)

		createPropertiesFile stagingDir, version, spritePaths

		createBundles stagingDir, charset

		def jsErrorReporter = new JsErrorReporter()
		boolean processJS = Utils.getConfigBoolean('processJS')
		boolean processCSS = Utils.getConfigBoolean('processCSS')
		print "processJS: $processJS"
		boolean processImages = Utils.getConfigBoolean('processImages')
		boolean keepOriginals = Utils.getConfigBoolean('keepOriginals', true)
		List imageExtensions = Utils.getConfigValue('imageExtensions', Utils.DEFAULT_IMAGE_EXTENSIONS)

		stagingDir.eachFileRecurse { file ->
			if (file.directory) {
				return
			}

			String relativePath = (file.path - (basedir + File.separatorChar)).replaceAll('\\\\', '/')
			if (Utils.isExcluded(relativePath)) {
				return
			}

			applyVersion file, version, charset, jsErrorReporter, imageExtensions,
				processJS, processCSS, processImages, keepOriginals
		}
	}

	private Map<String, String> createSprites(File stagingDir) {
		boolean processImages = Utils.getConfigBoolean('processImages')
		if (!processImages) {
			return [:]
		}

		Map<String, String> spritePaths = [:]

		CH.config.uiperformance.bundles.findAll {it.type == 'sprite'}.each { bundle ->
			processSprite bundle.files, bundle.name, bundle.ext, stagingDir, spritePaths
		}

		buildSprites stagingDir

		return spritePaths
	}

	private void createBundles(File stagingDir, String charset) {
		boolean processCSS = Utils.getConfigBoolean('processCSS')
		boolean processJS = Utils.getConfigBoolean('processJS')
		CH.config.uiperformance.bundles.findAll { it.type != 'sprite'}.each { bundle ->
			if (!(bundle.type == 'css' && !processCSS) && !(bundle.type == 'js' && !processJS)) {
				concatenate bundle.files, bundle.name, bundle.type, bundle.type,
					stagingDir, charset
			}
		}
	}

	/**
	 * Write out a properties file for use by taglibs, etc.
	 * @param stagingDir  the root dir
	 * @param version  the app version
	 * @param spritePaths  optional map of sprite paths
	 */
	private void createPropertiesFile(File stagingDir, String version, Map<String, String> spritePaths) {

		def props = spritePaths as Properties
		props.version = version

		props.store new FileOutputStream(new File(stagingDir, 'WEB-INF/classes/uiperformance.properties')),
			'UI Performance plugin properties'
	}

	private void applyVersion(file, String version, String charset, jsErrorReporter, List imageExtensions,
			boolean processJS, boolean processCSS, boolean processImages, boolean keepOriginals) {

		if (processCSS && file.name.toLowerCase().endsWith('.css')) {
			versionAndRewriteCss file, version, charset, processImages, keepOriginals
			return
		}

		if (processJS && file.name.toLowerCase().endsWith('.js')) {
			versionAndMinifyJs file, version, charset, jsErrorReporter, keepOriginals
			return
		}

		if (processImages) {
			for (ext in imageExtensions) {
				if (file.name.toLowerCase().endsWith(".$ext")) {
					renameWithVersion file, version, keepOriginals
				}
			}
		}
	}

	private void renameWithVersion(file, String version, boolean keepOriginals) {
		String versionedPath = addVersion(file, version)
		File newFile = new File(versionedPath)
		if (keepOriginals) {
			FileCopyUtils.copy file, newFile
		}
		else {
			if (!file.renameTo(newFile)) {
				throw new RuntimeException("unable to rename $file.path to $versionedPath")
			}
		}
	}

	private void versionAndRewriteCss(file, String version, String charset,
			boolean processImages, boolean keepOriginals) {

		Date originalDate = new Date(file.lastModified())
		def css = new StringBuilder()
		file.eachLine { line ->
			
			int index = line.indexOf('url(')
			boolean isData = index > -1 && line =~ /url\s*\(\s*['|"]?\s*data:/

			
			String cssuri
			if(index >0 ){
				cssuri = line.substring(index + 4, line.indexOf(')', index))
			}
			if (!processImages || index == -1 || cssuri?.contains('http') || cssuri?.contains('secure.mlstatic.com') || isData) {
				css.append line
			}
			else {
				int index2 = line.indexOf(')', index)
				String url = line.substring(index + 4, index2)
				if (Utils.isExcluded(url)) {
					css.append line
				}
				else {
					String baseUri = ""
					
					if(CH.config.uiperformance.staticBaseUrlGenerator){
						baseUri += CH.config.uiperformance.staticBaseUrlGenerator(null)
					}
					String before = line.substring(0, index + 4) + baseUri.replaceAll("//", "/").replaceAll("\"","")
					css.append before					
					StringBuilder partial = new StringBuilder()
					addVersion partial, url, version
					css.append partial.toString().replaceAll("'","").replaceAll("\"","")
					css.append line.substring(index2)
				}
			}
			css.append '\n'
		}

		writeMinifiedCss css.toString(), addVersion(file, version), charset,
			originalDate, file, keepOriginals
	}
	private boolean isExcluded(String text, List patterns){
		for (String pattern : patterns) {
			if(text ==~ pattern){
				return true
			}
		}
		return false
	}
	private void versionAndMinifyJs(file, version, charset, jsErrorReporter, boolean keepOriginals) {

		Date originalDate = new Date(file.lastModified())

		Writer out
		String versionedName = addVersion(file, version)

		boolean minifyJs = Utils.getConfigBoolean('minifyJs')
		List excludes = Utils.getConfigValue('excludedMinifiedJs', []) 
		boolean excludeThis = excludes && isExcluded(file.getName(), excludes)
		if (minifyJs && !excludeThis) {
			boolean minifyJsAsErrorCheck = Utils.getConfigBoolean('minifyJsAsErrorCheck', false)
			try {
				Reader jsIn
				def compressor
				try {
					jsIn = new InputStreamReader(new FileInputStream(file), charset)
					compressor = new JavaScriptCompressor(jsIn, jsErrorReporter)
				}catch(Exception e){
					e.printStackTrace()
				}finally {
					close jsIn
				}

				if (minifyJsAsErrorCheck) {
					out = new StringWriter()
				}
				else {
					out = new OutputStreamWriter(new FileOutputStream(versionedName), charset)
				}

				// TODO  lookup in config
				boolean munge = true
				boolean preserveAllSemiColons = false
				boolean disableOptimizations = false
				boolean verbose = false

				compressor.compress out, -1, munge, verbose, preserveAllSemiColons, disableOptimizations

				if (minifyJsAsErrorCheck) {
					writeUnminifiedJs file, version, originalDate
				}
			}
			catch (e) {
				error "problem minifying $file: $e.message"
				boolean continueJs = Utils.getConfigBoolean('continueAfterMinifyJsError', false)
				if (minifyJsAsErrorCheck || continueJs) {
					writeUnminifiedJs file, version, originalDate
					if (!file.delete()) {
						error "unable to delete $file.path"
					}
				}
				else {
					throw e
				}
			}
			finally {
				close out
			}
		}
		else {
			writeUnminifiedJs file, version, originalDate
		}

		gzip versionedName, originalDate

		if (!keepOriginals && !file.delete()) {
			error "unable to delete $file.path"
		}
	}

	private void writeUnminifiedJs(file, version, originalDate) {
		String versionedName = addVersion(file, version)
		FileCopyUtils.copy file, new File(versionedName)
		gzip versionedName, originalDate
	}

	private void writeMinifiedCss(String css, String outputFilename, String charset,
			Date originalDate, file, boolean keepOriginals) {

		boolean minifyCss = Utils.getConfigBoolean('minifyCss')
		if (minifyCss) {

			boolean minifyCssAsErrorCheck = Utils.getConfigBoolean('minifyCssAsErrorCheck', false)
			Writer out
			try {
				def compressor = new CssCompressor(new StringReader(css))

				if (minifyCssAsErrorCheck) {
					out = new StringWriter()
				}
				else {
					out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset)
				}

				compressor.compress out, -1

				if (minifyCssAsErrorCheck) {
					writeUnminifiedCss css, outputFilename, originalDate, file
				}
			}
			catch (e) {
				error "problem minifying $file: $e.message"
				boolean continueCss = Utils.getConfigBoolean('continueAfterMinifyCssError', false)
				if (minifyCssAsErrorCheck || continueCss) {
					writeUnminifiedCss css, outputFilename, originalDate, file
					if (!file.delete()) {
						error "unable to delete $file.path"
					}
				}
				else {
					throw e
				}
			}
			finally {
				if (out) {
					close(out)
				}
			}
		}
		else {
			writeUnminifiedCss css, outputFilename, originalDate, file
		}

		gzip outputFilename, originalDate

		if (!keepOriginals && !file.delete()) {
			error "unable to delete $file.path"
		}
	}

	private void writeUnminifiedCss(String css, String outputFilename, Date originalDate, file) {
		FileCopyUtils.copy new StringReader(css), new FileWriter(outputFilename)
		gzip outputFilename, originalDate
	}

	private String addVersion(file, String version) {
		def sb = new StringBuilder()
		addVersion sb, file.path, version
		return sb.toString()
	}

	private void addVersion(StringBuilder sb, String name, String version) {
		int index = name.lastIndexOf('.')
		sb.append name.substring(0, index)
		sb.append '__v'
		sb.append version
		sb.append name.substring(index)
	}

	private void gzip(filename, originalDate) {
		File gzipped = createGzipFile(filename)

		FileCopyUtils.copy(
				new BufferedInputStream(new FileInputStream(filename)),
				new GZIPOutputStream(new FileOutputStream(gzipped)))

		// currently Grails copies to staging w/out preserving last modified, so this isn't helpful yet ...
		gzipped.lastModified = originalDate.time
	}

	private File createGzipFile(filename) {
		int index = filename.lastIndexOf('.')
		String ext = filename.substring(index + 1)
		filename = (filename - ".$ext") + ".gz.$ext"
		return new File(filename)
	}

	private void concatenate(List files, String name, String subdir, String ext,
			File stagingDir, String charset) {
		new File(stagingDir, "$subdir/${name}.$ext").withWriter charset, { writer ->
			files.each { file ->
				writer.write new File(stagingDir, "$subdir/${file}.$ext").getText(charset)
				writer.write '\n'
			}
		}
	}

	private void processSprite(List fileNames, String name, String ext,
			File stagingDir, Map<String, String> spritePaths) {

		println "\ncreating sprite file ${name}-sprite.css"
		Set files = expandAndResolveFileList(fileNames, new File(stagingDir, 'images'))
		def spriteCss = new StringBuilder()
		spriteCss.append "/** sprite: ${name}-sprite; sprite-image: url(../images/${name}-sprite.${ext}); */\n"
		for (file in files) {
			if (file.directory) {
				continue
			}
			def dimensions = Utils.calculateImageDimension(file)
			if (!dimensions) {
				// ???
			}
			String fileName = file.name.substring(0, file.name.lastIndexOf('.'))
			String relativePath = getRelativePath(file, stagingDir)
			spriteCss.append ".${fileName}_sprite {\n"
			spriteCss.append "	width: ${(int)dimensions.width}px;\n"
			spriteCss.append "	height: ${(int)dimensions.height}px;\n"
			spriteCss.append "	background-color: transparent;\n"
			spriteCss.append "	background-image: url(../$relativePath);  /** sprite-ref: ${name}-sprite; */\n"
			spriteCss.append "}\n\n"
		}

		new File(stagingDir, "css/${name}.css") << spriteCss.toString()

		spritePaths["sprite-$name"] = buildSpritePaths(files, stagingDir, name)
	}

	/**
	 * Expands names/patterns into Files.
	 * @param fileNames  names and/or patterns
	 * @param baseDir  the root dir to search in
	 * @return  matching files
	 */
	private Set<File> expandAndResolveFileList(List fileNames, File baseDir) {
		def pathResolver = new PathMatchingResourcePatternResolver()
		def resources = new HashSet<FileSystemResource>()
		for (fileName in fileNames) {
			resources.addAll pathResolver.doFindMatchingFileSystemResources(baseDir, fileName)
		}
		return resources*.file
	}

	private String buildSpritePaths(Collection files, File stagingDir, String name) {
		def paths = new StringBuilder()
		for (file in files) {
			paths.append getRelativePath(file, stagingDir)
			paths.append ','
		}
		return paths.toString()
	}

	/**
	 * return a path string of the given file relative to the provided
	 * parent directory
	 */
	private String getRelativePath(File file, File dir) {
		return (file.canonicalPath - dir.canonicalPath).substring(1).replace(File.separator, '/')
	}

	private void buildSprites(File rootDir) {
		def parameters = new SmartSpritesParameters((new File(rootDir, 'css')).getPath())
		parameters.cssFileSuffix = SPRITE_CSS_SUFFIX
		def messageSink = new MemoryMessageSink()
		def messageLog = new MessageLog(messageSink)

		new SpriteBuilder(parameters, messageLog).buildSprites()

		if (true) {
			def comparator = Message.MessageLevel.COMPARATOR
			def minLevel = Message.MessageLevel.IE6NOTICE
			StringBuilder spriteMessages = new StringBuilder()
			for (message in messageSink.messages) {
				if (comparator.compare(message.level, minLevel) >= 0) {
					spriteMessages.append '\t'
					spriteMessages.append message.toString()
					spriteMessages.append '\n'
				}
			}
			println "\nSmartSprite log:\n$spriteMessages"
		}
		
		// by default files get "-sprite" added to the name, but that requires knowing
		// whether you're using the sprite version or not, which complicates dev/prod mode.
		// so write the files out to a temp dir with no suffix, and overwrite the originals.
		// unfortunately SmartSprites can't rewrite in-place.
		renameSpriteCss rootDir
	}

	private void renameSpriteCss(File currentDir) {
		for (file in currentDir.listFiles()) {
			if (file.directory) {
				renameSpriteCss file
			}
			else if (file.name.contains(SPRITE_CSS_SUFFIX)) {
				FileCopyUtils.copy file, new File(file.path - SPRITE_CSS_SUFFIX)
				file.delete()
			}
		}
	}

	private void close(Closeable closeable) {
		try {
			if (closeable) {
				closeable.close()
			}
		}
		catch (e) {
			// ignored
		}
	}

	private void error(String message) {
		println "\nERROR: $message\n"
	}
}
