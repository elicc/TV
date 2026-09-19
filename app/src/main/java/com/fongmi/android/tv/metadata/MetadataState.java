package com.fongmi.android.tv.metadata;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MetadataState {
    public enum Status { IDLE, LOADING, SUCCESS, FALLBACK, ERROR }

    private final MovieIdentity identity;
    private final MovieMetadata source;
    private final Map<MetadataProvider, MovieMetadata> providers;
    private final List<MetadataCandidate> candidates;
    private final MetadataProvider selectedProvider;
    private final Status status;
    private final String message;

    public MetadataState(MovieIdentity identity, MovieMetadata source, Map<MetadataProvider, MovieMetadata> providers,
                         List<MetadataCandidate> candidates, MetadataProvider selectedProvider, Status status, String message) {
        this.identity = identity;
        this.source = source;
        this.providers = Collections.unmodifiableMap(new EnumMap<>(providers));
        this.candidates = candidates == null ? Collections.emptyList() : Collections.unmodifiableList(candidates);
        this.selectedProvider = selectedProvider == null ? MetadataProvider.SOURCE : selectedProvider;
        this.status = status == null ? Status.IDLE : status;
        this.message = message == null ? "" : message;
    }

    public static MetadataState loading(MovieIdentity identity, MovieMetadata source) {
        EnumMap<MetadataProvider, MovieMetadata> providers = new EnumMap<>(MetadataProvider.class);
        providers.put(MetadataProvider.SOURCE, source);
        return new MetadataState(identity, source, providers, Collections.emptyList(), MetadataProvider.SOURCE, Status.LOADING, "");
    }

    public MovieIdentity getIdentity() { return identity; }
    public MovieMetadata getSource() { return source; }
    public Map<MetadataProvider, MovieMetadata> getProviders() { return providers; }
    public List<MetadataCandidate> getCandidates() { return candidates; }
    public MetadataProvider getSelectedProvider() { return selectedProvider; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }

    public MovieMetadata getSelected() {
        MovieMetadata metadata = providers.get(selectedProvider);
        if (metadata != null) return metadata;
        MetadataCandidate candidate = getCandidate(selectedProvider);
        return candidate == null ? source : candidate.getMetadata();
    }

    public MetadataState select(MetadataProvider provider) {
        MetadataProvider selected = hasProviderOrCandidate(provider) ? provider : MetadataProvider.SOURCE;
        return new MetadataState(identity, source, providers, candidates, selected, status, message);
    }

    public boolean hasProviderOrCandidate(MetadataProvider provider) {
        return providers.containsKey(provider) || getCandidate(provider) != null;
    }

    public boolean isPendingCandidate(MetadataProvider provider) {
        return !providers.containsKey(provider) && getCandidate(provider) != null;
    }

    public MetadataCandidate getCandidate(MetadataProvider provider) {
        for (MetadataCandidate candidate : candidates) {
            MovieMetadata metadata = candidate == null ? null : candidate.getMetadata();
            if (metadata != null && metadata.getProvider() == provider) return candidate;
        }
        return null;
    }
}
