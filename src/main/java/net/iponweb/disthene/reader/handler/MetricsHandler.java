package net.iponweb.disthene.reader.handler;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import net.iponweb.disthene.reader.exceptions.MissingParameterException;
import net.iponweb.disthene.reader.exceptions.UnsupportedMethodException;
import net.iponweb.disthene.reader.service.metric.MetricService;
import org.apache.log4j.Logger;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Andrei Ivanov
 */
public class MetricsHandler implements DistheneReaderHandler {

    final static Logger logger = Logger.getLogger(MetricsHandler.class);

    private MetricService metricService;

    public MetricsHandler(MetricService metricService) {
        this.metricService = metricService;
    }

    @Override
    public FullHttpResponse handle(HttpRequest request) throws UnsupportedMethodException, MissingParameterException, ExecutionException, InterruptedException {
        MetricsParameters parameters = parse(request);

        logger.debug("Got request: " + parameters);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(metricService.getMetricsAsJson(parameters.getTenant(), parameters.getPaths(), parameters.getFrom(), parameters.getTo()).getBytes()));
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    private MetricsParameters parse(HttpRequest request) throws MissingParameterException, UnsupportedMethodException {
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());

        if (request.getMethod().equals(HttpMethod.GET)) {
            MetricsParameters parameters = new MetricsParameters();
            if (queryStringDecoder.parameters().get("tenant") != null) {
                parameters.setTenant(queryStringDecoder.parameters().get("tenant").get(0));
            } else {
                throw new MissingParameterException("Tenant parameter is missing");
            }
            if (queryStringDecoder.parameters().get("path") != null) {
                for (String path : queryStringDecoder.parameters().get("path")) {
                    parameters.getPaths().add(path);
                }
            } else {
                throw new MissingParameterException("Path parameter is missing");
            }
            if (queryStringDecoder.parameters().get("from") != null) {
                parameters.setFrom(Long.valueOf(queryStringDecoder.parameters().get("from").get(0)));
            } else {
                throw new MissingParameterException("From parameter is missing");
            }
            if (queryStringDecoder.parameters().get("to") != null) {
                parameters.setTo(Long.valueOf(queryStringDecoder.parameters().get("to").get(0)));
            } else {
                throw new MissingParameterException("To parameter is missing");
            }

            return parameters;
        } else if (request.getMethod().equals(HttpMethod.POST)) {
            MetricsParameters parameters =
                    new Gson().fromJson(((HttpContent) request).content().toString(Charset.defaultCharset()), MetricsParameters.class);
            if (parameters.getTenant() == null) {
                throw new MissingParameterException("Tenant parameter is missing");
            }
            if (parameters.getPaths().size() == 0) {
                throw new MissingParameterException("Path parameter is missing");
            }
            if (parameters.getFrom() == null) {
                throw new MissingParameterException("From parameter is missing");
            }
            if (parameters.getTo() == null) {
                throw new MissingParameterException("To parameter is missing");
            }
            return parameters;
        } else {
            throw new UnsupportedMethodException();
        }
    }

    private class MetricsParameters {

        private String tenant;
        private List<String> paths = new ArrayList<>();
        private Long from;
        private Long to;

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPath(List<String> paths) {
            this.paths = paths;
        }

        public Long getFrom() {
            return from;
        }

        public void setFrom(Long from) {
            this.from = from;
        }

        public Long getTo() {
            return to;
        }

        public void setTo(Long to) {
            this.to = to;
        }

        @Override
        public String toString() {
            return "MetricsParameters{" +
                    "tenant='" + tenant + '\'' +
                    ", paths=" + paths +
                    ", from=" + from +
                    ", to=" + to +
                    '}';
        }
    }

}