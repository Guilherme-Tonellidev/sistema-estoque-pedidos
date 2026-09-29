package sistema_estoque_pedidos;

import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class TratadorDeErros {

    public record ErroResposta(int status, String erro, String caminho) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String mensagem = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(erro -> erro.getDefaultMessage() == null
                        ? "Campo inválido."
                        : erro.getDefaultMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining(" "));

        return responder(400, mensagem, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroResposta> tratarRegra(
            ResponseStatusException exception,
            HttpServletRequest request) {

        String mensagem = exception.getReason() == null
                ? "Não foi possível concluir a operação."
                : exception.getReason();

        return responder(
                exception.getStatusCode().value(),
                mensagem,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> tratarCorpoInvalido(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return responder(
                400,
                "Envie um JSON válido com os campos nos tipos esperados.",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tratarParametroInvalido(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return responder(
                400,
                "O parâmetro informado possui um formato inválido.",
                request);
    }

    private ResponseEntity<ErroResposta> responder(
            int status,
            String mensagem,
            HttpServletRequest request) {

        ErroResposta resposta = new ErroResposta(
                status,
                mensagem,
                request.getRequestURI());

        return ResponseEntity.status(status).body(resposta);
    }
}