package org.ektorp.http.clientconfig;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.ektorp.http.HttpResponse;
import org.ektorp.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;

public class ThreadLocalCloseableHttpClientRequestExecutor extends HttpClientRequestExecutor {

	private final static Logger LOG = LoggerFactory.getLogger(ThreadLocalCloseableHttpClientRequestExecutor.class);

	private String serverHostName;

	private Integer serverHostPort;

	private HttpClientBuilder clientBuilder;

	private HttpClientBuilder backendBuilder;

	private final ThreadLocal<CloseableHttpClient> clientThreadLocal = new ThreadLocal<CloseableHttpClient>() {

		@Override
		protected CloseableHttpClient initialValue() {
			return clientBuilder.build();
		}

	};

	private final ThreadLocal<CloseableHttpClient> backendThreadLocal = new ThreadLocal<CloseableHttpClient>() {

		@Override
		protected CloseableHttpClient initialValue() {
			return backendBuilder.build();
		}

	};

	public ThreadLocalCloseableHttpClientRequestExecutor() {
		super();
	}

	@PostConstruct
	public void init() {
		Assert.notNull(serverHostName);
		Assert.notNull(serverHostPort);
	}

	public HttpResponse executeRequest(HttpUriRequest request, boolean useBackend) throws IOException {
		HttpClient client = null;
		try {
			client = locateHttpClient(useBackend);
			final org.apache.http.HttpResponse response;
			response = client.execute(getHttpHost(client), request);
			return createHttpResponse(response, request);
		} finally {
			releaseHttpClient(client);
		}
	}

	@Override
	public HttpClient locateHttpClient(boolean useBackend) {
		if (useBackend) {
			return backendThreadLocal.get();
		} else {
			return clientThreadLocal.get();
		}
	}

	@Override
	public void releaseHttpClient(HttpClient client) throws IOException {
		// nothing to do
	}

	@Override
	public void shutdown() {
		// FIXME : initial value would create a new instance if not exists already, which would be done for nothing : TODO : remove initialValue() in ThreadLocal 
		IOUtils.closeQuietly(clientThreadLocal.get());
		IOUtils.closeQuietly(backendThreadLocal.get());
	}

	@Override
	public HttpHost getHttpHost(HttpClient client) {
		return new HttpHost(serverHostName, serverHostPort);
	}

	protected void closeClient(CloseableHttpClient c) {
		try {
			c.close();
		} catch (IOException e) {
			LOG.error("IOException while closing CloseableHttpClient");
		}
	}

	public void setClientBuilder(HttpClientBuilder clientBuilder) {
		this.clientBuilder = clientBuilder;
	}

	public void setBackendBuilder(HttpClientBuilder backendBuilder) {
		this.backendBuilder = backendBuilder;
	}

	public void setServerHostName(String serverHostName) {
		this.serverHostName = serverHostName;
	}

	public void setServerHostPort(Integer serverHostPort) {
		this.serverHostPort = serverHostPort;
	}
}
