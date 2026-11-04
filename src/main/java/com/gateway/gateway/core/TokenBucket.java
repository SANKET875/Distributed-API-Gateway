package com.gateway.gateway.core;

public class TokenBucket {
    //required variables
    private final long capacity;
    private final double refillRatePerMs;
    private double currentTokens;
    private long lastRefillTimestamp;
    //custom constructor to set capacity and refill rate
    public TokenBucket(long capacity, long refillRatePerSecond){
        this.capacity = capacity;
        this.refillRatePerMs = (double)refillRatePerSecond/1000.0;
        this.currentTokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();

    }
    //main logic
    public synchronized boolean allowRequest(){
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastRefillTimestamp;

        double tokensToAdd = elapsedTime * refillRatePerMs;
        currentTokens = Math.min(capacity, currentTokens + tokensToAdd);
        lastRefillTimestamp = now;
        if (currentTokens >= 1.0) {
            currentTokens -= 1.0;
            return true;
        }

        return false;
    }

}
