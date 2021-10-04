package com.kash.stord.tinyurl;

import java.util.UUID;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entry point for incoming REST API calls for path/resource "/tinyUrl".
 */
@RestController
public class TinyUrlRestController {
    private static final Logger logger = LogManager.getLogger();
    private final TinyUrlRepository repository;
    @Value("${com.kash.tinyurl.host:http://localhost:9090}")
    private String authority;

    @Autowired
    public TinyUrlRestController(TinyUrlRepository repo) {
        this.repository = repo;
    }

    /**
     * Create a new short URL from a long URL.
     * Creates a new entry/mapping in DB (id <-> long URL).
     * Converts the id (number) of this new mapping to alpha-numeric short URL (string) using a bijective function.
     * Returns the short URL.
     *
     * @param body              JSON deserialized by Spring. Only longUrl is required.
     * @param userCorrelationId optional header, used for traceability
     * @return A new mapping, or an error message if URL is invalid.
     */
    @PostMapping(path = "/tinyurl")
    public ResponseEntity<UrlMappingPojo> createTinyurl(@RequestBody UrlMappingPojo body,
                                                        @RequestHeader(value = "X-Correlation-Id", required = false)
                                                            String userCorrelationId) {
        // if user provided a correlation id in header (X-Correlation-Id: <some unique id>) then use that
        // else generate one of our own. This is used in logging.pattern (%X{correlation-id}) in
        // application.properties, so it gets embedded in every log message. Required to find right
        // logs in a multi-threaded cloud environment.
        String correlationId = Strings.isNotEmpty(userCorrelationId) ? userCorrelationId : UUID.randomUUID().toString();
        ThreadContext.put("correlation-id", correlationId);

        // FIXME: In a boundary function like this, always provide a log at entry and all exits with as many
        //        variables/params as possible.
        logger.info("body: {}, correlationId: {}", body, correlationId);
        String longUrl = body.longUrl;

        try {
            if (!UrlValidator.getInstance().isValid(longUrl)) {
                // FIXME: Would be nice to specify what's wrong with URL and not just say it's bad URL.
                //        But there is not standard validator and Apache one only returns bool.
                String errMsg = String.format("Supplied longUrl (%s) is not a valid URL. " +
                    "Ensure it has valid Scheme, Authority, Path, Query, Fragment.", longUrl);
                logger.warn(errMsg);
                return new ResponseEntity<>(
                    body.withMessage(errMsg).witHttpStatusCode(HttpStatus.BAD_REQUEST),
                    HttpStatus.BAD_REQUEST);
            }

            UrlMapping newMapping = repository.save(new UrlMapping(longUrl));
            String shortUrl = NumToStrBijectiveConverter.numToStr(newMapping.getId());
            String qualifiedShortUrl = String.format("%s/%s", authority, shortUrl);
            logger.info("newMapping.getId(): {}, qualifiedShortUrl: {}", newMapping.getId(), qualifiedShortUrl);

            return new ResponseEntity<>(
                new UrlMappingPojo().withShortUrl(qualifiedShortUrl).withLongUrl(longUrl)
                    .withMessage("success").witHttpStatusCode(HttpStatus.OK),
                HttpStatus.OK);

        } catch (Exception e) {
            // FIXME: In a boundary function provide a catch all, so user never receives HTTP 500 with useless
            //        message "Internal server error".
            logger.error("Unexpected exception handling body: '{}'", body, e);
            // FIXME: If a correlation id is part of every bug report, it makes it easier to debug.
            return new ResponseEntity<>(
                body.withMessage("An unexpected error occurred. If problem persists, please contact support." +
                    " Provide correlation id: " + correlationId)
                    .witHttpStatusCode(HttpStatus.INTERNAL_SERVER_ERROR),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(path = "/tinyurl/{shortUrl}")
    public ResponseEntity<UrlMappingPojo> resolveTinyurl(@PathVariable String shortUrl,
                                                         @RequestHeader(value = "X-Correlation-Id", required = false)
                                                             String userCorrelationId) {
        String correlationId = Strings.isNotEmpty(userCorrelationId) ? userCorrelationId : UUID.randomUUID().toString();
        ThreadContext.put("correlation-id", correlationId);
        logger.info("shortUrl: {}, correlationId: {}", shortUrl, correlationId);
        try {
            long id = NumToStrBijectiveConverter.strToNum(shortUrl);
            UrlMapping resolvedUrlMapping = repository.findById(id).orElse(null);
            if (null == resolvedUrlMapping) {
                logger.warn("no mapping found for shortUrl: {}, id: {} in DB.", shortUrl, id);
                String errMsg = String.format("No mapping found for shortUrl: %s. Did you create a mapping?", shortUrl);
                return new ResponseEntity<>(
                    new UrlMappingPojo().withShortUrl(shortUrl).withMessage(errMsg)
                        .witHttpStatusCode(HttpStatus.NOT_FOUND),
                    HttpStatus.NOT_FOUND);
            }
            logger.info("resolved to: resolvedUrlMapping: {}", resolvedUrlMapping);
            return new ResponseEntity<>(
                new UrlMappingPojo().withLongUrl(resolvedUrlMapping.getLongUrl()).withShortUrl(shortUrl),
                HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Unexpected exception handling shortUrl: '{}'", shortUrl, e);
            // FIXME: If a correlation id is part of every bug report, it makes it easier to debug.
            return new ResponseEntity<>(
                new UrlMappingPojo()
                    .withMessage("An unexpected error occurred. If problem persists, please contact support." +
                        " Provide correlation id: " + correlationId)
                    .withShortUrl(shortUrl).witHttpStatusCode(HttpStatus.INTERNAL_SERVER_ERROR),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
