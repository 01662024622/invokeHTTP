package nifi.processors.demo.properties;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.AttributeExpression;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class Descriptions {
    public static final String HTTP = "http";
    public static final String HTTPS = "https";
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    public static final PropertyDescriptor PROP_METHOD = new PropertyDescriptor.Builder()
            .name("HTTP Method")
            .description("HTTP request method (GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS). Arbitrary methods are also supported. "
                    + "Methods other than POST, PUT and PATCH will be sent without a message body.")
            .required(true)
            .defaultValue("GET")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING))
            .build();

    public static final PropertyDescriptor PROP_URL = new PropertyDescriptor.Builder()
            .name("Remote URL")
            .description("Remote URL which will be connected to, including scheme, host, port, path.")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.URL_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_CONNECT_TIMEOUT = new PropertyDescriptor.Builder()
            .name("Connection Timeout")
            .description("Max wait time for connection to remote service.")
            .required(true)
            .defaultValue("5 secs")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_READ_TIMEOUT = new PropertyDescriptor.Builder()
            .name("Read Timeout")
            .description("Max wait time for response from remote service.")
            .required(true)
            .defaultValue("15 secs")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();


    public static final PropertyDescriptor PROP_FOLLOW_REDIRECTS = new PropertyDescriptor.Builder()
            .name("Follow Redirects")
            .description("Follow HTTP redirects issued by remote server.")
            .required(true)
            .defaultValue("True")
            .allowableValues("True", "False")
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_ATTRIBUTES_TO_SEND = new PropertyDescriptor.Builder()
            .name("Attributes to Send")
            .description("Regular expression that defines which attributes to send as HTTP headers in the request. "
                    + "If not defined, no attributes are sent as headers. Also any dynamic properties set will be sent as headers. "
                    + "The dynamic property key will be the header key and the dynamic property value will be interpreted as expression "
                    + "language will be the header value.")
            .required(false)
            .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
            .build();



    public static final PropertyDescriptor PROP_CONTENT_TYPE = new PropertyDescriptor.Builder()
            .name("Content-Type")
            .description("The Content-Type to specify for when content is being transmitted through a PUT, POST or PATCH. "
                    + "In the case of an empty value after evaluating an expression language expression, Content-Type defaults to " + DEFAULT_CONTENT_TYPE)
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .defaultValue("${" + CoreAttributes.MIME_TYPE.key() + "}")
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING))
            .build();

    public static final PropertyDescriptor PROP_SEND_BODY = new PropertyDescriptor.Builder()
            .name("send-message-body")
            .displayName("Send Message Body")
            .description("If true, sends the HTTP message body on POST/PUT/PATCH requests (default).  If false, suppresses the message body and content-type header for these requests.")
            .defaultValue("true")
            .allowableValues("true", "false")
            .required(false)
            .build();

    // Per RFC 7235, 2617, and 2616.
    // basic-credentials = base64-user-pass
    // base64-user-pass = userid ":" password
    // userid = *<TEXT excluding ":">
    // password = *TEXT
    //
    // OCTET = <any 8-bit sequence of data>
    // CTL = <any US-ASCII control character (octets 0 - 31) and DEL (127)>
    // LWS = [CRLF] 1*( SP | HT )
    // TEXT = <any OCTET except CTLs but including LWS>
    //
    // Per RFC 7230, username & password in URL are now disallowed in HTTP and HTTPS URIs.
    public static final PropertyDescriptor PROP_BASIC_AUTH_USERNAME = new PropertyDescriptor.Builder()
            .name("Basic Authentication Username")
            .displayName("Basic Authentication Username")
            .description("The username to be used by the client to authenticate against the Remote URL.  Cannot include control characters (0-31), ':', or DEL (127).")
            .required(false)
            .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile("^[\\x20-\\x39\\x3b-\\x7e\\x80-\\xff]+$")))
            .build();

    public static final PropertyDescriptor PROP_BASIC_AUTH_PASSWORD = new PropertyDescriptor.Builder()
            .name("Basic Authentication Password")
            .displayName("Basic Authentication Password")
            .description("The password to be used by the client to authenticate against the Remote URL.")
            .required(false)
            .sensitive(true)
            .addValidator(StandardValidators.createRegexMatchingValidator(Pattern.compile("^[\\x20-\\x7e\\x80-\\xff]+$")))
            .build();

    public static final PropertyDescriptor PROP_PUT_OUTPUT_IN_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("Put Response Body In Attribute")
            .description("If set, the response body received back will be put into an attribute of the original FlowFile instead of a separate "
                    + "FlowFile. The attribute key to put to is determined by evaluating value of this property. ")
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING))
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    public static final PropertyDescriptor PROP_PUT_ATTRIBUTE_MAX_LENGTH = new PropertyDescriptor.Builder()
            .name("Max Length To Put In Attribute")
            .description("If routing the response body to an attribute of the original (by setting the \"Put response body in attribute\" "
                    + "property or by receiving an error status code), the number of characters put to the attribute value will be at "
                    + "most this amount. This is important because attributes are held in memory and large attributes will quickly "
                    + "cause out of memory issues. If the output goes longer than this value, it will be truncated to fit. "
                    + "Consider making this smaller if able.")
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .defaultValue("256")
            .build();

    public static final PropertyDescriptor PROP_DIGEST_AUTH = new PropertyDescriptor.Builder()
            .name("Digest Authentication")
            .displayName("Use Digest Authentication")
            .description("Whether to communicate with the website using Digest Authentication. 'Basic Authentication Username' and 'Basic Authentication Password' are used "
                    + "for authentication.")
            .required(false)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final PropertyDescriptor PROP_OUTPUT_RESPONSE_REGARDLESS = new PropertyDescriptor.Builder()
            .name("Always Output Response")
            .description("Will force a response FlowFile to be generated and routed to the 'Response' relationship regardless of what the server status code received is "
                    + "or if the processor is configured to put the server response body in the request attribute. In the later configuration a request FlowFile with the "
                    + "response body in the attribute and a typical response FlowFile will be emitted to their respective relationships.")
            .required(false)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final PropertyDescriptor PROP_ADD_HEADERS_TO_REQUEST = new PropertyDescriptor.Builder()
            .name("Add Response Headers to Request")
            .description("Enabling this property saves all the response headers to the original request. This may be when the response headers are needed "
                    + "but a response is not generated due to the status code received.")
            .required(false)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final PropertyDescriptor PROP_USE_CHUNKED_ENCODING = new PropertyDescriptor.Builder()
            .name("Use Chunked Encoding")
            .description("When POST'ing, PUT'ing or PATCH'ing content set this property to true in order to not pass the 'Content-length' header and instead send 'Transfer-Encoding' with "
                    + "a value of 'chunked'. This will enable the data transfer mechanism which was introduced in HTTP 1.1 to pass data of unknown lengths in chunks.")
            .required(true)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final PropertyDescriptor PROP_PENALIZE_NO_RETRY = new PropertyDescriptor.Builder()
            .name("Penalize on \"No Retry\"")
            .description("Enabling this property will penalize FlowFiles that are routed to the \"No Retry\" relationship.")
            .required(false)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final PropertyDescriptor PROP_USE_ETAG = new PropertyDescriptor.Builder()
            .name("use-etag")
            .description("Enable HTTP entity tag (ETag) support for HTTP requests.")
            .displayName("Use HTTP ETag")
            .required(true)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final PropertyDescriptor PROP_ETAG_MAX_CACHE_SIZE = new PropertyDescriptor.Builder()
            .name("etag-max-cache-size")
            .description("The maximum size that the ETag cache should be allowed to grow to. The default size is 10MB.")
            .displayName("Maximum ETag Cache Size")
            .required(true)
            .defaultValue("10MB")
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .build();

    public static final PropertyDescriptor IGNORE_RESPONSE_CONTENT = new PropertyDescriptor.Builder()
            .name("ignore-response-content")
            .description("If true, the processor will not write the response's content into the flow file.")
            .displayName("Ignore response's content")
            .required(true)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final List<PropertyDescriptor> DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
            PROP_METHOD,
            PROP_URL,
            PROP_CONNECT_TIMEOUT,
            PROP_READ_TIMEOUT,
            PROP_FOLLOW_REDIRECTS,
            PROP_ATTRIBUTES_TO_SEND,
            PROP_BASIC_AUTH_USERNAME,
            PROP_BASIC_AUTH_PASSWORD,
            PROP_PUT_OUTPUT_IN_ATTRIBUTE,
            PROP_PUT_ATTRIBUTE_MAX_LENGTH,
            PROP_DIGEST_AUTH,
            PROP_OUTPUT_RESPONSE_REGARDLESS,
            PROP_ADD_HEADERS_TO_REQUEST,
            PROP_CONTENT_TYPE,
            PROP_SEND_BODY,
            PROP_USE_CHUNKED_ENCODING,
            PROP_PENALIZE_NO_RETRY,
            PROP_USE_ETAG,
            PROP_ETAG_MAX_CACHE_SIZE,
            IGNORE_RESPONSE_CONTENT));
}
