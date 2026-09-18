package com.fongmi.android.tv.metadata;

import java.io.IOException;
import java.util.List;

/** Provider adapter contract for Douban, TMDB and future metadata sources. */
public interface MetadataProviderClient {
    MetadataProvider provider();

    List<MovieMetadata> search(MovieIdentity identity) throws IOException;

    MovieMetadata detail(String externalId, boolean tv) throws IOException;

    /** Best-effort enrichment for records cached before artwork support existed. */
    default void enrichArtwork(MovieMetadata metadata, boolean tv) throws IOException {
    }
}
