package com.cloud.api.response;

import com.cloud.api.ApiConstants;
import com.cloud.api.BaseResponse;
import com.cloud.serializer.Param;

import com.google.gson.annotations.SerializedName;

public class TrafficTypeImplementorResponse extends BaseResponse {
    @SerializedName(ApiConstants.TRAFFIC_TYPE)
    @Param(description = "network traffic type")
    private String trafficType;

    @SerializedName(ApiConstants.TRAFFIC_TYPE_IMPLEMENTOR)
    @Param(description = "implementor of network traffic type")
    private String implementor;

    public void setTrafficType(final String type) {
        this.trafficType = type;
    }

    public void setImplementor(final String impl) {
        this.implementor = impl;
    }
}
