package com.fongmi.android.tv.metadata;

/** Display-only cast credit returned by a metadata provider. */
public class MovieCast {
    private String externalId = "";
    private String name = "";
    private String character = "";
    private String avatar = "";
    private String url = "";

    public MovieCast() {
    }

    public MovieCast(String externalId, String name, String character, String avatar, String url) {
        this.externalId = externalId;
        this.name = name;
        this.character = character;
        this.avatar = avatar;
        this.url = url;
    }

    public String getExternalId() { return value(externalId); }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getName() { return value(name); }
    public void setName(String name) { this.name = name; }
    public String getCharacter() { return value(character); }
    public void setCharacter(String character) { this.character = character; }
    public String getAvatar() { return value(avatar); }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getUrl() { return value(url); }
    public void setUrl(String url) { this.url = url; }

    private static String value(String value) { return value == null ? "" : value.trim(); }
}
