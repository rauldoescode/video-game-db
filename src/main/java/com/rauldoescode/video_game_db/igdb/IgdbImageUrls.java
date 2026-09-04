package com.rauldoescode.video_game_db.igdb;

/**
 * Utility class for generating URLs for IGDB image resources
 */
public class IgdbImageUrls {

    /**
     * Returns a URL for an image with the given ID and size
     * @param imageId IGDB image ID
     * @param size image size
     * @return a URL for the image, or null if the image ID is blank or null
     */
    public static String url(String imageId, IgdbImageSize size) {
        if (imageId == null || imageId.isBlank()) {
            return null;
        }
        return "https://images.igdb.com/igdb/image/upload/t_" + size.getToken() + "/" + imageId + ".jpg";
    }
}
