/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nifi.processors.demo;

import nifi.processors.demo.model.LoggerModel;
import nifi.processors.demo.model.RequestBuilder;
import nifi.processors.demo.util.SoftLimitBoundedByteArrayOutputStream;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import nifi.processors.demo.properties.Descriptions;
import nifi.processors.demo.properties.Relationships;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.stream.io.StreamUtils;
import org.apache.nifi.util.Tuple;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@Tags({"example"})
@CapabilityDescription("Provide a description")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public final class MyProcessor extends AbstractProcessor {

    private final AtomicReference<OkHttpClient> okHttpClientAtomicReference = new AtomicReference<>();
    private static final Map<String, String> excludedHeaders = new HashMap<String, String>();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        excludedHeaders.put("Trusted Hostname", "HTTP request header '{}' excluded. " +
                "Update processor to use the SSLContextService instead. " +
                "See the Access Policies section in the System Administrator's Guide.");
    }

    @Override
    public Set<Relationship> getRelationships() {
        return Relationships.RELATIONSHIPS;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return Descriptions.DESCRIPTORS;
    }

    private volatile Pattern regexAttributesToSend = null;
    private volatile boolean useChunked = false;
    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        okHttpClientAtomicReference.set(null);
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();
        okHttpClientBuilder.connectTimeout((context.getProperty(Descriptions.PROP_CONNECT_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue()), TimeUnit.MILLISECONDS);
        okHttpClientBuilder.readTimeout(context.getProperty(Descriptions.PROP_READ_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue(), TimeUnit.MILLISECONDS);

        // Set whether to follow redirects
        okHttpClientBuilder.followRedirects(context.getProperty(Descriptions.PROP_FOLLOW_REDIRECTS).asBoolean());


        useChunked = context.getProperty(Descriptions.PROP_USE_CHUNKED_ENCODING).asBoolean();

        okHttpClientAtomicReference.set(okHttpClientBuilder.build());
    }

    private Request configureRequest(final ProcessContext context, final ProcessSession session, final FlowFile requestFlowFile, URL url) {
        Request.Builder requestBuilder = new Request.Builder();

        requestBuilder = requestBuilder.url(url);
        final String authUser = trimToEmpty(context.getProperty(Descriptions.PROP_BASIC_AUTH_USERNAME).getValue());

        // If the username/password properties are set then check if digest auth is being used
        if (!authUser.isEmpty() && "false".equalsIgnoreCase(context.getProperty(Descriptions.PROP_DIGEST_AUTH).getValue())) {
            final String authPass = trimToEmpty(context.getProperty(Descriptions.PROP_BASIC_AUTH_PASSWORD).getValue());

            String credential = Credentials.basic(authUser, authPass);
            requestBuilder = requestBuilder.header("UserName", authUser );
            requestBuilder = requestBuilder.header("PassWord", authPass );
        }

        // set the request method
        String method = trimToEmpty(context.getProperty(Descriptions.PROP_METHOD).evaluateAttributeExpressions(requestFlowFile).getValue()).toUpperCase();
        switch (method) {
            case "GET":
                requestBuilder = requestBuilder.get();
                break;
            case "POST":
                RequestBody requestBody = RequestBuilder.getRequestBodyToSend(session, context, requestFlowFile,useChunked);
                requestBuilder = requestBuilder.post(requestBody);
                break;
            case "PUT":
                requestBody = RequestBuilder.getRequestBodyToSend(session, context, requestFlowFile,useChunked);
                requestBuilder = requestBuilder.put(requestBody);
                break;
            case "PATCH":
                requestBody = RequestBuilder.getRequestBodyToSend(session, context, requestFlowFile,useChunked);
                requestBuilder = requestBuilder.patch(requestBody);
                break;
            case "HEAD":
                requestBuilder = requestBuilder.head();
                break;
            case "DELETE":
                requestBuilder = requestBuilder.delete();
                break;
            default:
                requestBuilder = requestBuilder.method(method, null);
        }

        requestBuilder = setHeaderProperties(context, requestBuilder, requestFlowFile);

        return requestBuilder.build();
    }


    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        OkHttpClient okHttpClient = okHttpClientAtomicReference.get();
        FlowFile requestFlowFile = session.get();

        // Checking to see if the property to put the body of the response in an attribute was set
        boolean putToAttribute = context.getProperty(Descriptions.PROP_PUT_OUTPUT_IN_ATTRIBUTE).isSet();
        if (requestFlowFile == null) {
            if(context.hasNonLoopConnection()){
                return;
            }

            String request = context.getProperty(Descriptions.PROP_METHOD).evaluateAttributeExpressions().getValue().toUpperCase();
            if ("POST".equals(request) || "PUT".equals(request) || "PATCH".equals(request)) {
                return;
            } else if (putToAttribute) {
                requestFlowFile = session.create();
            }
        }

        // Setting some initial variables
        final int maxAttributeSize = context.getProperty(Descriptions.PROP_PUT_ATTRIBUTE_MAX_LENGTH).asInteger();
        final ComponentLog logger = getLogger();

        // log ETag cache metrics
        final boolean eTagEnabled = context.getProperty(Descriptions.PROP_USE_ETAG).asBoolean();
        if(eTagEnabled && logger.isDebugEnabled()) {
            final Cache cache = okHttpClient.cache();
            logger.debug("OkHttp ETag cache metrics :: Request Count: {} | Network Count: {} | Hit Count: {}",
                    new Object[] {cache.requestCount(), cache.networkCount(), cache.hitCount()});
        }

        // Every request/response cycle has a unique transaction id which will be stored as a flowfile attribute.
        final UUID txId = UUID.randomUUID();

        FlowFile responseFlowFile = null;
        try {
            // read the url property from the context
            final String urlstr = trimToEmpty(context.getProperty(Descriptions.PROP_URL).evaluateAttributeExpressions(requestFlowFile).getValue());
            final URL url = new URL(urlstr);

            Request httpRequest = configureRequest(context, session, requestFlowFile, url);

            // log request
            LoggerModel.logRequest(logger, httpRequest);

            // emit send provenance event if successfully sent to the server
            if (httpRequest.body() != null) {
                session.getProvenanceReporter().send(requestFlowFile, url.toExternalForm(), true);
            }

            final long startNanos = System.nanoTime();

            try (Response responseHttp = okHttpClient.newCall(httpRequest).execute()) {
                // output the raw response headers (DEBUG level only)
                LoggerModel.logResponse(logger, url, responseHttp);

                // store the status code and message
                int statusCode = responseHttp.code();
                String statusMessage = responseHttp.message();

                if (statusCode == 0) {
                    throw new IllegalStateException("Status code unknown, connection hasn't been attempted.");
                }

                // Create a map of the status attributes that are always written to the request and response FlowFiles
                Map<String, String> statusAttributes = new HashMap<>();
                statusAttributes.put(STATUS_CODE, String.valueOf(statusCode));
                statusAttributes.put(STATUS_MESSAGE, statusMessage);
                statusAttributes.put(REQUEST_URL, url.toExternalForm());
                statusAttributes.put(TRANSACTION_ID, txId.toString());

                if (requestFlowFile != null) {
                    requestFlowFile = session.putAllAttributes(requestFlowFile, statusAttributes);
                }

                // If the property to add the response headers to the request flowfile is true then add them
                if (context.getProperty(Descriptions.PROP_ADD_HEADERS_TO_REQUEST).asBoolean() && requestFlowFile != null) {
                    // write the response headers as attributes
                    // this will overwrite any existing flowfile attributes
                    requestFlowFile = session.putAllAttributes(requestFlowFile, RequestBuilder.convertAttributesFromHeaders(url, responseHttp));
                }

                boolean outputBodyToRequestAttribute = (!isSuccess(statusCode) || putToAttribute) && requestFlowFile != null;
                boolean outputBodyToResponseContent = (isSuccess(statusCode) && !putToAttribute) || context.getProperty(Descriptions.PROP_OUTPUT_RESPONSE_REGARDLESS).asBoolean();
                ResponseBody responseBody = responseHttp.body();
                boolean bodyExists = responseBody != null && !context.getProperty(Descriptions.IGNORE_RESPONSE_CONTENT).asBoolean();

                InputStream responseBodyStream = null;
                SoftLimitBoundedByteArrayOutputStream outputStreamToRequestAttribute = null;
                try {
                    responseBodyStream = bodyExists ? responseBody.byteStream() : null;
                    if (responseBodyStream != null && outputBodyToRequestAttribute && outputBodyToResponseContent) {
                        outputStreamToRequestAttribute = new SoftLimitBoundedByteArrayOutputStream(maxAttributeSize);
                    }

                    if (outputBodyToResponseContent) {
                        /*
                         * If successful and putting to response flowfile, store the response body as the flowfile payload
                         * we include additional flowfile attributes including the response headers and the status codes.
                         */

                        // clone the flowfile to capture the response
                        if (requestFlowFile != null) {
                            responseFlowFile = session.create(requestFlowFile);
                        } else {
                            responseFlowFile = session.create();
                        }

                        // write attributes to response flowfile
                        responseFlowFile = session.putAllAttributes(responseFlowFile, statusAttributes);

                        // write the response headers as attributes
                        // this will overwrite any existing flowfile attributes
                        responseFlowFile = session.putAllAttributes(responseFlowFile, RequestBuilder.convertAttributesFromHeaders(url, responseHttp));

                        // transfer the message body to the payload
                        // can potentially be null in edge cases
                        if (bodyExists) {
                            // write content type attribute to response flowfile if it is available
                            if (responseBody.contentType() != null) {
                                responseFlowFile = session.putAttribute(responseFlowFile, CoreAttributes.MIME_TYPE.key(), responseBody.contentType().toString());
                            }
                            responseFlowFile = session.importFrom(responseBodyStream, responseFlowFile);


                            // emit provenance event
                            final long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                            if(requestFlowFile != null) {
                                session.getProvenanceReporter().fetch(responseFlowFile, url.toExternalForm(), millis);
                            } else {
                                session.getProvenanceReporter().receive(responseFlowFile, url.toExternalForm(), millis);
                            }
                        }
                    }

                    // if not successful and request flowfile is not null, store the response body into a flowfile attribute
                    if (outputBodyToRequestAttribute && bodyExists) {
                        String attributeKey = context.getProperty(Descriptions.PROP_PUT_OUTPUT_IN_ATTRIBUTE).evaluateAttributeExpressions(requestFlowFile).getValue();
                        if (attributeKey == null) {
                            attributeKey = RESPONSE_BODY;
                        }
                        byte[] outputBuffer;
                        int size;

                        if (outputStreamToRequestAttribute != null) {
                            outputBuffer = outputStreamToRequestAttribute.getBuffer();
                            size = outputStreamToRequestAttribute.size();
                        } else {
                            outputBuffer = new byte[maxAttributeSize];
                            size = StreamUtils.fillBuffer(responseBodyStream, outputBuffer, false);
                        }
                        String bodyString = new String(outputBuffer, 0, size, getCharsetFromMediaType(responseBody.contentType()));
                        requestFlowFile = session.putAttribute(requestFlowFile, attributeKey, bodyString);

                        final long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                        session.getProvenanceReporter().modifyAttributes(requestFlowFile, "The " + attributeKey + " has been added. The value of which is the body of a http call to "
                                + url.toExternalForm() + ". It took " + millis + "millis,");
                    }
                } finally {
                    if(outputStreamToRequestAttribute != null){
                        outputStreamToRequestAttribute.close();
                        outputStreamToRequestAttribute = null;
                    }
                    if(responseBodyStream != null){
                        responseBodyStream.close();
                        responseBodyStream = null;
                    }
                }

                route(requestFlowFile, responseFlowFile, session, context, statusCode);

            }
        } catch (final Exception e) {
            // penalize or yield
            if (requestFlowFile != null) {
                logger.error("Routing to {} due to exception: {}", new Object[]{Relationships.REL_FAILURE.getName(), e}, e);
                requestFlowFile = session.penalize(requestFlowFile);
                requestFlowFile = session.putAttribute(requestFlowFile, EXCEPTION_CLASS, e.getClass().getName());
                requestFlowFile = session.putAttribute(requestFlowFile, EXCEPTION_MESSAGE, e.getMessage());
                // transfer original to failure
                session.transfer(requestFlowFile, Relationships.REL_FAILURE);
            } else {
                logger.error("Yielding processor due to exception encountered as a source processor: {}", e);
                context.yield();
            }


            // cleanup response flowfile, if applicable
            try {
                if (responseFlowFile != null) {
                    session.remove(responseFlowFile);
                }
            } catch (final Exception e1) {
                logger.error("Could not cleanup response flowfile due to exception: {}", new Object[]{e1}, e1);
            }
        }
    }


    public final static String STATUS_CODE = "invokehttp.status.code";
    public final static String STATUS_MESSAGE = "invokehttp.status.message";
    public final static String RESPONSE_BODY = "invokehttp.response.body";
    public final static String REQUEST_URL = "invokehttp.request.url";
    public final static String TRANSACTION_ID = "invokehttp.tx.id";
    public final static String REMOTE_DN = "invokehttp.remote.dn";
    public final static String EXCEPTION_CLASS = "invokehttp.java.exception.class";
    public final static String EXCEPTION_MESSAGE = "invokehttp.java.exception.message";

    public static final Set<String> IGNORED_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            STATUS_CODE, STATUS_MESSAGE, RESPONSE_BODY, REQUEST_URL, TRANSACTION_ID, REMOTE_DN,
            EXCEPTION_CLASS, EXCEPTION_MESSAGE, "uuid", "filename", "path")));

    private volatile Set<String> dynamicPropertyNames = new HashSet<>();

    private Request.Builder setHeaderProperties(final ProcessContext context, Request.Builder requestBuilder, final FlowFile requestFlowFile) {
        // check if we should send the a Date header with the request

        final ComponentLog logger = getLogger();
        for (String headerKey : dynamicPropertyNames) {
            String headerValue = context.getProperty(headerKey).evaluateAttributeExpressions(requestFlowFile).getValue();

            // don't include any of the excluded headers, log instead
            if (excludedHeaders.containsKey(headerKey)) {
                logger.warn(excludedHeaders.get(headerKey), new Object[]{headerKey});
                continue;
            }
            requestBuilder = requestBuilder.addHeader(headerKey, headerValue);
        }

        // iterate through the flowfile attributes, adding any attribute that
        // matches the attributes-to-send pattern. if the pattern is not set
        // (it's an optional property), ignore that attribute entirely
        if (regexAttributesToSend != null && requestFlowFile != null) {
            Map<String, String> attributes = requestFlowFile.getAttributes();
            Matcher m = regexAttributesToSend.matcher("");
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String headerKey = trimToEmpty(entry.getKey());

                // don't include any of the ignored attributes
                if (IGNORED_ATTRIBUTES.contains(headerKey)) {
                    continue;
                }

                // check if our attribute key matches the pattern
                // if so, include in the request as a header
                m.reset(headerKey);
                if (m.matches()) {
                    String headerVal = trimToEmpty(entry.getValue());
                    requestBuilder = requestBuilder.addHeader(headerKey, headerVal);
                }
            }
        }
        return requestBuilder;
    }
    private boolean isSuccess(int statusCode) {
        return statusCode / 100 == 2;
    }
    private Charset getCharsetFromMediaType(MediaType contentType) {
        return contentType != null ? contentType.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
    }
    private void route(FlowFile request, FlowFile response, ProcessSession session, ProcessContext context, int statusCode){
        // check if we should yield the processor
        if (!isSuccess(statusCode) && request == null) {
            context.yield();
        }

        // If the property to output the response flowfile regardless of status code is set then transfer it
        boolean responseSent = false;
        if (context.getProperty(Descriptions.PROP_OUTPUT_RESPONSE_REGARDLESS).asBoolean()) {
            session.transfer(response, Relationships.REL_RESPONSE);
            responseSent = true;
        }

        // transfer to the correct relationship
        // 2xx -> SUCCESS
        if (isSuccess(statusCode)) {
            // we have two flowfiles to transfer
            if (request != null) {
                session.transfer(request, Relationships.REL_SUCCESS_REQ);
            }
            if (response != null && !responseSent) {
                session.transfer(response, Relationships.REL_RESPONSE);
            }

            // 5xx -> RETRY
        } else if (statusCode / 100 == 5) {
            if (request != null) {
                request = session.penalize(request);
                session.transfer(request, Relationships.REL_RETRY);
            }

            // 1xx, 3xx, 4xx -> NO RETRY
        } else {
            if (request != null) {
                if (context.getProperty(Descriptions.PROP_PENALIZE_NO_RETRY).asBoolean()) {
                    request = session.penalize(request);
                }
                session.transfer(request, Relationships.REL_NO_RETRY);
            }
        }

    }
}
