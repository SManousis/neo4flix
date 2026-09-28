package com.neo4flix.user.user;

import com.neo4flix.user.api.PublicUser;
import com.neo4flix.user.api.UpdateProfileRequest;
import com.neo4flix.user.api.ReauthenticationRequest;
import com.neo4flix.user.auth.RefreshCookieFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.neo4flix.user.auth.AuthApplicationService;
import com.neo4flix.user.security.JwtKeyConfiguration;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Conditional(JwtKeyConfiguration.PrivateKeyConfigured.class)
public class ProfileController {

    private final AuthApplicationService auth;
    private final AccountDeletionService deletion;
    private final RefreshCookieFactory cookies;

    public ProfileController(AuthApplicationService auth, AccountDeletionService deletion, RefreshCookieFactory cookies) {
        this.auth = auth;
        this.deletion = deletion;
        this.cookies = cookies;
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReauthenticationRequest request) {
        deletion.delete(jwt.getSubject(), request.password(), request.code());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping
    public PublicUser me(@AuthenticationPrincipal Jwt jwt) {
        return auth.currentUser(jwt.getSubject());
    }

    @PatchMapping
    public PublicUser update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(jwt.getSubject(), request);
    }
}
