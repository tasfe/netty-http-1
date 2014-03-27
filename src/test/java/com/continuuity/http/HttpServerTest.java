/**
 * Copyright 2012-2014 Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.continuuity.http;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.Service;
import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Test the HttpServer
 */
public class HttpServerTest {

  private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
  static int port;
  static NettyHttpService service;

  @BeforeClass
  public static void setup() throws Exception {

    List<HttpHandler> handlers = Lists.newArrayList();
    handlers.add(new TestHandler());

    NettyHttpService.Builder builder = NettyHttpService.builder();
    builder.addHttpHandlers(handlers);

    service = builder.build();
    service.startAndWait();
    Service.State state = service.state();
    assertEquals(Service.State.RUNNING, state);
    port = service.getBindAddress().getPort();
  }

  @AfterClass
  public static void teardown() throws Exception {
    service.shutDown();
  }

  @Test
  public void testValidEndPoints() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/resource?num=10", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());

    String content = getResponseContent(response);
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(content, STRING_MAP_TYPE);
    assertEquals(1, map.size());
    assertEquals("Handled get in resource end-point", map.get("status"));

    endPoint = String.format("http://localhost:%d/test/v1/tweets/1", port);
    get = new HttpGet(endPoint);
    response = request(get);

    assertEquals(200, response.getStatusLine().getStatusCode());
    content = getResponseContent(response);
    map = gson.fromJson(content, STRING_MAP_TYPE);
    assertEquals(1, map.size());
    assertEquals("Handled get in tweets end-point, id: 1", map.get("status"));
  }

  @Test
  public void testPathWithMultipleMethods() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/tweets/1", port);
    HttpPut put = new HttpPut(endPoint);
    put.setEntity(new StringEntity("data"));
    HttpResponse response = request(put);
    assertEquals(200, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testPathWithPost() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/tweets/1", port);
    HttpPut put = new HttpPut(endPoint);
    put.setEntity(new StringEntity("data"));
    HttpResponse response = request(put);
    assertEquals(200, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testNonExistingEndPoints() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/users", port);
    HttpPost post = new HttpPost(endPoint);
    post.setEntity(new StringEntity("data"));
    HttpResponse response = request(post);
    assertEquals(404, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testPutWithData() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/facebook/1/message", port);
    HttpPut put = new HttpPut(endPoint);
    put.setEntity(new StringEntity("Hello, World"));
    HttpResponse response = request(put);
    assertEquals(200, response.getStatusLine().getStatusCode());
    String content = getResponseContent(response);
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(content, STRING_MAP_TYPE);
    assertEquals(1, map.size());
    assertEquals("Handled put in tweets end-point, id: 1. Content: Hello, World", map.get("result"));
  }

  @Test
  public void testPostWithData() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/facebook/1/message", port);
    HttpPost post = new HttpPost(endPoint);
    post.setEntity(new StringEntity("Hello, World"));
    HttpResponse response = request(post);
    assertEquals(200, response.getStatusLine().getStatusCode());
    String content = getResponseContent(response);
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(content, STRING_MAP_TYPE);
    assertEquals(1, map.size());
    assertEquals("Handled post in tweets end-point, id: 1. Content: Hello, World", map.get("result"));
  }

  @Test
  public void testNonExistingMethods() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/facebook/1/message", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(405, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testKeepAlive() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/tweets/1", port);
    HttpPut put = new HttpPut(endPoint);
    put.setEntity(new StringEntity("data"));
    HttpResponse response = request(put, true);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("keep-alive", response.getFirstHeader("Connection").getValue());
  }

  @Test
  public void testMultiplePathParameters() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/user/sree/message/12", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    String content = getResponseContent(response);
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(content, STRING_MAP_TYPE);
    assertEquals(1, map.size());
    assertEquals("Handled multiple path parameters sree 12", map.get("result"));
  }


  //Test the end point where the parameter in path and order of declaration in method signature are different
  @Test
  public void testMultiplePathParametersWithParamterInDifferentOrder() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/message/21/user/sree", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    String content = getResponseContent(response);
    Gson gson = new Gson();
    Map<String, String> map = gson.fromJson(content, STRING_MAP_TYPE);
    assertEquals(1, map.size());
    assertEquals("Handled multiple path parameters sree 21", map.get("result"));
  }

  @Test
  public void testNotRoutablePathParamMismatch() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/NotRoutable/sree", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(500, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testNotRoutableMissingPathParam() throws IOException {
    String endPoint = String.format("http://localhost:%d/test/v1/NotRoutable/sree/message/12", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(500, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testMultiMatchFoo() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/foo", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-get-actual-foo", getResponseContent(response));
  }

  @Test
  public void testMultiMatchAll() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/foo/baz/id", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-*", getResponseContent(response));
  }

  @Test
  public void testMultiMatchParam() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/bar", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-param-bar", getResponseContent(response));
  }

  @Test
  public void testMultiMatchParamBar() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/id/bar", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-param-bar-id", getResponseContent(response));
  }

  @Test
  public void testMultiMatchFooPut() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/foo", port);
    HttpPut put = new HttpPut(endPoint);
    HttpResponse response = request(put);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-put-actual-foo", getResponseContent(response));
  }

  @Test
  public void testMultiMatchParamPut() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/bar", port);
    HttpPut put = new HttpPut(endPoint);
    HttpResponse response = request(put);
    assertEquals(405, response.getStatusLine().getStatusCode());
  }

  @Test
  public void testMultiMatchFooParamBar() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/foo/id/bar", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-foo-param-bar-id", getResponseContent(response));
  }

  @Test
  public void testMultiMatchFooBarParam() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/foo/bar/id", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-foo-bar-param-id", getResponseContent(response));
  }

  @Test
  public void testMultiMatchFooBarParamId() throws Exception {
    String endPoint = String.format("http://localhost:%d/test/v1/multi-match/foo/bar/bar/bar", port);
    HttpGet get = new HttpGet(endPoint);
    HttpResponse response = request(get);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("multi-match-foo-bar-param-bar-id-bar", getResponseContent(response));
  }

  private HttpResponse request(HttpUriRequest uri) throws IOException {
    return request(uri, false);
  }

  private HttpResponse request(HttpUriRequest uri, boolean keepalive) throws IOException {
    DefaultHttpClient client = new DefaultHttpClient();

    if (keepalive) {
      client.setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy());
    }
    return client.execute(uri);
  }

  private String getResponseContent(HttpResponse response) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ByteStreams.copy(response.getEntity().getContent(), bos);
    String result = bos.toString("UTF-8");
    bos.close();
    return result;
  }
}
