package com.plugin.etagFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Date;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.lang.text.StrBuilder;
import org.apache.log4j.Logger;
import org.codehaus.groovy.grails.commons.ConfigurationHolder;

public class EtagFilter implements Filter {
	Logger logger = Logger.getLogger(getClass());

	private Object getConfigProperty(final String name) {
		return ConfigurationHolder.getFlatConfig().get("uiperformance." + name);
	}

	private boolean getConfigBoolean(final String name) {
		Boolean value = (Boolean) getConfigProperty(name);
		return value == null ? true : value;
	}

	public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws IOException, ServletException {
		if (getConfigBoolean("etagfilter")) {
			HttpServletRequest servletRequest = (HttpServletRequest) req;
			HttpServletResponse servletResponse = (HttpServletResponse) res;

			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			HttpServletResponseWrapper wrappedResponse = new HttpServletResponseWrapper(
					servletResponse) {

				private ServletOutputStream stream = null;
				private PrintWriter writer = null;

				@Override
				public ServletOutputStream getOutputStream() throws IOException {
					if (stream == null) {
						stream = new ServletOutputStream() {
							@Override
							public void write(int b) throws IOException {
								baos.write(b);
							}
						};
					}
					return stream;
				}

				@Override
				public void flushBuffer() throws IOException {
					stream.flush();
				}

				@Override
				public PrintWriter getWriter() throws IOException {
					if (writer == null) {
						writer = new PrintWriter(new OutputStreamWriter(
								getOutputStream(), "UTF-8"));
					}
					return writer;
				}

			};
			chain.doFilter(servletRequest, wrappedResponse);
			StrBuilder str = new StrBuilder("");
			for (Cookie cookie : servletRequest.getCookies()) {
				str.append(cookie.getName()).append("=")
						.append(cookie.getValue()).append("\n");
			}
			String varyHeaders = servletRequest.getHeader("Accept") != null ? servletRequest
					.getHeader("Accept") : "" + str.toString();
			byte[] originalBytes = baos.toByteArray();
			byte[] b2 = varyHeaders.getBytes();

			byte[] bytes = new byte[originalBytes.length + b2.length];
			int i = 0;
			for (byte auxByte : originalBytes) {
				bytes[i++] = auxByte;
			}
			for (byte auxByte : b2) {
				bytes[i++] = auxByte;
			}

			String token = '"' + ETagComputeUtils.getMd5Digest(bytes) + '"';
			servletResponse.setHeader("ETag", token); // always store the ETag
														// in
														// the header

			String previousToken = servletRequest.getHeader("If-None-Match");
			if (previousToken != null && previousToken.equals(token)) { // compare
																		// previous
																		// token
																		// with
																		// current
																		// one
				logger.debug("ETag match: returning 304 Not Modified");
				servletResponse.sendError(HttpServletResponse.SC_NOT_MODIFIED);
				// use the same date we sent when we created the ETag the first
				// time
				// through
				servletResponse.setHeader("Last-Modified",
						servletRequest.getHeader("If-Modified-Since"));
			} else { // first time through - set last modified time to now
				Calendar cal = Calendar.getInstance();
				cal.set(Calendar.MILLISECOND, 0);
				Date lastModified = cal.getTime();
				servletResponse.setDateHeader("Last-Modified",
						lastModified.getTime());

				logger.debug("Writing body content");
				servletResponse.setContentLength(originalBytes.length);
				ServletOutputStream sos = servletResponse.getOutputStream();
				sos.write(originalBytes);
				sos.flush();
				sos.close();
			}
		} else {
			chain.doFilter(req, res);
		}
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

	@Override
	public void init(FilterConfig arg0) throws ServletException {
		// TODO Auto-generated method stub

	}
}
