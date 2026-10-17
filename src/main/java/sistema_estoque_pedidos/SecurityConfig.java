package sistema_estoque_pedidos;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${estoque.jwt.segredo}") String segredo) {

        OctetSequenceKey jwk = new OctetSequenceKey.Builder(chave(segredo))
                .build();

        JWKSource<SecurityContext> jwks =
                new ImmutableJWKSet<>(new JWKSet(jwk));

        return new NimbusJwtEncoder(jwks);
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${estoque.jwt.segredo}") String segredo) {

        return NimbusJwtDecoder.withSecretKey(chave(segredo))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public AuthenticationEntryPoint autenticacao(
            @Qualifier("handlerExceptionResolver")
            HandlerExceptionResolver resolvedor) {

        // Envia o erro para o TratadorDeErros, que monta a resposta 401.
        return (request, response, exception) ->
                resolvedor.resolveException(request, response, null, exception);
    }

    @Bean
    public AccessDeniedHandler acessoNegado(
            @Qualifier("handlerExceptionResolver")
            HandlerExceptionResolver resolvedor) {

        // Envia o erro para o TratadorDeErros, que monta a resposta 403.
        return (request, response, exception) ->
                resolvedor.resolveException(request, response, null, exception);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint autenticacao,
            AccessDeniedHandler acessoNegado) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sessao -> sessao
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(autorizacao -> autorizacao
                    .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                    .requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(autenticacao))
            .exceptionHandling(excecao -> excecao
                    .authenticationEntryPoint(autenticacao)
                    .accessDeniedHandler(acessoNegado));

        return http.build();
    }

    private static SecretKey chave(String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);

        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                    "O segredo do JWT precisa ter pelo menos 32 bytes.");
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}