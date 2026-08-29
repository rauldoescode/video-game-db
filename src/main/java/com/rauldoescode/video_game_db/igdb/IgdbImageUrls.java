package com.rauldoescode.video_game_db.igdb;

public class IgdbImageUrls {

    public static String url(String imageId, IgdbImageSize size) {
        if (imageId == null || imageId.isBlank()) {
            return null;
        }
        return "https://images.igdb.com/igdb/image/upload/t_" + size.getToken() + "/" + imageId + ".jpg";
    }
}
