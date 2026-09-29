package sistema_estoque_pedidos;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI estoqueOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Estoque e Pedidos")
                        .version("0.0.1")
                        .description(
                                "API para cadastro de produtos e controle "
                                + "de entradas e saídas de estoque com histórico. "
                                + "O módulo de pedidos está em desenvolvimento.")
                        .contact(new Contact()
                                .name("Guilherme Tonelli")
                                .url(
                                        "https://www.linkedin.com/in/"
                                        + "guilherme-tonellidev")));
    }
}