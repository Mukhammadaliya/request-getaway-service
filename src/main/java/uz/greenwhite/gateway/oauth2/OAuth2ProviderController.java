package uz.greenwhite.gateway.oauth2;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oauth2-provider")
@RequiredArgsConstructor
public class OAuth2ProviderController {

    private final OAuth2ProviderService oAuth2ProviderService;

    /**
     * OAuth2 provider ma'lumotlari
     *
     * GET http://localhost:8090/api/v1/oauth2-provider/info
     *
     * Response:
     * {
     *   "providers": {
     *     "smartup": { "type": "biruni", "tokenUrl": "...", "scope": "read+write" }
     *   },
     *   "types": ["biruni"]
     * }
     */
    @GetMapping("/info")
    public ResponseEntity<?> getInformation() {
        return ResponseEntity.ok(oAuth2ProviderService.getInformation());
    }
}