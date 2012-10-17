// Copyright 2010 Square, Inc.
package retrofit.http;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Test;
import retrofit.io.ByteSink;

import javax.inject.Provider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Executor;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Fetcher test cases.
 *
 * @author Bob Lee (bob@squareup.com)
 */
public class FetcherTest {

  private static final String URL = "http://crazybob.org/";

  private ByteSink.Factory sinkFactory = createMock(ByteSink.Factory.class);
  private StatusLine statusLine = createMock(StatusLine.class);
  private HttpEntity entity = createMock(HttpEntity.class);
  private HttpResponse response = createMock(HttpResponse.class);
  @SuppressWarnings("unchecked")
  private Callback<Void> callback = createMock(Callback.class);
  private ProgressListener progressListener
      = createMock(ProgressListener.class);

  @Test public void testSuccessfulFetch() throws Exception {
    DummyInputStream in = new DummyInputStream();
    final SingletonHttpClient httpClient = new SingletonHttpClient(response);
    DummyExecutor executor = new DummyExecutor();
    DummyMainThread uiThread = new DummyMainThread();
    ArraySink sink = new ArraySink();

    expect(response.getStatusLine()).andReturn(statusLine);
    expect(statusLine.getStatusCode()).andReturn(200);
    expect(response.getEntity()).andReturn(entity);
    expect(entity.getContentLength()).andReturn(3L);
    expect(entity.getContent()).andReturn(in);
    expect(sinkFactory.newSink()).andReturn(sink);
    progressListener.hearProgress(anyInt()); expectLastCall().atLeastOnce();
    callback.call(null);

    replayAll();

    Fetcher fetcher = new Fetcher(new Provider<HttpClient>() {
      public HttpClient get() {
        return httpClient;
      }
    }, executor, uiThread);
    fetcher.fetch(URL, sinkFactory, callback, progressListener);

    verifyAll();
    assertThat(executor.calls).isEqualTo(1);
    assertThat(uiThread.calls).isGreaterThan(1); // result + progress updates
    assertThat(httpClient.calls).isEqualTo(1);
    assertThat(sink.toArray()).isEqualTo(new byte[] { 1, 2, 3 });
  }

  private void replayAll() {
    replay(sinkFactory, statusLine, entity, response, callback,
        progressListener);
  }

  private void verifyAll() {
    verify(sinkFactory, statusLine, entity, response, callback,
        progressListener);
  }

  static class ArraySink implements ByteSink {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    boolean closed;
    public void write(byte[] buffer, int count) throws IOException {
      bout.write(buffer, 0, count);
    }
    public void close() throws IOException {
      closed = true;
    }
    public byte[] toArray() {
      return bout.toByteArray();
    }
  }

  /** Returns { 1, 2, 3 }, 1 byte at a time. */
  static class DummyInputStream extends InputStream {
    int count;
    @Override public int read(byte[] b) throws IOException {
      if (count++ == 3) return -1;
      Arrays.fill(b, 0, 1, (byte) count);
      return 1;
    }
    @Override public int read() throws IOException {
      throw new UnsupportedOperationException();
    }
  }

  static class DummyExecutor implements Executor {
    int calls;
    public void execute(Runnable command) {
      calls++;
      command.run();
    }
  }

  static class DummyMainThread extends DummyExecutor implements MainThread {
    public void executeDelayed(Runnable r, long delay) {
      throw new UnsupportedOperationException();
    }

    public void executeSynchronously(Runnable r) {
      throw new UnsupportedOperationException();
    }

    @Override public void executeOnMain(Runnable r) {
      throw new UnsupportedOperationException();
    }

    public void cancel(Runnable r) {
      throw new UnsupportedOperationException();
    }
  }

  /** An HttpClient that returns one response. */
  private class SingletonHttpClient extends DummyHttpClient {
    private final HttpResponse response;
    public SingletonHttpClient(HttpResponse response) {
      this.response = response;
    }
    int calls;
    public <T> T execute(HttpUriRequest request,
        ResponseHandler<? extends T> responseHandler) throws IOException {
      calls++;
      assertThat(request.getURI().toString()).isEqualTo(FetcherTest.URL);
      T t = responseHandler.handleResponse(response);
      assertThat(t).isNull();
      return t;
    }
  }
}
