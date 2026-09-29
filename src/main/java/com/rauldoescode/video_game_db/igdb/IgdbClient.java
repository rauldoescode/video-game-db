package com.rauldoescode.video_game_db.igdb;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * HTTP service interface for the IGDB /games and /popularity_primitives endpoints. Each method's
 * path is relative to the RestClient base URL in IgdbConfig. Accepts JSON in return from the IGDB API.
 */
@HttpExchange(accept="application/json")
public interface IgdbClient {

    /**
     * POSTs the apicalypse query to the IGDB API (as text/plain), then IGDB returns a JSON response.
     * Jackson will parse the JSON into a List of IgdbGame objects, then the method returns the list.
     * @param apicalypseQuery the apicalypse query to send to IGDB
     * @return a list of IgdbGame objects
     */
    @PostExchange(url="/games", contentType="text/plain")
    List<IgdbGame> games(@RequestBody String apicalypseQuery);

    /**
     * POSTs an Apicalypse query to IGDB's popularity primitives. The response is ranked game ids
     * and scores, not full games — a second call to {@link #games} loads those ids.
     * @param apicalypseQuery the apicalypse query to send to IGDB
     * @return the ranked primitives, never null when IGDB responds
     */
    @PostExchange(url="/popularity_primitives", contentType="text/plain")
    List<IgdbPopularity> popularityPrimitives(@RequestBody String apicalypseQuery);
}
