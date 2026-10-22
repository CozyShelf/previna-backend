package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

public record PreCadastroGestanteRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @CPF @Pattern(regexp = "\\d{11}") String cpf,
        @NotNull @Past LocalDate dataNascimento,
        @NotBlank @Pattern(regexp = "\\d{10,11}") String telefone,
        @Email @Size(max = 150) String email,
        @NotNull @PastOrPresent LocalDate dum
) {}
