package com.studentsonly.grails.plugins.uiperformance.taglib


import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH
/**
 * Abstract base class for taglibs.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
abstract class AbstractTaglib {

	/**
	 * Generates html for attributes not explicitly handled.
	 * <p/>
	 * Remove all handled attributes before calling this, e.g.
	 * <code>attrs.remove 'border'</code>
	 *
	 * @param attrs  the attribute map
	 * @return  html for extra attributes
	 */
	protected String generateExtraAttributes(attrs) {

		String extra = ''

		attrs.each { key, value ->
			extra += " $key=\"$value\""
		}

		return extra
	}

	protected String generateRelativePath(dir, name, extension, plugin, absolute,def pluginContext = false) {		
		if ('true' == absolute) {
			return name
		}

		String baseUri = grailsAttributes.getApplicationUri(request)
		if(CH.config.uiperformance.staticBaseUrlGenerator){
			baseUri = CH.config.uiperformance.staticBaseUrlGenerator(request)
		}
		StringBuilder path = new StringBuilder(baseUri)
		if (!baseUri.endsWith('/')) {
			path.append '/'
		}
		
		String requestPluginContext = ''
		if(!pluginContext){
			requestPluginContext = plugin ? plugin : ''
		}else{
			requestPluginContext = plugin ? pluginContextPath : ''
		}
		
		if (requestPluginContext) {
			path.append (requestPluginContext.startsWith('/') ? requestPluginContext.substring(1) : requestPluginContext)
			path.append '/'
		}
		if (dir) {
			path.append dir
			path.append '/'
		}
		path.append name
		if (extension) {
			path.append extension
		}

		return path.toString().replaceAll('//', '/')
	}
}
