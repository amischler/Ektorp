package org.ektorp.http;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.ektorp.util.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author henriklundgren
 */
public class StdHttpResponse implements HttpResponse {

	private final static Logger LOG = LoggerFactory.getLogger(StdHttpResponse.class);

	private final static HttpEntity NULL_ENTITY = new NullEntity();

	private final HttpEntity entity;
	private final StatusLine status;
	private final String requestURI;
	private final HttpUriRequest httpRequest;
	private final String revision;
	
	private boolean closed = false;

	public static StdHttpResponse of(org.apache.http.HttpResponse rsp, HttpUriRequest httpRequest) {
		return new StdHttpResponse(rsp.getEntity(), rsp.getStatusLine(), httpRequest, rsp.getFirstHeader("ETag"));
	}

	public StdHttpResponse(HttpEntity e, StatusLine status, HttpUriRequest httpRequest, Header eTagHeader) {
		this.httpRequest = httpRequest;
		this.entity = e != null ? e : NULL_ENTITY;
		this.status = status;
		this.requestURI = httpRequest.getURI().toString();
		if (eTagHeader != null) {
			revision = eTagHeader.getValue().replace("\"", "");
		} else {
			revision = null;
		}
	}


	public int getCode() {
		return status.getStatusCode();
	}

	public String getReason() {
		return status.getReasonPhrase();
	}

	public String getRequestURI() {
		return requestURI;
	}

	public long getContentLength() {
		return entity.getContentLength();
	}

	public String getContentType() {
		return entity.getContentType().getValue();
	}

	/**
	 * TODO : add IOException to method signature 
 	 */
	public InputStream getContent() {
		try {
			return entity.getContent();
		} catch (IOException e) {
			throw Exceptions.propagate(e);
		}
	}

	public String getETag() {
		return revision;
	}

	public boolean isSuccessful() {
		return getCode() < 300;
	}

	public void releaseConnection() {
		InputStream content = null;
		try {
			content = entity.getContent();
			// this will consume the content
			IOUtils.copy(content, NullOutputStream.NULL_OUTPUT_STREAM);
		} catch (IOException e) {
			LOG.warn("IOException while getting and consuming HttpEntity's content in order to close it.", e);
		} finally {
			IOUtils.closeQuietly(content);
			closed = true;
		}
	}

	public void abort() {
		httpRequest.abort();
	}

	public String toString() {
		return status.getStatusCode() + ":" + status.getReasonPhrase();
	}
	
	@Override
	protected void finalize() {
		if (!closed) {
			LOG.warn("StdHttpResponse was not closed properly. In order to avoid leaking connections, don't forget to call releaseConnection() on every instance of StdHttpResponse");
		}
	}
	
	private class ConnectionReleasingInputStream extends FilterInputStream {

		private ConnectionReleasingInputStream(InputStream src) {
			super(src);
		}

		public void close() throws IOException {
			releaseConnection();
			IOUtils.closeQuietly(in);
		}

	}

	private static class NullEntity implements HttpEntity {

		private static final Header contentType = new BasicHeader(HTTP.CONTENT_TYPE, "null");

		private static final Header contentEncoding = new BasicHeader(HTTP.CONTENT_ENCODING, "UTF-8");

		public void consumeContent() throws IOException {

		}

		public InputStream getContent() throws IOException,
				IllegalStateException {
			return null;
		}

		public Header getContentEncoding() {
			return contentEncoding;
		}

		public long getContentLength() {
			return 0;
		}

		public Header getContentType() {
			return contentType;
		}

		public boolean isChunked() {
			return false;
		}

		public boolean isRepeatable() {
			return true;
		}

		public boolean isStreaming() {
			return false;
		}

		public void writeTo(OutputStream outstream) throws IOException {
			throw new UnsupportedOperationException("NullEntity cannot write");
		}

	}

}
