package com.rauldoescode.video_game_db.library;

import com.rauldoescode.video_game_db.dto.request.AddLibraryEntryRequest;
import com.rauldoescode.video_game_db.dto.request.UpdateLibraryEntryRequest;
import com.rauldoescode.video_game_db.dto.response.LibraryEntryResponse;
import com.rauldoescode.video_game_db.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService library;

    public LibraryController(LibraryService library) {
        this.library = library;
    }

    /**
     * The signed-in user's library, newest change first.
     *
     * @param jwt    the access token
     * @param status only this status, or every entry when omitted
     * @return the entries
     */
    @GetMapping
    public List<LibraryEntryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) GameStatus status) {
        return library.list(CurrentUser.id(jwt), status);
    }

    /**
     * Adds a game to the signed-in user's library, storing the game from IGDB on first add.
     *
     * @param jwt     the access token
     * @param request IGDB id and optional starting status
     * @return the new entry
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryEntryResponse add(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddLibraryEntryRequest request) {
        return library.add(CurrentUser.id(jwt), request.igdbId(), request.status());
    }

    /**
     * Replaces one of the signed-in user's entries. Another user's entry id is a 404.
     *
     * @param jwt     the access token
     * @param entryId the library entry
     * @param request the new state
     * @return the updated entry
     */
    @PutMapping("/{entryId}")
    public LibraryEntryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID entryId,
            @Valid @RequestBody UpdateLibraryEntryRequest request) {
        return library.update(CurrentUser.id(jwt), entryId, request);
    }

    /**
     * Removes one of the signed-in user's entries. The stored game stays.
     *
     * @param jwt     the access token
     * @param entryId the library entry
     */
    @DeleteMapping("/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entryId) {
        library.delete(CurrentUser.id(jwt), entryId);
    }
}
