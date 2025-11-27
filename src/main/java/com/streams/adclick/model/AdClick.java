package com.streams.adclick.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an ad click event.
 * Used to demonstrate event modeling in Kafka Streams.
 */
public class AdClick {
    
    @JsonProperty("seller_id")
    private String sellerId;
    
    @JsonProperty("ad_id")
    private String adId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("page_url")
    private String pageUrl;

    // Required for Jackson deserialization
    public AdClick() {}

    public AdClick(String sellerId, String adId, long timestamp, String pageUrl) {
        this.sellerId = sellerId;
        this.adId = adId;
        this.timestamp = timestamp;
        this.pageUrl = pageUrl;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getAdId() {
        return adId;
    }

    public void setAdId(String adId) {
        this.adId = adId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    @Override
    public String toString() {
        return "AdClick{" +
                "sellerId='" + sellerId + '\'' +
                ", adId='" + adId + '\'' +
                ", timestamp=" + timestamp +
                ", pageUrl='" + pageUrl + '\'' +
                '}';
    }
}
