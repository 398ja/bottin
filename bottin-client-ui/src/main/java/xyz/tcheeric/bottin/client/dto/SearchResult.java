package xyz.tcheeric.bottin.client.dto;

public class SearchResult {
    private String pubkey;
    private String npub;
    private String displayName;
    private String name;
    private String about;
    private String picture;
    private String nip05;
    private boolean isFollowed;
    private boolean isBlocked;

    public String getPubkey() { return pubkey; }
    public void setPubkey(String pubkey) { this.pubkey = pubkey; }
    public String getNpub() { return npub; }
    public void setNpub(String npub) { this.npub = npub; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getPicture() { return picture; }
    public void setPicture(String picture) { this.picture = picture; }
    public String getNip05() { return nip05; }
    public void setNip05(String nip05) { this.nip05 = nip05; }
    public boolean isFollowed() { return isFollowed; }
    public void setFollowed(boolean followed) { isFollowed = followed; }
    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }
}
