package uz.greenwhite.gateway.source;

import uz.greenwhite.gateway.model.ResponseSaveRequest;

public interface ResponseSinkClient {
    boolean saveResponse(ResponseSaveRequest request);
}