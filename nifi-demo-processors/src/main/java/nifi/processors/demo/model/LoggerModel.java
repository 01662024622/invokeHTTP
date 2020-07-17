package nifi.processors.demo.model;

import okhttp3.Request;
import okhttp3.Response;
import org.apache.nifi.logging.ComponentLog;

import java.net.URL;
import java.util.List;
import java.util.Map;

public class LoggerModel {
    public static void logRequest(ComponentLog logger, Request request) {
        logger.debug("\nRequest to remote service:\n\t{}\n{}",
                new Object[]{request.url().url().toExternalForm(), getLogString(request.headers().toMultimap())});
    }

    public static void logResponse(ComponentLog logger, URL url, Response response) {
        logger.debug("\nResponse from remote service:\n\t{}\n{}",
                new Object[]{url.toExternalForm(), getLogString(response.headers().toMultimap())});
    }

    public static String getLogString(Map<String, List<String>> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> list = entry.getValue();
            if (list.isEmpty()) {
                continue;
            }
            sb.append("\t");
            sb.append(entry.getKey());
            sb.append(": ");
            if (list.size() == 1) {
                sb.append(list.get(0));
            } else {
                sb.append(list.toString());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
