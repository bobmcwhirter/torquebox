/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.torquebox.ruby.enterprise.web.rack;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.kernel.Kernel;
import org.jboss.kernel.spi.registry.KernelRegistryEntry;
import org.jboss.logging.Logger;
import org.torquebox.ruby.enterprise.web.rack.spi.RackApplication;
import org.torquebox.ruby.enterprise.web.rack.spi.RackApplicationPool;

public class RackFilter implements Filter {

	private static final Logger log = Logger.getLogger(RackFilter.class);

	private static final String KERNEL_NAME = "jboss.kernel:service=Kernel";

	public static final String RACK_APP_POOL_INIT_PARAM = "jboss.rack.app.pool.name";

	private RackApplicationPool rackAppPool;

	private ServletContext servletContext;

	@SuppressWarnings("deprecation")
	public void init(FilterConfig filterConfig) throws ServletException {
		Kernel kernel = (Kernel) filterConfig.getServletContext().getAttribute(KERNEL_NAME);
		String rackAppPoolName = filterConfig.getInitParameter(RACK_APP_POOL_INIT_PARAM);
		log.info( "fetching pool [" + rackAppPoolName + "]" );
		KernelRegistryEntry entry = kernel.getRegistry().findEntry(rackAppPoolName);
		if (entry != null) {
			this.rackAppPool = (RackApplicationPool) entry.getTarget();
		} else {
			throw new ServletException( "Unable to obtain Rack application pool '" + rackAppPoolName + "'" );
		}
		
		log.info( "pool is " + rackAppPool );
		
		this.servletContext = filterConfig.getServletContext();
	}

	public void destroy() {
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
			doFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
		}
	}

	protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
		HttpServletResponseCapture responseCapture = new HttpServletResponseCapture(response);
		try {
			chain.doFilter(request, responseCapture);
			if ( responseCapture.isError() ) {
				response.reset();
			} else {
				return;
			}
		} catch (ServletException e) {
			log.error( e );
		}
		doRack( request, response );
	}

	protected void doRack(HttpServletRequest request, HttpServletResponse response) throws IOException {
		RackApplication rackApp = null;

		try {
			rackApp = borrowRackApplication();
			Object rackEnv = rackApp.createEnvironment(servletContext, request);
			rackApp.call(rackEnv).respond(response);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (rackApp != null) {
				releaseRackApplication(rackApp);
				rackApp = null;
			}
		}
	}

	private RackApplication borrowRackApplication() throws Exception {
		return this.rackAppPool.borrowApplication();
	}

	private void releaseRackApplication(RackApplication rackApp) {
		this.rackAppPool.releaseApplication(rackApp);
	}

}
