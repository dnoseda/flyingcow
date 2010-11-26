import org.codehaus.groovy.grails.commons.GrailsApplication

import grails.util.GrailsUtil

import com.plugin.etagFilter.EtagFilter;
import com.studentsonly.grails.plugins.uiperformance.CacheFilter

import com.studentsonly.grails.plugins.uiperformance.postprocess.CssTagPostProcessor
import com.studentsonly.grails.plugins.uiperformance.postprocess.ImageTagPostProcessor
import com.studentsonly.grails.plugins.uiperformance.postprocess.JsTagPostProcessor

class UiPerformanceGrailsPlugin {

	String version = '1.2.5'
	String grailsVersion = '1.0 > *'
	Map dependsOn = [:]
	List pluginExcludes = [
		'lib/build/*.jar',
		'lib/easymock.jar',
		'src/groovy/com/studentsonly/grails/plugins/uiperformance/taglib/AbstractTaglibTest.groovy'
	]

	String author = 'Burt Beckwith'
	String authorEmail = 'burt@burtbeckwith.com'
	String title = 'Grails UI Performance Plugin'
	String description = "Taglibs and Filter to implement some of the Yahoo performance team's 14 rules"
	String documentation = 'http://grails.org/plugin/ui-performance'

	private static final String COMPRESSING_FILTER_CLASS =
		'com.planetj.servlet.filter.compression.CompressingFilter'

	def doWithSpring = {
		// register the three post-processors
		imageTagPostProcessor(ImageTagPostProcessor)
		cssTagPostProcessor(CssTagPostProcessor)
		jsTagPostProcessor(JsTagPostProcessor)
	}

	def doWithWebDescriptor = { xml ->

		if (!isEnabled(application)) {
			return
		}

		def contextParam = xml.'context-param'
		contextParam[contextParam.size() - 1] + {
			'filter' {
				'filter-name'('cacheFilter')
				'filter-class'(CacheFilter.name)
			}
		}
		if(isEtagFilterEnabled(application)){
			contextParam[contextParam.size() - 1] + {
				'filter' {
					'filter-name'('etagFilter')
					'filter-class'(EtagFilter.name)
				}
			}
		}

		def htmlConfig = application.config.uiperformance.html

		if (htmlConfig.compress) {

			if (!htmlConfig.containsKey('includeContentTypes') &&
					!htmlConfig.containsKey('excludeContentTypes')) {
				// set default to text types only if there's no config set
				htmlConfig.includeContentTypes = ['text/html', 'text/xml', 'text/plain']
			}

			contextParam[contextParam.size() - 1] + {
				'filter' {
					'filter-name'(COMPRESSING_FILTER_CLASS)
					'filter-class'(COMPRESSING_FILTER_CLASS)

					['debug', 'statsEnabled'].each { name ->
						def value = htmlConfig[name]
						if (value) {
							'init-param' {
								'param-name'(name)
								'param-value'('true')
							}
						}
					}

					['includePathPatterns', 'excludePathPatterns',
					 'includeContentTypes', 'excludeContentTypes',
					 'includeUserAgentPatterns', 'excludeUserAgentPatterns'].each { name ->
						def value = htmlConfig[name]
						if (value) {
							'init-param' {
								'param-name'(name)
								'param-value'(value.join(','))
							}
						}
					}

					['compressionThreshold', 'javaUtilLogger', 'jakartaCommonsLogger'].each { name ->
						def value = htmlConfig[name]
						if (value) {
							'init-param' {
								'param-name'(name)
								'param-value'(value.toString())
							}
						}
					}
				}
			}
		}
		
		if(isHtmlMinifizerEnabled(application)){
			def htmlMiniConfig = application.config.uiperformance.htmlminifizer
			println htmlMiniConfig
			contextParam[contextParam.size() - 1] + {
				'filter' {
					'filer-name' ("html-minimizer")
					'filter-class' ("com.mercadolibre.web.minimizer.HtmlMinimizerFilter")
					[[configName:"inclusionPatterns",name:"inclusion-patterns"],
						[configName:"exclusionPatterns",name:"exclusion-patterns"]].each {
						def paramName = it.name
						def paramValue = htmlMiniConfig[it.configName]?.join(";") ?: ""
						
						'init-param' {
							'param-name'(paramName)
							'param-value'(paramValue)
						}
					}
				}
			}
		}

		def filter = xml.'filter'
		filter[filter.size() - 1] + {
			'filter-mapping' {
				'filter-name'('cacheFilter')
				'url-pattern'('/*')
			}
		}

		if (htmlConfig.compress) {
			filter[filter.size() - 1] + {
				if (!htmlConfig.urlPatterns) {
					htmlConfig.urlPatterns = ['/*']
				}

				for (pattern in htmlConfig.urlPatterns) {
					'filter-mapping' {
						'filter-name'(COMPRESSING_FILTER_CLASS)
						'url-pattern'(pattern)
					}
				}
			}
		}
		
		if(isEtagFilterEnabled(application)){
			filter[filter.size() - 1] + {
				'filter-mapping' {
					'filter-name'('etagFilter')
					'url-pattern'('/*')
				}
			}
		}		
		/**
		 * 
		 */
		
		if(isHtmlMinifizerEnabled(application)){
			int pos = -1
			for(int i = 0; i< filter.size() ; i++){
				println filter[i]["filter-name"]
				if(filter[i]["filter-name"] == "sitemesh"){
					pos = i
				}
			}
			filter[pos-1] + {
				'filter-mapping' {
					'filter-name'('html-minimizer')
					'url-pattern'('/*')
				}
			}
			println "filters mappings:"
			for(int i = 0; i< filter.size() ; i++){
				println filter[i]["filter-name"]
			}
		}
	}

	def doWithDynamicMethods = { ctx ->
		// nothing to do
	}

	def onChange = { event ->
		// nothing to do
	}

	def onConfigChange = { event ->
		// nothing to do
	}

	def doWithApplicationContext = { applicationContext ->
		// nothing to do
	}

	private boolean isEnabled(application) {
		def enabled = application.config.uiperformance.enabled
		return enabled instanceof Boolean ? enabled : true
	}
	private boolean isEtagFilterEnabled(application) {
		def enabled = application.config.uiperformance.etagfilter
		return enabled instanceof Boolean ? enabled : false
	}
	private boolean isHtmlMinifizerEnabled(application) {
		def enabled = application.config.uiperformance.htmlMinifizer
		println "minifizer enabled $enabled"
		return enabled instanceof Boolean ? enabled : false
	}
}
