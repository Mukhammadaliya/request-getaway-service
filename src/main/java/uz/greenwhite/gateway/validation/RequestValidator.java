package uz.greenwhite.gateway.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.greenwhite.gateway.model.kafka.RequestMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class RequestValidator {

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    /**
     * Validate request message before sending to Kafka.
     * Returns list of validation errors. Empty list means valid.
     */
    public List<String> validate(RequestMessage request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Request message is null");
            return errors;
        }

        if (request.getCompanyId() == null) {
            errors.add("companyId is null");
        }

        if (request.getRequestId() == null) {
            errors.add("requestId is null");
        }

        if (request.getBaseUrl() == null || request.getBaseUrl().isBlank()) {
            errors.add("baseUrl is null or empty");
        }

        if (request.getMethod() == null || request.getMethod().isBlank()) {
            errors.add("method is null or empty");
        } else if (!ALLOWED_METHODS.contains(request.getMethod().toUpperCase())) {
            errors.add("Invalid HTTP method: " + request.getMethod());
        }

        return errors;
    }

    /**
     * Check if request is valid
     */
    public boolean isValid(RequestMessage request) {
        return validate(request).isEmpty();
    }
}