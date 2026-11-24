package ma.agencysuite.iam.infrastructure.security;

import ma.agencysuite.iam.application.CurrentUserResolver;
import ma.agencysuite.platform.PlatformApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trois chaines de securite, evaluees dans l'ordre :
 *
 * <ol>
 *   <li>{@code /api/v1/backoffice/**} : jeton du realm "platform" uniquement, d'un membre ACTIVE de
 *       l'editeur &rarr; perimetre plateforme ;</li>
 *   <li>{@code /api/**} : {@code GET /api/v1/platform/info} public ; tout le reste exige un jeton du
 *       realm "agencysuite", d'un utilisateur ACTIVE de son agence &rarr; perimetre de cette agence
 *       et utilisateur courant ;</li>
 *   <li>tout le reste : sante et informations de l'actuator, documentation OpenAPI et page
 *       d'erreur publiques ; tout autre chemin refuse.</li>
 * </ol>
 *
 * <p>Chaque chaine a son propre decodeur (issuer et cles du realm) et verifie en plus le claim
 * {@code iss} dans sa regle d'autorisation (defense en profondeur). Sans etat, sans CSRF (API a
 * jeton porteur), sans formulaire ni HTTP Basic. Les decodeurs ne sont pas des beans : chacun
 * n'est utilisable que par sa chaine.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

    private final SecurityProperties properties;
    private final SecurityProblems problems;

    SecurityConfiguration(SecurityProperties properties, MessageSource messages, LocaleResolver localeResolver,
            JsonMapper json) {
        this.properties = properties;
        this.problems = new SecurityProblems(messages, localeResolver, json);
    }

    @Bean
    @Order(1)
    SecurityFilterChain backofficeSecurityChain(HttpSecurity http, CurrentUserResolver users) throws Exception {
        String issuer = properties.platformIssuer();
        bearerTokenApi(http, KeycloakJwtDecoders.forRealm(issuer, properties.audience()))
                .securityMatcher("/api/v1/backoffice/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().access(issuedBy(issuer)))
                .addFilterAfter(TenantScopeFilter.platform(issuer, users, problems), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain agencySecurityChain(HttpSecurity http, PlatformApi platform, CurrentUserResolver users)
            throws Exception {
        String issuer = properties.agencyIssuer();
        bearerTokenApi(http, KeycloakJwtDecoders.forRealm(issuer, properties.audience()))
                .securityMatcher("/api/**")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/info").permitAll()
                        .anyRequest().access(issuedBy(issuer)))
                .addFilterAfter(TenantScopeFilter.agency(issuer, platform, users, problems),
                        AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain publicSecurityChain(HttpSecurity http) throws Exception {
        stateless(http)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                // Aucun jeton n'ouvre ces chemins : 403, jamais d'invitation a s'authentifier.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN, "access-denied"))
                        .accessDeniedHandler(problems::accessDenied));
        return http.build();
    }

    /** API a jeton porteur : jeton JWT decode par le decodeur du realm, erreurs problem+json. */
    private HttpSecurity bearerTokenApi(HttpSecurity http, JwtDecoder decoder) throws Exception {
        return stateless(http)
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(decoder))
                        .authenticationEntryPoint(problems::unauthenticated)
                        .accessDeniedHandler(problems::accessDenied))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems::unauthenticated)
                        .accessDeniedHandler(problems::accessDenied));
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /** Jeton du realm attendu : claim {@code iss} egal a l'issuer de la chaine (en plus du decodeur). */
    private static AuthorizationManager<RequestAuthorizationContext> issuedBy(String issuer) {
        return (authentication, context) -> new AuthorizationDecision(
                authentication.get() instanceof JwtAuthenticationToken token
                        && issuer.equals(token.getToken().getClaimAsString(JwtClaimNames.ISS)));
    }
}
