package com.fongmi.android.tv.metadata;

public class MetadataCandidate {
    private MovieMetadata metadata;
    private double confidence;

    public MetadataCandidate() {
    }

    public MetadataCandidate(MovieMetadata metadata, double confidence) {
        this.metadata = metadata;
        this.confidence = confidence;
    }

    public MovieMetadata getMetadata() { return metadata; }
    public double getConfidence() { return confidence; }
}
