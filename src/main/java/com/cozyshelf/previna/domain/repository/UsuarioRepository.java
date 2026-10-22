package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    boolean existsByCpf(String cpf);

    boolean existsByLogin(String login);
}
