package nifi.processors.demo.model;

import nifi.processors.demo.properties.Descriptions;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;

import java.io.IOException;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class RequestBuilder {
    public final static String STATUS_CODE = "invokehttp.status.code";
    public final static String STATUS_MESSAGE = "invokehttp.status.message";
    public final static String RESPONSE_BODY = "invokehttp.response.body";
    public final static String REQUEST_URL = "invokehttp.request.url";
    public final static String TRANSACTION_ID = "invokehttp.tx.id";
    public final static String REMOTE_DN = "invokehttp.remote.dn";
    public final static String EXCEPTION_CLASS = "invokehttp.java.exception.class";
    public final static String EXCEPTION_MESSAGE = "invokehttp.java.exception.message";
    public static RequestBody getRequestBodyToSend(final ProcessSession session, final ProcessContext context, final FlowFile requestFlowFile, boolean useChunked) {
        if(context.getProperty(Descriptions.PROP_SEND_BODY).asBoolean()) {
            return new RequestBody() {
                @Override
                public MediaType contentType() {
                    String contentType = context.getProperty(Descriptions.PROP_CONTENT_TYPE).evaluateAttributeExpressions(requestFlowFile).getValue();
                    contentType = StringUtils.isBlank(contentType) ? Descriptions.DEFAULT_CONTENT_TYPE : contentType;
                    return MediaType.parse(contentType);
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    session.exportTo(requestFlowFile, sink.outputStream());
                }

                @Override
                public long contentLength(){
                    return useChunked ? -1 : requestFlowFile.getSize();
                }
            };
        } else {
            return RequestBody.create(null, new byte[0]);
        }
    }

    public static Map<String, String> convertAttributesFromHeaders(URL url, Response responseHttp){
        // create a new hashmap to store the values from the connection
        Map<String, String> map = new HashMap<>();
        responseHttp.headers().names().forEach( (key) -> {
            if (key == null) {
                return;
            }

            List<String> values = responseHttp.headers().values(key);

            // we ignore any headers with no actual values (rare)
            if (values == null || values.isEmpty()) {
                return;
            }

            // create a comma separated string from the values, this is stored in the map
            String value = csv(values);

            // put the csv into the map
            map.put(key, value);
        });

        if (responseHttp.request().isHttps()) {
            Principal principal = responseHttp.handshake().peerPrincipal();

            if (principal != null) {
                map.put(REMOTE_DN, principal.getName());
            }
        }

        return map;
    }

    private static String csv(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }

        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            value = value.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb.toString().trim();
    }

}
