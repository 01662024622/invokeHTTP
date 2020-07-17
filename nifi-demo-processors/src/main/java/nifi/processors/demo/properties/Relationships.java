package nifi.processors.demo.properties;

import org.apache.nifi.processor.Relationship;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Relationships {
    // relationships
    public static final Relationship REL_SUCCESS_REQ = new Relationship.Builder()
            .name("Original")
            .description("The original FlowFile will be routed upon success (2xx status codes). It will have new attributes detailing the "
                    + "success of the request.")
            .build();

    public static final Relationship REL_RESPONSE = new Relationship.Builder()
            .name("Response")
            .description("A Response FlowFile will be routed upon success (2xx status codes). If the 'Output Response Regardless' property "
                    + "is true then the response will be sent to this relationship regardless of the status code received.")
            .build();

    public static final Relationship REL_RETRY = new Relationship.Builder()
            .name("Retry")
            .description("The original FlowFile will be routed on any status code that can be retried (5xx status codes). It will have new "
                    + "attributes detailing the request.")
            .build();

    public static final Relationship REL_NO_RETRY = new Relationship.Builder()
            .name("No Retry")
            .description("The original FlowFile will be routed on any status code that should NOT be retried (1xx, 3xx, 4xx status codes).  "
                    + "It will have new attributes detailing the request.")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("Failure")
            .description("The original FlowFile will be routed on any type of connection failure, timeout or general exception. "
                    + "It will have new attributes detailing the request.")
            .build();

    public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_SUCCESS_REQ, REL_RESPONSE, REL_RETRY, REL_NO_RETRY, REL_FAILURE)));
}
