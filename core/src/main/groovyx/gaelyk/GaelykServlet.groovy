/*
 * Copyright 2009-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovyx.gaelyk

import java.net.URLConnection;

import groovy.servlet.AbstractHttpServlet;
import groovy.servlet.GroovyServlet
import groovy.servlet.ServletBinding
import groovy.util.ResourceException;

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import javax.servlet.ServletConfig

import groovyx.gaelyk.plugins.PluginResourceSupport;
import groovyx.gaelyk.plugins.PluginsHandler
import groovyx.gaelyk.logging.GroovyLogger
import org.codehaus.groovy.runtime.InvokerHelper
import java.util.logging.Logger

/**
 * The Gaelyk servlet extends Groovy's own Groovy servlet
 * to inject Google App Engine dedicated services in the binding of the Groolets.
 * 
 * @author Marcel Overdijk
 * @author Guillaume Laforge
 *
 * @see groovy.servlet.GroovyServlet
 */
class GaelykServlet extends GroovyServlet {
    private static final Logger log = Logger.getLogger(GroovyServlet.class.getName());

    private GroovyScriptEngine gse

    @Override
    void init(ServletConfig config) {
        super.init(config)
        File groovy = new File(config.getServletContext().getRealPath('/WEB-INF/groovy'))
        preloadDirectory(groovy)
    }
    protected GroovyScriptEngine createGroovyScriptEngine(){
        gse = new GroovyScriptEngine(this)
        gse
    }

    private void preloadDirectory(File dir) {
        Closure preload
        preload = { File file ->
            file.eachDir(preload)
            file.eachFile {
                if (it.getName().endsWith(".groovy")) {
                    String script = it.getAbsolutePath()
                    script = script.substring(script.indexOf('/WEB-INF/groovy')+'/WEB-INF/groovy'.length())
                    log.info(script)
                    gse.loadScriptByName(script)
                }
            }
        }
        preload(dir)
    }

    /**
     * Injects the default variables and GAE services in the binding of Groovlets
     * as well as the variables contributed by plugins, and a logger.
     *  
     * @param binding the binding to enhance
     */
    @Override
    protected void setVariables(ServletBinding binding) {
        GaelykBindingEnhancer.bind(binding)
        PluginsHandler.instance.enrich(binding)
        binding.setVariable("log", GroovyLogger.forGroovletUri(super.getScriptUri(binding.request)))
    }

    /**
     * Service incoming requests applying the <code>GaelykCategory</code>
     * and the other categories defined by the installed plugins.
     *
     * @param request the request
     * @param response the response
     * @throws IOException when anything goes wrong
     */
    @Override
    void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        use([GaelykCategory, * PluginsHandler.instance.categories]) {
            PluginsHandler.instance.executeBeforeActions(request, response)
            log.info getScriptUri(request)
            super.service(request, response)
            PluginsHandler.instance.executeAfterActions(request, response)
        }
    }
	
	/**
	 * This methods adds plugin awareness to the default {@link AbstractHttpServlet#getResourceConnection(String)} method.
	 * @param name resource to be found
	 */
	@Override
	URLConnection getResourceConnection(String name)
			throws ResourceException {
		try {
			return super.getResourceConnection(name)
		} catch (ResourceException re){
			return PluginResourceSupport.getResourceConnection("groovy",name)
		}
	}
}