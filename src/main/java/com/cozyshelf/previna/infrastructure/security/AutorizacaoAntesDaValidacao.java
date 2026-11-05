package com.cozyshelf.previna.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

// O @PreAuthorize do controller só é conferido quando o método é chamado, e o Spring lê e valida o corpo (@Valid)
// antes disso: quem não tinha permissão e mandava um corpo inválido recebia 400 com as regras de validação, e não 403.
// Este interceptor roda depois de escolher o método e antes de ler os argumentos, e avalia o mesmo @PreAuthorize
// (do método ou da classe): as regras continuam num lugar só, nas anotações. A checagem do proxy continua valendo.
// Só cabem expressões sem argumentos do método (hasRole, hasAnyRole...), que é o que os controllers usam.
public class AutorizacaoAntesDaValidacao implements HandlerInterceptor {
    private final PreAuthorizeAuthorizationManager autorizacao = new PreAuthorizeAuthorizationManager();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        AuthorizationResult resultado = autorizacao.authorize(
                () -> SecurityContextHolder.getContext().getAuthentication(),
                new SimpleMethodInvocation(metodo.getBean(), metodo.getMethod()));
        // Sem @PreAuthorize o resultado é nulo: a rota segue as regras da SecurityConfig
        if (resultado != null && !resultado.isGranted()) {
            // Tratada pelo Spring Security como a do proxy: 403 com token, 401 sem
            throw new AccessDeniedException("Acesso negado");
        }
        return true;
    }
}
