package com.rauldoescode.video_game_db.igdb;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

// Proxy POSTs to /games path (RestClient base URL in IgdbConfig) and only accepts JSON in return

/**
 * HTTP service interface for the IGDB /games endpoint. Path is relative to the RestClient base URL
 * in IgdbConfig. Accepts JSON in return from the IGDB API.
 */
@HttpExchange(url="/games", accept="application/json")
public interface IgdbClient {

    /**
     * POSTs the apicalypse query to the IGDB API (as text/plain), then IGDB returns a JSON response.
     * Jackson will parse the JSON into a List of IgdbGame objects, then the method returns the list.
     * @param apicalypseQuery the apicalypse query to send to IGDB
     * @return a list of IgdbGame objects
     */
    @PostExchange(contentType="text/plain")
    List<IgdbGame> games(@RequestBody String apicalypseQuery);
}
