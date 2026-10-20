package com.cozyshelf.previna.infrastructure.security;

// papel: o perfil do usuário, ou PRIMEIRO_ACESSO enquanto ele ainda usa a senha temporária
public record UsuarioAutenticado(Long id, String papel) {
}
